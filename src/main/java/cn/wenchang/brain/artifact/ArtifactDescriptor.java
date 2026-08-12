package cn.wenchang.brain.artifact;

/** Public, storage-independent description of a generated task file. */
public record ArtifactDescriptor(
        String id,
        String conversationId,
        String type,
        String filename,
        String displayName,
        String mimeType,
        long sizeBytes,
        String createdAt,
        String downloadUrl,
        boolean previewAvailable,
        int sourceCount,
        String createdByAgent,
        String skillId
) { }
