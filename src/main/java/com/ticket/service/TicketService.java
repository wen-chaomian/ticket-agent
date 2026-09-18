package com.ticket.service;

import com.ticket.entity.Ticket;

import java.util.Map;

public interface TicketService {

    Ticket submit(Ticket ticket);

    Ticket getById(Long id);

    Ticket getByTicketNo(String ticketNo);

    boolean updateStatus(Long id, Integer status);

    void updateAiResult(Long id, Map<String, Object> agentResult);

    void updateSolution(Long id, String solution);

    void confirm(String ticketNo, String decision, String operator);
}