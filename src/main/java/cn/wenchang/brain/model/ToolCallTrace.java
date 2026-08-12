package cn.wenchang.brain.model;

/** 单次工具执行的生产级审计字段。 */
public record ToolCallTrace(
        String traceId,
        String toolName,
        String toolSource,
        String stage,
        String input,
        String status,
        String errorType,
        String output,
        long latencyMs
) {
    /** 兼容旧调用方，旧 Trace 一律按成功的 Native Tool 解释。 */
    public ToolCallTrace(String traceId, String toolName, String toolInput, String toolOutput, long latencyMs) {
        this(traceId, toolName, "NATIVE", "TOOL_EXECUTION", toolInput,
                "SUCCESS", "", toolOutput, latencyMs);
    }

    /** Java 兼容访问器；JSON 使用明确的 toolSource/input/output 字段。 */
    public String source() { return toolSource; }
    public String toolInput() { return input; }
    public String toolOutput() { return output; }
}
