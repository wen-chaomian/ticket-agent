package com.ticket.constants;

public class MQTopic {

    /**
     * 工单分析请求 Topic
     */
    public static final String TICKET_ANALYZE_TOPIC = "ticket-analyze-topic";

    /**
     * 工单确认结果 Topic
     */
    public static final String TICKET_CONFIRM_TOPIC = "ticket-confirm-topic";

    /**
     * 消费者组
     */
    public static final String ANALYZE_CONSUMER_GROUP = "ticket-analyze-consumer-group";
    public static final String CONFIRM_CONSUMER_GROUP = "ticket-confirm-consumer-group";
}