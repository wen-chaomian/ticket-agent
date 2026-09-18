package com.ticket.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.constants.MQTopic;
import com.ticket.entity.Ticket;
import com.ticket.service.StatusService;
import com.ticket.service.TicketService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQTopic.TICKET_ANALYZE_TOPIC,
        consumerGroup = MQTopic.ANALYZE_CONSUMER_GROUP
)
public class TicketAnalyzeConsumer implements RocketMQListener<String> {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private StatusService statusService;

    @Autowired
    @Qualifier("pythonRestTemplate")
    private RestTemplate pythonRestTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(String ticketIdStr) {
        Long ticketId = Long.valueOf(ticketIdStr);
        log.info("收到工单分析消息，ticketId: {}", ticketId);

        Ticket ticket = ticketService.getById(ticketId);
        if (ticket == null) {
            log.error("工单不存在，ticketId: {}", ticketId);
            return;
        }

        // 更新状态为处理中
        statusService.updateProcessing(ticket.getTicketNo());

        String url = "http://localhost:8000/analyze";
        Map<String, String> request = Map.of(
                "ticketId", String.valueOf(ticket.getId()),
                "title", ticket.getTitle(),
                "description", ticket.getDescription()
        );

        try {
            String result = pythonRestTemplate.postForObject(url, request, String.class);
            log.info("Python Agent 返回结果: {}", result);

            // 解析返回结果
            Map<String, Object> response = objectMapper.readValue(result, Map.class);
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            String status = (String) data.get("status");

            if ("WAITING_HUMAN".equals(status)) {
                // 待人工确认
                statusService.updateWaitingHuman(ticket.getTicketNo(), data);
                ticketService.updateAiResult(ticket.getId(), data);
                log.info("工单进入待人工确认状态，ticketId: {}", ticketId);

            } else if ("SUCCESS".equals(status)) {
                // 直接完成
                String solution = (String) data.get("solution");
                statusService.updateSuccess(ticket.getTicketNo(), solution);
                ticketService.updateSolution(ticket.getId(), solution);
                log.info("工单已自动解决，ticketId: {}", ticketId);

            } else {
                log.warn("未知状态: {}", status);
            }

        } catch (Exception e) {
            log.error("调用 Python Agent 失败，将自动重试: {}", e.getMessage(), e);
            // 抛出异常让 RocketMQ 自动重试（默认最多16次，间隔逐渐增加）
            // 重试次数用完后消息进入死信队列，届时可在死信队列中手动处理
            throw new RuntimeException("Python Agent 调用失败: " + e.getMessage(), e);
        }
    }
}