package cn.wenchang.mcp;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wenchang.public-resource")
public class PublicResourceProperties {

    private Path dataRoot;
    private String publicServicesFile = "wenchang-public-services.json";
    private String townshipsFile = "wenchang-townships.json";
    private String placesFile = "wenchang-places.json";
    private int maxResults = 20;

    public Path getDataRoot() {
        return dataRoot;
    }

    public void setDataRoot(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    public String getPublicServicesFile() {
        return publicServicesFile;
    }

    public void setPublicServicesFile(String publicServicesFile) {
        this.publicServicesFile = publicServicesFile;
    }

    public String getTownshipsFile() {
        return townshipsFile;
    }

    public void setTownshipsFile(String townshipsFile) {
        this.townshipsFile = townshipsFile;
    }

    public String getPlacesFile() {
        return placesFile;
    }

    public void setPlacesFile(String placesFile) {
        this.placesFile = placesFile;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = Math.max(1, Math.min(maxResults, 100));
    }
}
