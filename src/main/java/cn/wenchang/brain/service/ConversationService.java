package cn.wenchang.brain.service;

import cn.wenchang.brain.model.ChatResponseDto;
import cn.wenchang.brain.model.ConversationDetail;
import cn.wenchang.brain.model.ConversationSummary;
import cn.wenchang.brain.model.MessageRevisionOption;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ConversationService {

    public static final String LOCAL_OWNER = "local";
    private static final String UNTITLED = "新对话";
    private static final long ROOT_BRANCH = 0L;

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
        ensureLineage(conversation);
        conversation.selectAgent(agentId);
        if (UNTITLED.equals(conversation.getTitle()) && messageRepository.countByConversation_Id(conversationId) == 0) {
            conversation.rename(titleFrom(firstMessage));
        }
        return summary(conversation);
    }

    @Transactional
    public ConversationDetail detail(String id) {
        ConversationEntity conversation = require(id);
        List<MessageEntity> all = ensureLineage(conversation);
        return detail(conversation, all);
    }

    @Transactional
    public ConversationSummary rename(String id, String title) {
        ConversationEntity conversation = require(id);
        conversation.rename(normalizeTitle(title));
        return summary(conversation);
    }

    @Transactional
    public void prepareEdit(String id, Long editMessageId) {
        if (editMessageId == null) return;
        ConversationEntity conversation = require(id);
        List<MessageEntity> all = ensureLineage(conversation);
        MessageEntity target = requireMessage(conversation, editMessageId, all);
        if (target.getRole() != MessageRole.USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能编辑用户问题");
        }
        boolean onActiveBranch = activeChain(all, conversation.getActiveLeafMessageId()).stream()
                .anyMatch(message -> Objects.equals(message.getId(), editMessageId));
        if (!onActiveBranch) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只能编辑当前显示分支中的问题");
        }
        conversation.activateLeaf(target.getParentMessageId() == null ? ROOT_BRANCH : target.getParentMessageId());
    }

    @Transactional
    public Long appendUser(String id, String content) {
        return appendUser(id, content, "wenchang", null, null);
    }

    @Transactional
    public Long appendUser(String id, String content, String agentId, String skillId, Long editMessageId) {
        ConversationEntity conversation = require(id);
        List<MessageEntity> all = ensureLineage(conversation);
        Long activeLeaf = conversation.getActiveLeafMessageId();
        Long parent = Objects.equals(activeLeaf, ROOT_BRANCH) ? null : activeLeaf;
        String revisionGroup = UUID.randomUUID().toString();
        int revision = 1;

        if (editMessageId != null) {
            MessageEntity target = requireMessage(conversation, editMessageId, all);
            if (target.getRole() != MessageRole.USER) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能编辑用户问题");
            }
            if (!Objects.equals(parent, target.getParentMessageId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "编辑分支状态已变化，请刷新后重试");
            }
            revisionGroup = target.getRevisionGroupId();
            if (revisionGroup == null || revisionGroup.isBlank()) {
                revisionGroup = UUID.randomUUID().toString();
                target.ensureRevisionIdentity(revisionGroup, 1);
            }
            String group = revisionGroup;
            revision = all.stream()
                    .filter(message -> message.getRole() == MessageRole.USER)
                    .filter(message -> group.equals(message.getRevisionGroupId()))
                    .map(MessageEntity::getRevisionIndex)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo).orElse(1) + 1;
        }

        MessageEntity message = new MessageEntity(conversation, MessageRole.USER, content.trim(), Instant.now());
        message.configureLineage(parent, revisionGroup, revision);
        message.attachUserContext(normalizeAgent(agentId), normalizeSkill(skillId));
        MessageEntity saved = messageRepository.save(message);
        conversation.activateLeaf(saved.getId());
        return saved.getId();
    }

    @Transactional
    public void appendAssistant(String id, ChatResponseDto response) {
        ConversationEntity conversation = require(id);
        ensureLineage(conversation);
        appendAssistantInternal(conversation, response, conversation.getActiveLeafMessageId());
    }

    @Transactional
    public void appendAssistant(String id, ChatResponseDto response, Long userMessageId) {
        ConversationEntity conversation = require(id);
        ensureLineage(conversation);
        appendAssistantInternal(conversation, response, userMessageId);
    }

    @Transactional
    public ConversationDetail activateRevision(String id, Long userMessageId) {
        ConversationEntity conversation = require(id);
        List<MessageEntity> all = ensureLineage(conversation);
        MessageEntity target = requireMessage(conversation, userMessageId, all);
        if (target.getRole() != MessageRole.USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分支入口必须是用户问题");
        }
        conversation.activateLeaf(latestLeafFor(target.getId(), all));
        return detail(conversation, all);
    }

    @Transactional
    public List<MessageEntity> activeMessages(String id) {
        ConversationEntity conversation = require(id);
        List<MessageEntity> all = ensureLineage(conversation);
        return List.copyOf(activeChain(all, conversation.getActiveLeafMessageId()));
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

    private void appendAssistantInternal(ConversationEntity conversation, ChatResponseDto response, Long userMessageId) {
        if (userMessageId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "回答缺少对应的用户问题");
        }
        MessageEntity message = new MessageEntity(conversation, MessageRole.ASSISTANT,
                response.answer() == null ? "" : response.answer(), Instant.now());
        message.configureLineage(userMessageId, null, null);
        message.attachAssistantMetadata(response.traceId(), response.modelProvider(), response.modelName(),
                json(response.sources()), json(response.toolsUsed()), response.agentId(), response.skillId(),
                json(response.agentRun()), json(response.artifacts()));
        MessageEntity saved = messageRepository.save(message);
        conversation.activateLeaf(saved.getId());
    }

    private ConversationDetail detail(ConversationEntity conversation, List<MessageEntity> all) {
        List<MessageEntity> active = activeChain(all, conversation.getActiveLeafMessageId());
        List<PersistedMessageDto> messages = active.stream().map(entity -> messageDto(entity, all)).toList();
        return new ConversationDetail(conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(),
                conversation.getUpdatedAt(), conversation.getAgentId(), messages);
    }

    private List<MessageEntity> ensureLineage(ConversationEntity conversation) {
        List<MessageEntity> all = new ArrayList<>(messageRepository
                .findAllByConversation_IdOrderByCreatedAtAscIdAsc(conversation.getId()));
        if (all.isEmpty()) return all;

        boolean legacyLinear = conversation.getActiveLeafMessageId() == null
                && all.stream().allMatch(message -> message.getParentMessageId() == null);
        if (legacyLinear) {
            Long parent = null;
            for (MessageEntity message : all) {
                String group = message.getRole() == MessageRole.USER ? UUID.randomUUID().toString() : null;
                Integer revision = message.getRole() == MessageRole.USER ? 1 : null;
                message.configureLineage(parent, group, revision);
                parent = message.getId();
            }
            conversation.activateLeaf(parent);
            return all;
        }

        all.stream().filter(message -> message.getRole() == MessageRole.USER).forEach(message ->
                message.ensureRevisionIdentity(UUID.randomUUID().toString(), 1));
        if (conversation.getActiveLeafMessageId() == null) {
            conversation.activateLeaf(all.get(all.size() - 1).getId());
        }
        return all;
    }

    private List<MessageEntity> activeChain(List<MessageEntity> all, Long leafId) {
        if (all.isEmpty() || leafId == null) return List.of();
        Map<Long, MessageEntity> byId = new HashMap<>();
        all.forEach(message -> byId.put(message.getId(), message));
        List<MessageEntity> reversed = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long cursor = leafId;
        while (cursor != null && visited.add(cursor)) {
            MessageEntity message = byId.get(cursor);
            if (message == null) break;
            reversed.add(message);
            cursor = message.getParentMessageId();
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private Long latestLeafFor(Long rootMessageId, List<MessageEntity> all) {
        Map<Long, List<Long>> children = new HashMap<>();
        all.forEach(message -> {
            if (message.getParentMessageId() != null) {
                children.computeIfAbsent(message.getParentMessageId(), ignored -> new ArrayList<>()).add(message.getId());
            }
        });
        Set<Long> descendants = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(rootMessageId);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            if (!descendants.add(current)) continue;
            children.getOrDefault(current, List.of()).forEach(queue::addLast);
        }
        return descendants.stream()
                .filter(candidate -> children.getOrDefault(candidate, List.of()).stream()
                        .noneMatch(descendants::contains))
                .max(Long::compareTo)
                .orElse(rootMessageId);
    }

    private MessageEntity requireMessage(ConversationEntity conversation, Long messageId, List<MessageEntity> all) {
        return all.stream().filter(message -> Objects.equals(message.getId(), messageId))
                .filter(message -> conversation.getId().equals(message.getConversation().getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消息不存在"));
    }

    private PersistedMessageDto messageDto(MessageEntity entity, List<MessageEntity> all) {
        List<MessageRevisionOption> revisions = List.of();
        if (entity.getRole() == MessageRole.USER && entity.getRevisionGroupId() != null) {
            revisions = all.stream()
                    .filter(message -> message.getRole() == MessageRole.USER)
                    .filter(message -> entity.getRevisionGroupId().equals(message.getRevisionGroupId()))
                    .sorted(Comparator.comparing(message ->
                            message.getRevisionIndex() == null ? Integer.MAX_VALUE : message.getRevisionIndex()))
                    .map(message -> new MessageRevisionOption(message.getId(),
                            message.getRevisionIndex() == null ? 1 : message.getRevisionIndex(),
                            Objects.equals(message.getId(), entity.getId())))
                    .toList();
        }
        return new PersistedMessageDto(entity.getId(), entity.getRole(), entity.getContent(), entity.getCreatedAt(),
                entity.getParentMessageId(), entity.getRevisionGroupId(), entity.getRevisionIndex(), revisions,
                entity.getTraceId(), entity.getModelProvider(), entity.getModelName(), entity.getSourcesJson(),
                entity.getToolsUsedJson(), entity.getAgentId(), entity.getSkillId(), entity.getAgentRunJson(),
                entity.getArtifactsJson());
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

    private String normalizeTitle(String title) {
        String value = title == null ? "" : title.replaceAll("\\s+", " ").trim();
        if (value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题不能为空");
        int count = value.codePointCount(0, value.length());
        return count <= 80 ? value : value.substring(0, value.offsetByCodePoints(0, 80));
    }

    private String normalizeAgent(String value) {
        return value == null || value.isBlank() ? "wenchang" : value.trim();
    }

    private String normalizeSkill(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { return "[]"; }
    }
}