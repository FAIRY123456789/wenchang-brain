package cn.wenchang.brain.artifact;

public record ArtifactMetadata(
        String id,
        String conversationId,
        String type,
        String filename,
        String createdAt,
        String createdByAgent,
        String skillId,
        int sourceCount,
        String contentType,
        long size,
        String relativePath,
        String downloadUrl
) {
    public ArtifactDescriptor descriptor() {
        String displayName = filename == null ? "" : filename.replaceFirst("\\.[^.]+$", "");
        return new ArtifactDescriptor(id, conversationId, type, filename, displayName, contentType, size,
                createdAt, downloadUrl, false, sourceCount, createdByAgent, skillId);
    }
}
