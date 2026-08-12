package cn.wenchang.brain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameConversationRequest(
        @NotBlank(message = "title 不能为空") @Size(max = 80, message = "title 最多 80 个字符") String title
) { }
