package cn.wenchang.brain.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ArtifactService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactService.class);

    private final Path root;
    private final ObjectMapper objectMapper;

    public ArtifactService(ArtifactProperties properties) {
        this.root = properties.getRoot().toAbsolutePath().normalize();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public List<ArtifactMetadata> list(String conversationId) {
        if (!Files.isDirectory(root)) return List.of();
        try (var files = Files.walk(root, 3)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".metadata.json"))
                    .map(this::readQuietly).filter(Objects::nonNull)
                    .filter(metadata -> conversationId == null || conversationId.isBlank()
                            || conversationId.equals(metadata.conversationId()))
                    .sorted(Comparator.comparing(ArtifactMetadata::createdAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件列表读取失败", exception);
        }
    }

    public ArtifactMetadata require(String id) {
        validateId(id);
        return list(null).stream().filter(metadata -> id.equals(metadata.id())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在"));
    }

    public ArtifactFile open(String id) {
        ArtifactMetadata metadata = require(id);
        Path path = resolveStoredFile(metadata);
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        verifyRealPath(path);
        return new ArtifactFile(metadata, path);
    }

    public void delete(String id) {
        ArtifactMetadata metadata = require(id);
        Path storedFile = resolveStoredFile(metadata);
        verifyRealPathIfPresent(storedFile);
        Path manifest = findManifest(id);
        try {
            Files.deleteIfExists(storedFile);
            Files.deleteIfExists(manifest);
        }
        catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件删除失败", exception);
        }
    }

    private ArtifactMetadata readQuietly(Path manifest) {
        try {
            Path normalized = manifest.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) return null;
            ArtifactMetadata metadata = objectMapper.readValue(manifest.toFile(), ArtifactMetadata.class);
            validateMetadata(metadata);
            return metadata;
        }
        catch (IOException | IllegalArgumentException exception) {
            log.warn("Ignoring invalid artifact manifest {}: {}", manifest.getFileName(), exception.getMessage());
            return null;
        }
    }

    private Path findManifest(String id) {
        if (!Files.isDirectory(root)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        try (var files = Files.walk(root, 3)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(id + ".metadata.json"))
                    .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在"));
        }
        catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件索引读取失败", exception);
        }
    }

    private Path resolveStoredFile(ArtifactMetadata metadata) {
        if (metadata.relativePath() == null || metadata.relativePath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件路径不存在");
        }
        Path relative = Path.of(metadata.relativePath());
        if (relative.isAbsolute()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件路径");
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件路径");
        return path;
    }

    private void verifyRealPath(Path path) {
        try {
            Path realRoot = root.toRealPath();
            if (!path.toRealPath().startsWith(realRoot)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件路径");
            }
        }
        catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在", exception);
        }
    }

    private void verifyRealPathIfPresent(Path path) {
        if (Files.exists(path)) verifyRealPath(path);
    }

    private void validateMetadata(ArtifactMetadata metadata) {
        if (metadata == null || metadata.id() == null || metadata.filename() == null
                || metadata.relativePath() == null) throw new IllegalArgumentException("Invalid artifact metadata");
        validateId(metadata.id());
        if (!Path.of(metadata.filename()).getFileName().toString().equals(metadata.filename())) {
            throw new IllegalArgumentException("Invalid artifact filename");
        }
        resolveStoredFile(metadata);
    }

    private void validateId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9-]{0,63}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件 ID");
        }
    }

    public record ArtifactFile(ArtifactMetadata metadata, Path path) { }
}
