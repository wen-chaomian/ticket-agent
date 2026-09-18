package com.ticket.service.impl;

import com.ticket.constants.RedisKeys;
import com.ticket.constants.TicketStatus;
import com.ticket.entity.Ticket;
import com.ticket.service.StatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class StatusServiceImpl implements StatusService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void saveTicketStatus(Ticket ticket) {
        String key = RedisKeys.ticketStatusKey(ticket.getTicketNo());
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("ticketNo", ticket.getTicketNo());
        statusMap.put("status", ticket.getStatus());
        statusMap.put("statusDesc", TicketStatus.fromCode(ticket.getStatus()).getDesc());
        // 改成 String，不要直接用 LocalDateTime
        statusMap.put("updatedAt", ticket.getCreatedAt().toString());
        redisTemplate.opsForHash().putAll(key, statusMap);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }

    @Override
    public void updateStatus(String ticketNo, TicketStatus status, Object extra) {
        String key = RedisKeys.ticketStatusKey(ticketNo);
        redisTemplate.opsForHash().put(key, "status", status.getCode());
        redisTemplate.opsForHash().put(key, "statusDesc", status.getDesc());
        if (extra != null) {
            redisTemplate.opsForHash().put(key, "extra", extra);
        }
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }

    @Override
    public String getStatus(String ticketNo) {
        String key = RedisKeys.ticketStatusKey(ticketNo);
        Object status = redisTemplate.opsForHash().get(key, "status");
        return status != null ? status.toString() : null;
    }

    @Override
    public Object getStatusDetail(String ticketNo) {
        String key = RedisKeys.ticketStatusKey(ticketNo);
        return redisTemplate.opsForHash().entries(key);
    }

    @Override
    public void deleteStatus(String ticketNo) {
        String key = RedisKeys.ticketStatusKey(ticketNo);
        redisTemplate.delete(key);
    }

    @Override
    public void cacheTicket(Ticket ticket) {
        String key = RedisKeys.ticketDetailKey(ticket.getTicketNo());
        redisTemplate.opsForValue().set(key, ticket, 7, TimeUnit.DAYS);
    }

    @Override
    public Ticket getCachedTicket(String ticketNo) {
        String key = RedisKeys.ticketDetailKey(ticketNo);
        Object ticket = redisTemplate.opsForValue().get(key);
        return ticket != null ? (Ticket) ticket : null;
    }

    @Override
    public void updateProcessing(String ticketNo) {
        String key = RedisKeys.ticketStatusKey(ticketNo);
        redisTemplate.opsForHash().put(key, "status", TicketStatus.PROCESSING.getCode());
        redisTemplate.opsForHash().put(key, "statusDesc", TicketStatus.PROCESSING.getDesc());
        redisTemplate.opsForHash().put(key, "updatedAt", java.time.LocalDateTime.now().toString());
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }

    @Override
    public void updateWaitingHuman(String ticketNo, Map<String, Object> agentResult) {
        String key = RedisKeys.ticketStatusKey(ticketNo);
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("status", TicketStatus.WAITING_HUMAN.getCode());
        statusMap.put("statusDesc", TicketStatus.WAITING_HUMAN.getDesc());
        statusMap.put("category", agentResult.get("category"));
        statusMap.put("confidence", agentResult.get("confidence"));
        statusMap.put("options", agentResult.get("options"));
        statusMap.put("suggested", agentResult.get("suggested"));
        statusMap.put("summary", agentResult.get("summary"));
        statusMap.put("updatedAt", java.time.LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(key, statusMap);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }

    @Override
    public void updateSuccess(String ticketNo, String solution) {
        String key = RedisKeys.ticketStatusKey(ticketNo);
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("status", TicketStatus.COMPLETED.getCode());
        statusMap.put("statusDesc", TicketStatus.COMPLETED.getDesc());
        statusMap.put("solution", solution);
        statusMap.put("updatedAt", java.time.LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(key, statusMap);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }

    @Override
    public void updateFailed(String ticketNo, String errorMsg) {
        String key = RedisKeys.ticketStatusKey(ticketNo);
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("status", TicketStatus.FAILED.getCode());
        statusMap.put("statusDesc", TicketStatus.FAILED.getDesc());
        statusMap.put("error", errorMsg);
        statusMap.put("updatedAt", java.time.LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(key, statusMap);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }
}