package cn.wenchang.mcp;

public record ArtifactManifest(
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
) { }
