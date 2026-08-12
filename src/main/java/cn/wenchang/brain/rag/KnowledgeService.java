package cn.wenchang.brain.rag;

import cn.wenchang.brain.config.WenchangProperties;
import cn.wenchang.brain.model.KnowledgeStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/** 管理知识摄取、语料指纹校验、向量持久化和可观测状态。 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final EmbeddingModel embeddingModel;
    private final WenchangProperties properties;
    private final KnowledgeIngestionSupervisor ingestionSupervisor;
    private final ObjectMapper objectMapper;

    private volatile SimpleVectorStore vectorStore;
    private volatile KnowledgeStatus status;

    public KnowledgeService(EmbeddingModel embeddingModel, WenchangProperties properties,
                            KnowledgeIngestionSupervisor ingestionSupervisor) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.ingestionSupervisor = ingestionSupervisor;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        this.status = KnowledgeStatus.empty(properties.getVectorStoreFile(), embeddingMode());
    }

    @PostConstruct
    public synchronized void initialize() {
        try {
            KnowledgeIngestionSupervisor.PreparedKnowledge prepared = ingestionSupervisor.prepare();
            Path storePath = storePath();
            KnowledgeStoreManifest manifest = readManifest();
            if (Files.isRegularFile(storePath) && manifest != null
                    && prepared.signature().equals(manifest.corpusSignature())
                    && embeddingMode().equals(manifest.embeddingMode())) {
                SimpleVectorStore loaded = SimpleVectorStore.builder(embeddingModel).build();
                loaded.load(storePath.toFile());
                this.vectorStore = loaded;
                this.status = buildStatus("LOADED", prepared, true, manifest.indexedAt(),
                        manifest.chunks(), 0);
                logSummary("VectorStore loaded; corpus fingerprint matched", prepared);
            } else {
                reindexPrepared(prepared);
            }
        } catch (Exception exception) {
            this.status = KnowledgeStatus.empty(properties.getVectorStoreFile(), embeddingMode());
            log.error("Knowledge initialization failed", exception);
        }
    }

    public synchronized KnowledgeStatus reindex() throws IOException {
        return reindexPrepared(ingestionSupervisor.prepare());
    }

    private KnowledgeStatus reindexPrepared(KnowledgeIngestionSupervisor.PreparedKnowledge prepared) throws IOException {
        log.info("================ KNOWLEDGE INGESTION START ================");
        int previousChunks = status == null ? 0 : status.chunks();
        SimpleVectorStore freshStore = SimpleVectorStore.builder(embeddingModel).build();
        if (!prepared.chunks().isEmpty()) freshStore.add(prepared.chunks());

        Path storePath = storePath();
        Files.createDirectories(storePath.getParent());
        Path temporaryStore = storePath.resolveSibling(storePath.getFileName() + ".tmp");
        freshStore.save(temporaryStore.toFile());
        replaceFile(temporaryStore, storePath);

        Instant indexedAt = Instant.now();
        writeManifest(new KnowledgeStoreManifest(prepared.signature(), embeddingMode(), prepared.chunks().size(), indexedAt));
        this.vectorStore = freshStore;
        this.status = buildStatus("READY", prepared, true, indexedAt, previousChunks,
                Math.max(0, prepared.chunks().size() - previousChunks));
        logSummary("Embedding completed; VectorStore saved", prepared);
        return status;
    }

    public SimpleVectorStore getVectorStore() { return vectorStore; }
    public KnowledgeStatus getStatus() { return status; }

    private KnowledgeStatus buildStatus(String state,
                                        KnowledgeIngestionSupervisor.PreparedKnowledge prepared,
                                        boolean persisted,
                                        Instant indexedAt,
                                        int previousChunks,
                                        int addedChunks) {
        return new KnowledgeStatus(state, prepared.files(), prepared.readerDocuments(), prepared.chunks().size(),
                prepared.categories(), prepared.sourceLevels(), prepared.sources(), prepared.chunksPerFile(),
                storePath().toString(), persisted, indexedAt, embeddingMode(), previousChunks, addedChunks,
                prepared.duplicatesSkipped());
    }

    private void logSummary(String result, KnowledgeIngestionSupervisor.PreparedKnowledge prepared) {
        log.info("Knowledge files: {}; source records: {}; reader documents: {}; sections: {}; chunks: {}",
                prepared.files(), prepared.sources(), prepared.readerDocuments(), prepared.sectionDocuments(),
                prepared.chunks().size());
        log.info("Categories: {}; source levels: {}; duplicates skipped: {}",
                prepared.categories(), prepared.sourceLevels(), prepared.duplicatesSkipped());
        log.info(result);
    }

    private KnowledgeStoreManifest readManifest() {
        Path path = manifestPath();
        if (!Files.isRegularFile(path)) return null;
        try {
            return objectMapper.readValue(path.toFile(), KnowledgeStoreManifest.class);
        } catch (IOException exception) {
            log.warn("Ignoring unreadable knowledge manifest: {}", path, exception);
            return null;
        }
    }

    private void writeManifest(KnowledgeStoreManifest manifest) throws IOException {
        Path path = manifestPath();
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), manifest);
        replaceFile(temporary, path);
    }

    private void replaceFile(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path storePath() {
        return Path.of(properties.getVectorStoreFile()).toAbsolutePath().normalize();
    }

    private Path manifestPath() {
        Path store = storePath();
        return store.resolveSibling(store.getFileName() + ".meta.json");
    }

    private String embeddingMode() { return embeddingModel.getClass().getSimpleName(); }

    private record KnowledgeStoreManifest(String corpusSignature, String embeddingMode, int chunks, Instant indexedAt) { }
}
