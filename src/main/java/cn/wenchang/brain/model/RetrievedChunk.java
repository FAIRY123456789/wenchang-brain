package cn.wenchang.brain.model;

public record RetrievedChunk(
        int rank,
        String documentId,
        String file,
        String section,
        String category,
        String sourceOrganization,
        String sourceUrl,
        String sourceLevel,
        String publishedAt,
        String retrievedAt,
        Double score,
        String preview
) {
    public RetrievedChunk(int rank, String documentId, String file, String section, Double score, String preview) {
        this(rank, documentId, file, section, "", "", "", "", "", "", score, preview);
    }
}
