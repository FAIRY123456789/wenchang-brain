package cn.wenchang.brain.model;

public record SourceRef(
        String file,
        String section,
        String category,
        String sourceOrganization,
        String sourceUrl,
        String sourceLevel,
        String publishedAt,
        String retrievedAt
) {
    public SourceRef(String file, String section) {
        this(file, section, "", "", "", "", "", "");
    }
}
