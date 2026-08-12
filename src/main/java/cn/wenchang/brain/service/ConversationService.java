package cn.wenchang.brain.service;

import cn.wenchang.brain.model.ChatResponseDto;
import cn.wenchang.brain.model.ConversationDetail;
import cn.wenchang.brain.model.ConversationSummary;
import cn.wenchang.brain.model.PersistedMessageDto;
import cn.wenchang.brain.persistence.ConversationEntity;
import cn.wenchang.brain.persistence.ConversationRepository;
import cn.wenchang.brain.persistence.MessageEntity;
import cn.wenchang.brain.persistence.MessageRepository;
import cn.wenchang.brain.persistence.MessageRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    public static final String LOCAL_OWNER = "local";
    private static final String UNTITLED = "新对话";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public ConversationService(ConversationRepository conversationRepository, MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list() {
        return conversationRepository.findAllByOwnerIdOrderByUpdatedAtDesc(LOCAL_OWNER)
                .stream().map(this::summary).toList();
    }

    @Transactional
    public ConversationSummary create() {
        Instant now = Instant.now();
        return summary(conversationRepository.save(
                new ConversationEntity(UUID.randomUUID().toString(), UNTITLED, LOCAL_OWNER, now)));
    }

    @Transactional
    public ConversationSummary resolveForChat(String conversationId, String firstMessage) {
        return resolveForChat(conversationId, firstMessage, "wenchang");
    }

    @Transactional
    public ConversationSummary resolveForChat(String conversationId, String firstMessage, String agentId) {
        if (conversationId == null || conversationId.isBlank()) {
            Instant now = Instant.now();
            ConversationEntity created = new ConversationEntity(UUID.randomUUID().toString(),
                    titleFrom(firstMessage), LOCAL_OWNER, now);
            created.selectAgent(agentId);
            return summary(conversationRepository.save(created));
        }
        ConversationEntity conversation = require(conversationId);
        conversation.selectAgent(agentId);
        if (UNTITLED.equals(conversation.getTitle()) && messageRepository.countByConversation_Id(conversationId) == 0) {
            conversation.rename(titleFrom(firstMessage));
        }
        return summary(conversation);
    }

    @Transactional(readOnly = true)
    public ConversationDetail detail(String id) {
        ConversationEntity conversation = require(id);
        List<PersistedMessageDto> messages = messageRepository
                .findAllByConversation_IdOrderByCreatedAtAscIdAsc(id).stream().map(this::messageDto).toList();
        return new ConversationDetail(conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(),
                conversation.getUpdatedAt(), conversation.getAgentId(), messages);
    }

    @Transactional
    public ConversationSummary rename(String id, String title) {
        ConversationEntity conversation = require(id);
        conversation.rename(normalizeTitle(title));
        return summary(conversation);
    }

    @Transactional
    public void appendUser(String id, String content) {
        ConversationEntity conversation = require(id);
        messageRepository.save(new MessageEntity(conversation, MessageRole.USER, content.trim(), Instant.now()));
        conversation.touch();
    }

    @Transactional
    public void appendAssistant(String id, ChatResponseDto response) {
        ConversationEntity conversation = require(id);
        MessageEntity message = new MessageEntity(conversation, MessageRole.ASSISTANT,
                response.answer() == null ? "" : response.answer(), Instant.now());
        message.attachAssistantMetadata(response.traceId(), response.modelProvider(), response.modelName(),
                json(response.sources()), json(response.toolsUsed()), response.agentId(), response.skillId(),
                json(response.agentRun()), json(response.artifacts()));
        messageRepository.save(message);
        conversation.touch();
    }

    @Transactional
    public void delete(String id) { conversationRepository.delete(require(id)); }

    @Transactional
    public void deleteAll() { conversationRepository.deleteAllByOwnerId(LOCAL_OWNER); }

    public String titleFrom(String message) {
        String value = message == null ? "" : message.replaceAll("\\s+", " ").trim();
        value = value.replaceFirst("^(请问|请|麻烦|能否|可以|帮我)?(简单|详细)?(介绍一下|介绍|说说|讲讲|告诉我)?", "");
        value = value.replaceFirst("^[，。！？、：:,.!?\\s]+", "").trim();
        if (value.isBlank()) value = UNTITLED;
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints > 22) value = value.substring(0, value.offsetByCodePoints(0, 22)) + "…";
        return value;
    }

    private ConversationEntity require(String id) {
        return conversationRepository.findById(id)
                .filter(item -> LOCAL_OWNER.equals(item.getOwnerId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    private ConversationSummary summary(ConversationEntity entity) {
        return new ConversationSummary(entity.getId(), entity.getTitle(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getAgentId());
    }

    private PersistedMessageDto messageDto(MessageEntity entity) {
        return new PersistedMessageDto(entity.getId(), entity.getRole(), entity.getContent(), entity.getCreatedAt(),
                entity.getTraceId(), entity.getModelProvider(), entity.getModelName(), entity.getSourcesJson(),
                entity.getToolsUsedJson(), entity.getAgentId(), entity.getSkillId(), entity.getAgentRunJson(),
                entity.getArtifactsJson());
    }

    private String normalizeTitle(String title) {
        String value = title == null ? "" : title.replaceAll("\\s+", " ").trim();
        if (value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题不能为空");
        int count = value.codePointCount(0, value.length());
        return count <= 80 ? value : value.substring(0, value.offsetByCodePoints(0, 80));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { return "[]"; }
    }
}
