package cn.wenchang.brain.service;

import cn.wenchang.brain.persistence.MessageEntity;
import cn.wenchang.brain.persistence.MessageRole;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationMemoryService {

    private static final int RESTORE_LIMIT = 18;
    private final ChatMemory chatMemory;
    private final ConversationService conversationService;
    private final Set<String> restored = ConcurrentHashMap.newKeySet();

    public ConversationMemoryService(ChatMemory chatMemory, ConversationService conversationService) {
        this.chatMemory = chatMemory;
        this.conversationService = conversationService;
    }

    public void ensureRestored(String conversationId) {
        if (restored.contains(conversationId)) return;
        synchronized (conversationId.intern()) {
            if (restored.contains(conversationId)) return;
            List<MessageEntity> active = conversationService.activeMessages(conversationId);
            int from = Math.max(0, active.size() - RESTORE_LIMIT);
            List<Message> messages = active.subList(from, active.size()).stream().map(this::springMessage).toList();
            chatMemory.clear(conversationId);
            if (!messages.isEmpty()) chatMemory.add(conversationId, messages);
            restored.add(conversationId);
        }
    }

    public void clear(String conversationId) {
        chatMemory.clear(conversationId);
        restored.remove(conversationId);
    }

    public void clearAll(Iterable<String> conversationIds) { conversationIds.forEach(this::clear); }

    public int restoredMessageCount(String conversationId) {
        ensureRestored(conversationId);
        return chatMemory.get(conversationId).size();
    }

    private Message springMessage(MessageEntity entity) {
        return entity.getRole() == MessageRole.USER
                ? new UserMessage(entity.getContent()) : new AssistantMessage(entity.getContent());
    }
}