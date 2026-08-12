package cn.wenchang.brain.controller;

import cn.wenchang.brain.model.ModelConnectionTestResult;
import cn.wenchang.brain.model.RuntimeModelRequest;
import cn.wenchang.brain.model.RuntimeModelStatus;
import cn.wenchang.brain.runtime.RuntimeChatModelProvider;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/settings/model")
public class ModelSettingsController {

    private final RuntimeChatModelProvider modelProvider;

    public ModelSettingsController(RuntimeChatModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    @GetMapping
    public RuntimeModelStatus get() { return modelProvider.settingsStatus(); }

    @PutMapping
    public RuntimeModelStatus configure(@Valid @RequestBody RuntimeModelRequest request) {
        return modelProvider.configure(request);
    }

    @PostMapping("/test")
    public ResponseEntity<ModelConnectionTestResult> test(@Valid @RequestBody RuntimeModelRequest request) {
        ModelConnectionTestResult result = modelProvider.testConnection(request);
        return ResponseEntity.status(result.success() ? 200 : 422).body(result);
    }

    @PostMapping("/clear")
    public RuntimeModelStatus clear() { return modelProvider.clear(); }

    @PostMapping("/restore-default")
    public RuntimeModelStatus restoreDefault() { return modelProvider.restoreDefault(); }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception exception) {
        String message = exception.getMessage() == null ? "设置失败" : exception.getMessage()
                .replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "***");
        return ResponseEntity.badRequest().body(Map.of("error", exception.getClass().getSimpleName(),
                "message", message));
    }
}
