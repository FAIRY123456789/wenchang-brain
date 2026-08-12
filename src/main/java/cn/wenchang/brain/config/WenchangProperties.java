package cn.wenchang.brain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wenchang")
public class WenchangProperties {

    private String version = "V1.5";
    private String knowledgeDir = "knowledge";
    private String vectorStoreFile = "data/wenchang-vector-store.json";
    private String traceFile = "logs/agent-trace.jsonl";
    private String researchDir = "data/research";
    private int topK = 6;
    private double similarityThreshold = 0.15;
    private final WebSearch webSearch = new WebSearch();

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getKnowledgeDir() { return knowledgeDir; }
    public void setKnowledgeDir(String knowledgeDir) { this.knowledgeDir = knowledgeDir; }
    public String getVectorStoreFile() { return vectorStoreFile; }
    public void setVectorStoreFile(String vectorStoreFile) { this.vectorStoreFile = vectorStoreFile; }
    public String getTraceFile() { return traceFile; }
    public void setTraceFile(String traceFile) { this.traceFile = traceFile; }
    public String getResearchDir() { return researchDir; }
    public void setResearchDir(String researchDir) { this.researchDir = researchDir; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    public WebSearch getWebSearch() { return webSearch; }

    public static class WebSearch {
        private boolean enabled = true;
        private String provider = "auto";
        private String endpoint = "https://www.sogou.com/web";
        private String apiKey = "";
        private String tavilyApiKey = "";
        private String tavilyEndpoint = "https://api.tavily.com/search";
        private String braveApiKey = "";
        private String braveEndpoint = "https://api.search.brave.com/res/v1/web/search";
        private String fallbackOrder = "tavily,brave";
        private boolean allowHtmlFallback = false;
        private int cacheTtlSeconds = 300;
        private int circuitFailureThreshold = 3;
        private int circuitCooldownSeconds = 60;
        private int timeoutSeconds = 25;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getTavilyApiKey() { return tavilyApiKey; }
        public void setTavilyApiKey(String tavilyApiKey) { this.tavilyApiKey = tavilyApiKey; }
        public String getTavilyEndpoint() { return tavilyEndpoint; }
        public void setTavilyEndpoint(String tavilyEndpoint) { this.tavilyEndpoint = tavilyEndpoint; }
        public String getBraveApiKey() { return braveApiKey; }
        public void setBraveApiKey(String braveApiKey) { this.braveApiKey = braveApiKey; }
        public String getBraveEndpoint() { return braveEndpoint; }
        public void setBraveEndpoint(String braveEndpoint) { this.braveEndpoint = braveEndpoint; }
        public String getFallbackOrder() { return fallbackOrder; }
        public void setFallbackOrder(String fallbackOrder) { this.fallbackOrder = fallbackOrder; }
        public boolean isAllowHtmlFallback() { return allowHtmlFallback; }
        public void setAllowHtmlFallback(boolean allowHtmlFallback) { this.allowHtmlFallback = allowHtmlFallback; }
        public int getCacheTtlSeconds() { return cacheTtlSeconds; }
        public void setCacheTtlSeconds(int cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }
        public int getCircuitFailureThreshold() { return circuitFailureThreshold; }
        public void setCircuitFailureThreshold(int circuitFailureThreshold) { this.circuitFailureThreshold = circuitFailureThreshold; }
        public int getCircuitCooldownSeconds() { return circuitCooldownSeconds; }
        public void setCircuitCooldownSeconds(int circuitCooldownSeconds) { this.circuitCooldownSeconds = circuitCooldownSeconds; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
