package com.dylincode.mcp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * 同步Confluence的哪些space
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.confluence.space")
public class ConfluenceConfig {
    Set<String> keys;
}
