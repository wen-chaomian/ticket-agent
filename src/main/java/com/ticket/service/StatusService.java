package com.ticket.service;

import com.ticket.constants.TicketStatus;
import com.ticket.entity.Ticket;

import java.util.Map;

public interface StatusService {

    /**
     * 保存工单状态到 Redis（初始状态）
     */
    void saveTicketStatus(Ticket ticket);

    /**
     * 更新工单状态为处理中
     */
    void updateProcessing(String ticketNo);

    /**
     * 更新工单状态为待人工确认（存完整 Agent 返回数据）
     */
    void updateWaitingHuman(String ticketNo, Map<String, Object> agentResult);

    /**
     * 更新工单状态为成功
     */
    void updateSuccess(String ticketNo, String solution);

    /**
     * 更新工单状态为失败
     */
    void updateFailed(String ticketNo, String errorMsg);

    /**
     * 通用更新工单状态（带额外数据）
     */
    void updateStatus(String ticketNo, TicketStatus status, Object extra);

    /**
     * 获取工单状态码
     */
    String getStatus(String ticketNo);

    /**
     * 获取工单状态完整信息（供前端轮询）
     */
    Object getStatusDetail(String ticketNo);

    /**
     * 删除工单缓存
     */
    void deleteStatus(String ticketNo);

    /**
     * 缓存工单详情
     */
    void cacheTicket(Ticket ticket);

    /**
     * 获取缓存的工单详情
     */
    Ticket getCachedTicket(String ticketNo);
}