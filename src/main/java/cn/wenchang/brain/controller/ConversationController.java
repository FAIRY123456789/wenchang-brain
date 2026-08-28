package cn.wenchang.brain.controller;

import cn.wenchang.brain.model.ConversationDetail;
import cn.wenchang.brain.model.ConversationSummary;
import cn.wenchang.brain.model.RenameConversationRequest;
import cn.wenchang.brain.service.ConversationMemoryService;
import cn.wenchang.brain.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationMemoryService memoryService;

    public ConversationController(ConversationService conversationService, ConversationMemoryService memoryService) {
        this.conversationService = conversationService;
        this.memoryService = memoryService;
    }

    @GetMapping
    public List<ConversationSummary> list() { return conversationService.list(); }

    @PostMapping
    public ConversationSummary create() { return conversationService.create(); }

    @GetMapping("/{id}")
    public ConversationDetail detail(@PathVariable String id) {
        ConversationDetail detail = conversationService.detail(id);
        memoryService.ensureRestored(id);
        return detail;
    }

    @PostMapping("/{id}/messages/{messageId}/activate")
    public ConversationDetail activateRevision(@PathVariable String id, @PathVariable Long messageId) {
        ConversationDetail detail = conversationService.activateRevision(id, messageId);
        memoryService.clear(id);
        return detail;
    }

    @PatchMapping("/{id}")
    public ConversationSummary rename(@PathVariable String id,
                                      @Valid @RequestBody RenameConversationRequest request) {
        return conversationService.rename(id, request.title());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        conversationService.delete(id);
        memoryService.clear(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        List<String> ids = conversationService.list().stream().map(ConversationSummary::id).toList();
        conversationService.deleteAll();
        memoryService.clearAll(ids);
        return ResponseEntity.noContent().build();
    }
}