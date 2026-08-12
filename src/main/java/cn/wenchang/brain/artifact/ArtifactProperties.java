package cn.wenchang.brain.artifact;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wenchang.artifact")
public class ArtifactProperties {

    private Path root = Path.of("data", "artifacts");

    public Path getRoot() { return root; }
    public void setRoot(Path root) { this.root = root; }
}
