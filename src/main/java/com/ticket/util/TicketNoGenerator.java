package com.ticket.util;

import com.ticket.constants.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 工单号生成器（Redis 自增，防并发重复）
 * 格式：T + 日期(8位) + 当天序号(6位)
 * 例如：T20260918000001
 */
@Component
public class TicketNoGenerator {

    private final StringRedisTemplate stringRedisTemplate;

    public TicketNoGenerator(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String generate() {
        // 1. 日期前缀
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 2. Redis 自增获取当天序号
        String key = RedisKeys.TICKET_NO_GENERATOR + ":" + date;
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        // 设置过期时间为 7 天，自动清理
        stringRedisTemplate.expireAt(key, LocalDate.now().plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant());

        // 3. 拼接：T + 日期 + 6位序号
        return "T" + date + String.format("%06d", seq);
    }
}
