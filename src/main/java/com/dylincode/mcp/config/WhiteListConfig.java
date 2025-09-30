package com.dylincode.mcp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * 應用程式白名單
 */
@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "app.security.allowed")
public class WhiteListConfig {
    Set<String> emails;
    Set<String> domains;
}
