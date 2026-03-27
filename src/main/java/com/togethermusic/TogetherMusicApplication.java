package com.togethermusic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Together Music 主启动类
 * 排除 SecurityAutoConfiguration，使用 Sa-Token 替代 Spring Security Web 安全配置
 * 保留 Spring Security 的 BCrypt 密码编码器
 */
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
@EnableScheduling
@EnableConfigurationProperties
public class TogetherMusicApplication {

    public static void main(String[] args) {
        SpringApplication.run(TogetherMusicApplication.class, args);
    }
}
