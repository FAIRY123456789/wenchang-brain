package cn.wenchang.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties({PublicResourceProperties.class, ArtifactProperties.class})
public class WenchangPublicResourceMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(WenchangPublicResourceMcpApplication.class, args);
    }

    @Bean
    ToolCallbackProvider publicResourceToolProvider(PublicResourceTools publicResourceTools,
                                                    ProductionArtifactTools productionArtifactTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(publicResourceTools, productionArtifactTools)
                .build();
    }
}
