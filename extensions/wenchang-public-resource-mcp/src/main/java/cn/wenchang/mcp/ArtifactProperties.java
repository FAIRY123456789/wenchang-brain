package cn.wenchang.mcp;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wenchang.artifact")
public class ArtifactProperties {

    private Path root;
    private String downloadBaseUrl = "/api/artifacts";
    private String policiesFile = "wenchang-policies.json";
    private Path sourcesIndexFile;
    private int maxExportRows = 5000;

    public Path getRoot() { return root; }
    public void setRoot(Path root) { this.root = root; }
    public String getDownloadBaseUrl() { return downloadBaseUrl; }
    public void setDownloadBaseUrl(String downloadBaseUrl) { this.downloadBaseUrl = downloadBaseUrl; }
    public String getPoliciesFile() { return policiesFile; }
    public void setPoliciesFile(String policiesFile) { this.policiesFile = policiesFile; }
    public Path getSourcesIndexFile() { return sourcesIndexFile; }
    public void setSourcesIndexFile(Path sourcesIndexFile) { this.sourcesIndexFile = sourcesIndexFile; }
    public int getMaxExportRows() { return maxExportRows; }
    public void setMaxExportRows(int maxExportRows) {
        this.maxExportRows = Math.max(1, Math.min(maxExportRows, 50_000));
    }
}
