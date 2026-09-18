package com.ticket.config;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMQConfig {

    @Bean
    public RocketMQTemplate rocketMQTemplate() throws Exception {
        RocketMQTemplate template = new RocketMQTemplate();

        DefaultMQProducer producer = new DefaultMQProducer();
        // 直接写死配置
        producer.setNamesrvAddr("192.168.217.130:9876");
        producer.setProducerGroup("ticket-producer-group");
        producer.setSendMsgTimeout(3000);
        producer.setRetryTimesWhenSendFailed(2);

        template.setProducer(producer);
        return template;
    }
}