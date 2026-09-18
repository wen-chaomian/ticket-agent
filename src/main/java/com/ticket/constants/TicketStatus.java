package com.ticket.constants;

public enum TicketStatus {

    WAITING(1, "待分配"),
    PROCESSING(2, "处理中"),
    WAITING_HUMAN(3, "待人工确认"),
    COMPLETED(4, "已完成"),
    CLOSED(5, "已关闭"),
    FAILED(6, "处理失败");

    private final Integer code;
    private final String desc;

    TicketStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static TicketStatus fromCode(Integer code) {
        for (TicketStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}