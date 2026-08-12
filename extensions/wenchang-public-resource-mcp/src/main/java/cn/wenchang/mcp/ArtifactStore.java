package cn.wenchang.mcp;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class ArtifactStore {

    private static final String DEFAULT_CONVERSATION = "unassigned";

    private final ArtifactProperties properties;
    private final DataAssetRepository dataRepository;
    private final ObjectMapper objectMapper;

    public ArtifactStore(ArtifactProperties properties, DataAssetRepository dataRepository) {
        this.properties = properties;
        this.dataRepository = dataRepository;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public ArtifactManifest create(String conversationId, String type, String filename, String contentType,
                                   String createdByAgent, String skillId, int sourceCount,
                                   ArtifactWriter writer) {
        String safeConversation = safeSegment(conversationId, DEFAULT_CONVERSATION);
        String safeFilename = safeFilename(filename);
        String id = UUID.randomUUID().toString();
        Path root = root();
        Path directory = safeResolve(root, safeConversation);
        Path target = safeResolve(directory, id + "-" + safeFilename);
        Path temporary = safeResolve(directory, "." + id + ".tmp");
        Path manifestPath = safeResolve(directory, id + ".metadata.json");
        Path manifestTemporary = safeResolve(directory, "." + id + ".metadata.tmp");
        try {
            Files.createDirectories(directory);
            writer.write(temporary);
            if (!Files.isRegularFile(temporary) || Files.size(temporary) == 0L) {
                throw new IOException("Artifact writer produced an empty file");
            }
            move(temporary, target);
            String base = normalizeDownloadBase(properties.getDownloadBaseUrl());
            ArtifactManifest manifest = new ArtifactManifest(id, safeConversation, type, safeFilename, Instant.now().toString(),
                    defaultText(createdByAgent, "wenchang"), blankToNull(skillId), Math.max(0, sourceCount),
                    contentType, Files.size(target), root.relativize(target).toString().replace('\\', '/'),
                    base + "/" + id + "/download");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestTemporary.toFile(), manifest);
            move(manifestTemporary, manifestPath);
            return manifest;
        }
        catch (IOException exception) {
            deleteQuietly(temporary);
            deleteQuietly(manifestTemporary);
            deleteQuietly(target);
            throw new IllegalStateException("Artifact generation failed: " + exception.getMessage(), exception);
        }
    }

    public Path root() {
        Path configured = properties.getRoot();
        Path value = configured != null && !configured.toString().isBlank()
                ? configured : dataRepository.snapshot().dataRoot().resolve("artifacts");
        return value.toAbsolutePath().normalize();
    }

    private Path safeResolve(Path parent, String child) {
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path result = normalizedParent.resolve(child).normalize();
        if (!result.startsWith(normalizedParent)) throw new IllegalArgumentException("Artifact path escapes root");
        return result;
    }

    private String safeSegment(String value, String fallback) {
        String candidate = defaultText(value, fallback).trim();
        if (!candidate.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}") || candidate.equals(".") || candidate.equals("..")) {
            throw new IllegalArgumentException("Invalid conversationId");
        }
        return candidate;
    }

    private String safeFilename(String value) {
        String candidate = Path.of(defaultText(value, "artifact.bin")).getFileName().toString().trim()
                .replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_")
                .replaceAll("[. ]+$", "");
        if (candidate.isBlank()) candidate = "artifact.bin";
        if (candidate.length() > 160) {
            int dot = candidate.lastIndexOf('.');
            String suffix = dot > 0 && candidate.length() - dot <= 12 ? candidate.substring(dot) : "";
            candidate = candidate.substring(0, Math.max(1, 160 - suffix.length())) + suffix;
        }
        return candidate;
    }

    private String normalizeDownloadBase(String value) {
        String base = defaultText(value, "/api/artifacts").trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base.isBlank() ? "/api/artifacts" : base;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException ignored) { }
    }

    @FunctionalInterface
    public interface ArtifactWriter {
        void write(Path path) throws IOException;
    }
}
