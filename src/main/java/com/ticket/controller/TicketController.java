package com.ticket.controller;

import com.ticket.common.ApiResponse;
import com.ticket.constants.TicketStatus;
import com.ticket.entity.Ticket;
import com.ticket.service.StatusService;
import com.ticket.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final StatusService statusService;

    @PostMapping("/submit")
    public ApiResponse<Ticket> submit(@Valid @RequestBody Ticket ticket) {
        Ticket result = ticketService.submit(ticket);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<Ticket> getById(@PathVariable Long id) {
        Ticket ticket = ticketService.getById(id);
        return ticket != null ? ApiResponse.success(ticket) : ApiResponse.error("工单不存在");
    }

    @GetMapping("/no/{ticketNo}")
    public ApiResponse<Ticket> getByTicketNo(@PathVariable String ticketNo) {
        Ticket ticket = ticketService.getByTicketNo(ticketNo);
        return ticket != null ? ApiResponse.success(ticket) : ApiResponse.error("工单不存在");
    }

    @GetMapping("/status/{ticketNo}")
    public ApiResponse<Object> getStatus(@PathVariable String ticketNo) {
        return ApiResponse.success(statusService.getStatusDetail(ticketNo));
    }

    @PostMapping("/confirm/{ticketNo}")
    public ApiResponse<Map<String, Object>> confirm(
            @PathVariable String ticketNo,
            @RequestBody Map<String, String> request) {

        String decision = request.get("decision");
        String operator = request.getOrDefault("operator", "system");

        ticketService.confirm(ticketNo, decision, operator);

        return ApiResponse.success(Map.of(
                "ticketNo", ticketNo,
                "status", TicketStatus.COMPLETED.getCode(),
                "statusDesc", TicketStatus.COMPLETED.getDesc(),
                "message", "确认成功，工单已完成"
        ));
    }
}