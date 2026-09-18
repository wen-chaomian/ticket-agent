package com.ticket.constants;

public class RedisKeys {

    /**
     * 工单状态缓存 Key
     * ticket:status:{ticketNo}
     */
    public static final String TICKET_STATUS_PREFIX = "ticket:status:";

    /**
     * 工单详情缓存 Key
     * ticket:detail:{ticketNo}
     */
    public static final String TICKET_DETAIL_PREFIX = "ticket:detail:";

    /**
     * 工单编号生成器 Key
     */
    public static final String TICKET_NO_GENERATOR = "ticket:no:generator";

    public static String ticketStatusKey(String ticketNo) {
        return TICKET_STATUS_PREFIX + ticketNo;
    }

    public static String ticketDetailKey(String ticketNo) {
        return TICKET_DETAIL_PREFIX + ticketNo;
    }
}