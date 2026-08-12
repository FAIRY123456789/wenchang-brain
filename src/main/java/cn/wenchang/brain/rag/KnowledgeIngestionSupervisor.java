package cn.wenchang.brain.rag;

import cn.wenchang.brain.config.WenchangProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 负责 V1.3 知识摄取的单一入口：递归发现、Front Matter 解析、去重、章节化和切块。
 */
@Component
public class KnowledgeIngestionSupervisor {

    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,4})\\s+(.+?)\\s*$");
    private static final Pattern TOKEN = Pattern.compile("[\\p{IsHan}]{2,}|[A-Za-z0-9_-]{3,}");
    private static final double NEAR_DUPLICATE_THRESHOLD = 0.92d;

    private final WenchangProperties properties;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(420)
            .withMinChunkSizeChars(120)
            .withMinChunkLengthToEmbed(40)
            .withMaxNumChunks(20_000)
            .withKeepSeparator(true)
            .build();

    public KnowledgeIngestionSupervisor(WenchangProperties properties) {
        this.properties = properties;
    }

    public PreparedKnowledge prepare() throws IOException {
        Path knowledgeDir = Path.of(properties.getKnowledgeDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(knowledgeDir)) {
            throw new IOException("Knowledge directory not found: " + knowledgeDir);
        }

        List<Path> candidates;
        try (var stream = Files.walk(knowledgeDir)) {
            candidates = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .filter(path -> !isExcluded(knowledgeDir.relativize(path)))
                    .sorted(Comparator.comparing(path -> knowledgeDir.relativize(path).toString()))
                    .toList();
        }

        List<SourceDocument> parsed = new ArrayList<>();
        for (Path path : candidates) {
            SourceDocument source = readSource(knowledgeDir, path);
            if ("active".equalsIgnoreCase(source.metadata().getOrDefault("index_status", "active"))) {
                parsed.add(source);
            }
        }
        parsed.sort(Comparator.comparingInt(this::sourcePriority).thenComparing(SourceDocument::relativePath));

        List<SourceDocument> accepted = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();
        Set<String> seenContentHashes = new HashSet<>();
        int duplicatesSkipped = 0;
        for (SourceDocument source : parsed) {
            String url = normalize(source.metadata().get("source_url"));
            String title = normalize(source.title());
            String contentHash = source.metadata().get("content_sha256");
            boolean exactDuplicate = (!url.isBlank() && seenUrls.contains(url))
                    || (!title.isBlank() && seenTitles.contains(title))
                    || seenContentHashes.contains(contentHash);
            if (exactDuplicate || isNearDuplicate(source, accepted)) {
                duplicatesSkipped++;
                continue;
            }
            accepted.add(source);
            if (!url.isBlank()) seenUrls.add(url);
            if (!title.isBlank()) seenTitles.add(title);
            seenContentHashes.add(contentHash);
        }

        List<Document> chunks = new ArrayList<>();
        Map<String, Integer> chunksPerFile = new LinkedHashMap<>();
        Map<String, Integer> categories = new LinkedHashMap<>();
        Map<String, Integer> sourceLevels = new LinkedHashMap<>();
        Set<String> sources = new LinkedHashSet<>();
        int readerDocuments = 0;
        int sectionDocuments = 0;

        for (SourceDocument source : accepted) {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withIncludeBlockquote(true)
                    .withIncludeCodeBlock(true)
                    .withAdditionalMetadata(source.documentMetadata())
                    .build();
            readerDocuments += new MarkdownDocumentReader(
                    new FileSystemResource(source.path()), config).read().size();

            List<Document> sections = splitIntoSections(source);
            sectionDocuments += sections.size();
            List<Document> fileChunks = splitter.apply(sections);
            chunks.addAll(fileChunks);
            chunksPerFile.put(source.relativePath(), fileChunks.size());

            categories.merge(source.category(), 1, Integer::sum);
            sourceLevels.merge(source.metadata().getOrDefault("source_level", "UNSPECIFIED"), 1, Integer::sum);
            sources.add(source.metadata().getOrDefault("source_id", source.relativePath()));
        }

        return new PreparedKnowledge(
                accepted.size(), readerDocuments, sectionDocuments, List.copyOf(chunks),
                Map.copyOf(chunksPerFile), Map.copyOf(categories), Map.copyOf(sourceLevels),
                sources.size(), duplicatesSkipped, corpusSignature(knowledgeDir, candidates));
    }

    private SourceDocument readSource(Path root, Path path) throws IOException {
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        FrontMatter frontMatter = parseFrontMatter(raw);
        String relative = root.relativize(path).toString().replace('\\', '/');
        String filename = path.getFileName().toString();
        String title = frontMatter.metadata().getOrDefault("title", firstHeading(frontMatter.body(),
                filename.replaceFirst("(?i)\\.md$", "")));
        String category = frontMatter.metadata().getOrDefault("category", categoryFromPath(root.relativize(path)));

        Map<String, String> metadata = new LinkedHashMap<>(frontMatter.metadata());
        metadata.putIfAbsent("title", title);
        metadata.putIfAbsent("category", category);
        metadata.putIfAbsent("index_status", "active");
        metadata.put("filename", filename);
        metadata.put("relative_path", relative);
        metadata.put("source", path.toString());
        metadata.put("content_sha256", sha256(frontMatter.body()));

        return new SourceDocument(path, relative, title, category, frontMatter.body(),
                Map.copyOf(metadata), tokenSet(frontMatter.body()));
    }

    private List<Document> splitIntoSections(SourceDocument source) {
        List<HeadingPosition> headings = new ArrayList<>();
        Matcher matcher = HEADING.matcher(source.body());
        while (matcher.find()) {
            headings.add(new HeadingPosition(matcher.start(), matcher.end(), matcher.group(1).length(), matcher.group(2).trim()));
        }
        if (headings.isEmpty()) {
            return List.of(new Document(source.body(), sectionMetadata(source, source.title())));
        }

        List<Document> result = new ArrayList<>();
        Deque<HeadingPosition> hierarchy = new ArrayDeque<>();
        for (int index = 0; index < headings.size(); index++) {
            HeadingPosition heading = headings.get(index);
            while (!hierarchy.isEmpty() && hierarchy.peekLast().level() >= heading.level()) {
                hierarchy.removeLast();
            }
            hierarchy.addLast(heading);
            int end = index + 1 < headings.size() ? headings.get(index + 1).start() : source.body().length();
            String text = source.body().substring(heading.start(), end).trim();
            if (text.length() < 40 && index + 1 < headings.size()) {
                continue;
            }
            String sectionPath = hierarchy.stream().map(HeadingPosition::title).reduce((a, b) -> a + " > " + b)
                    .orElse(source.title());
            result.add(new Document(text, sectionMetadata(source, sectionPath)));
        }
        return result;
    }

    private Map<String, Object> sectionMetadata(SourceDocument source, String section) {
        Map<String, Object> metadata = new LinkedHashMap<>(source.documentMetadata());
        metadata.put("section", section);
        return metadata;
    }

    private boolean isExcluded(Path relative) {
        for (Path part : relative) {
            String name = part.toString();
            if (name.startsWith("_") || name.startsWith(".")) return true;
        }
        return relative.getFileName().toString().equalsIgnoreCase("README.md");
    }

    private boolean isNearDuplicate(SourceDocument source, List<SourceDocument> accepted) {
        if (source.tokens().isEmpty()) return false;
        for (SourceDocument other : accepted) {
            Set<String> intersection = new HashSet<>(source.tokens());
            intersection.retainAll(other.tokens());
            Set<String> union = new HashSet<>(source.tokens());
            union.addAll(other.tokens());
            if (!union.isEmpty() && (double) intersection.size() / union.size() >= NEAR_DUPLICATE_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private int sourcePriority(SourceDocument source) {
        return switch (source.metadata().getOrDefault("source_level", "P9").toUpperCase(Locale.ROOT)) {
            case "P0" -> 0;
            case "P1" -> 1;
            case "P2" -> 2;
            case "P3" -> 3;
            default -> 9;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceFirst("/+$", "");
    }

    private Set<String> tokenSet(String text) {
        Set<String> result = new HashSet<>();
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private FrontMatter parseFrontMatter(String raw) {
        String normalized = raw.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) return new FrontMatter(Map.of(), normalized);
        int closing = normalized.indexOf("\n---\n", 4);
        if (closing < 0) return new FrontMatter(Map.of(), normalized);

        Map<String, String> metadata = new LinkedHashMap<>();
        String[] lines = normalized.substring(4, closing).split("\n");
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            metadata.put(key, value);
        }
        return new FrontMatter(Map.copyOf(metadata), normalized.substring(closing + 5).trim());
    }

    private String categoryFromPath(Path relative) {
        if (relative.getNameCount() > 1) return relative.getName(0).toString();
        String filename = relative.getFileName().toString();
        int underscore = filename.indexOf('_');
        return underscore > 0 ? filename.substring(0, underscore) : "00-overview";
    }

    private String firstHeading(String text, String fallback) {
        Matcher matcher = HEADING.matcher(text);
        return matcher.find() ? matcher.group(2).trim() : fallback;
    }

    private String corpusSignature(Path root, List<Path> files) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Path file : files) {
                digest.update(root.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(file));
            }
            digest.update("splitter:420:120:40:v1.3".getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record FrontMatter(Map<String, String> metadata, String body) { }
    private record HeadingPosition(int start, int end, int level, String title) { }
    private record SourceDocument(Path path, String relativePath, String title, String category, String body,
                                  Map<String, String> metadata, Set<String> tokens) {
        Map<String, Object> documentMetadata() {
            return new HashMap<>(metadata);
        }
    }

    public record PreparedKnowledge(
            int files,
            int readerDocuments,
            int sectionDocuments,
            List<Document> chunks,
            Map<String, Integer> chunksPerFile,
            Map<String, Integer> categories,
            Map<String, Integer> sourceLevels,
            int sources,
            int duplicatesSkipped,
            String signature
    ) { }
}
