package cn.wenchang.brain.local;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 无密钥演示用的确定性 EmbeddingModel。
 * 它将中文单字、双字组合和拉丁词哈希到固定维度，再做 L2 归一化；
 * 因而同一关键词会落到同一方向，可真实驱动 SimpleVectorStore 的余弦相似度检索。
 * 生产模式设置 WENCHANG_AI_PROVIDER=openai 后会由官方 OpenAI EmbeddingModel 取代。
 */
public final class LocalFeatureHashEmbeddingModel implements EmbeddingModel {

    private final int dimensions;

    public LocalFeatureHashEmbeddingModel(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < request.getInstructions().size(); i++) {
            embeddings.add(new Embedding(embed(request.getInstructions().get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimensions];
        String normalized = text == null ? "" : text.toLowerCase().replaceAll("\\s+", " ");
        List<String> features = new ArrayList<>();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!Character.isWhitespace(c)) features.add(String.valueOf(c));
            if (i + 1 < normalized.length() && !Character.isWhitespace(c)
                    && !Character.isWhitespace(normalized.charAt(i + 1))) {
                features.add(normalized.substring(i, i + 2));
            }
        }
        for (String word : normalized.split("[^a-z0-9]+")) {
            if (!word.isBlank()) features.add(word);
        }
        for (String feature : features) {
            int hash = feature.hashCode();
            int index = Math.floorMod(hash, dimensions);
            vector[index] += (hash & 1) == 0 ? 1.0f : -1.0f;
        }
        double norm = 0;
        for (float value : vector) norm += value * value;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) vector[i] /= (float) norm;
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
