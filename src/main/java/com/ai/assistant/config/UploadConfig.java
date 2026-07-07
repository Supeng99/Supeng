package com.ai.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;
import java.util.Arrays;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "upload")
public class UploadConfig {

    private String path = "./uploads";
    private List<String> allowedTypes = Arrays.asList("pdf", "docx", "doc", "txt");
}
