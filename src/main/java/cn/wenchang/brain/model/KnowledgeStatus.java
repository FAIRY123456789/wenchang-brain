package cn.wenchang.brain.model;

import java.time.Instant;
import java.util.Map;

public record KnowledgeStatus(
        String state,
        int files,
        int documents,
        int chunks,
        Map<String, Integer> categories,
        Map<String, Integer> sourceLevels,
        int sources,
        Map<String, Integer> chunksPerFile,
        String vectorStoreFile,
        boolean persisted,
        Instant lastIndexedAt,
        String embeddingMode,
        int previousChunks,
        int addedChunks,
        int duplicatesSkipped
) {
    public static KnowledgeStatus empty(String file, String mode) {
        return new KnowledgeStatus("EMPTY", 0, 0, 0, Map.of(), Map.of(), 0, Map.of(),
                file, false, null, mode, 0, 0, 0);
    }

    /** V1.2 compatibility accessor. */
    public int sourceFiles() { return files; }

    /** V1.2 compatibility accessor. */
    public Instant indexedAt() { return lastIndexedAt; }
}
