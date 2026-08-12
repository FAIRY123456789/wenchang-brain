package cn.wenchang.brain.local;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 仅供无网络自动化测试使用；正式 HTTP Chat 在模型未配置时会被控制器拒绝。 */
public final class DevelopmentStubChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(extract(prompt.getContents())))));
    }

    private String extract(String prompt) {
        Set<String> lines = new LinkedHashSet<>();
        int contextMarker = prompt.indexOf("Context information is below");
        StringBuilder evidence = new StringBuilder();
        int webMarker = prompt.indexOf("联网搜索结果：");
        if (webMarker >= 0) {
            int webEnd = contextMarker > webMarker ? contextMarker : prompt.length();
            evidence.append(prompt, webMarker, webEnd).append('\n');
        }
        evidence.append(contextMarker >= 0 ? prompt.substring(contextMarker) : prompt);
        for (String raw : evidence.toString().split("[\\r\\n]+")) {
            String line = raw.replaceAll("^[#>*\\-\\s]+", "").trim();
            if (line.length() >= 12 && line.length() <= 260
                    && !line.contains("Context information") && !line.contains("Answer the query")
                    && !line.contains("Given the context") && !line.contains("provided history")
                    && !line.contains("reply to the user") && !line.contains("can't answer")) lines.add(line);
            if (lines.size() >= 10) break;
        }
        List<String> selected = new ArrayList<>(lines);
        if (selected.isEmpty()) return "模型未配置，请进入模型设置。";
        StringBuilder answer = new StringBuilder("根据文昌知识库检索结果：\n\n");
        for (String line : selected) answer.append("- ").append(line).append('\n');
        return answer.toString();
    }
}
