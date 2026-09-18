package com.ticket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean("pythonRestTemplate")
    public RestTemplate pythonRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 连接超时 2 秒
        factory.setConnectTimeout(2000);
        // 读取超时 30 秒（AI 分析可能较慢）
        factory.setReadTimeout(30000);
        return new RestTemplate(factory);
    }
}
