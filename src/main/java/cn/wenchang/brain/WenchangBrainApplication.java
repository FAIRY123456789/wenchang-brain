package cn.wenchang.brain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WenchangBrainApplication {

    public static void main(String[] args) {
        SpringApplication.run(WenchangBrainApplication.class, args);
    }
}
