package com.dylincode.mcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 設定Lucene向量index位置
 */
@Configuration
public class AppConfig {
    @Bean
    public String indexDir(@Value("${app.indexDir}") String indexDir){
        return indexDir;
    }
}
