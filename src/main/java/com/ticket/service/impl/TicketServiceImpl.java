package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.constants.MQTopic;
import com.ticket.constants.TicketStatus;
import com.ticket.entity.Ticket;
import com.ticket.entity.TicketLog;
import com.ticket.mapper.TicketLogMapper;
import com.ticket.mapper.TicketMapper;
import com.ticket.service.StatusService;
import com.ticket.service.TicketService;
import com.ticket.util.TicketNoGenerator;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Ticket> implements TicketService {

    // 操作类型常量
    private static final int ACTION_SUBMIT = 1;
    private static final int ACTION_AI_ANALYZE = 2;
    private static final int ACTION_CONFIRM = 3;

    @Autowired
    private StatusService statusService;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private TicketNoGenerator ticketNoGenerator;

    @Autowired
    private TicketLogMapper ticketLogMapper;

    @Override
    public Ticket submit(Ticket ticket) {
        // 生成工单编号（Redis 自增，防并发重复）
        ticket.setTicketNo(ticketNoGenerator.generate());
        // 默认状态：待分配
        ticket.setStatus(TicketStatus.WAITING.getCode());
        ticket.setCreatedAt(LocalDateTime.now());
        save(ticket);
        statusService.saveTicketStatus(ticket);
        rocketMQTemplate.convertAndSend(MQTopic.TICKET_ANALYZE_TOPIC, String.valueOf(ticket.getId()));

        // 记录操作日志
        saveLog(ticket.getId(), ticket.getSubmitterId(), ACTION_SUBMIT, "用户提交工单", null);
        return ticket;
    }

    /**
     * 记录操作日志
     */
    private void saveLog(Long ticketId, String operatorId, int action, String content, String extra) {
        TicketLog log = new TicketLog();
        log.setTicketId(ticketId);
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setContent(content);
        log.setExtra(extra);
        log.setCreatedAt(LocalDateTime.now());
        ticketLogMapper.insert(log);
    }

    @Override
    public Ticket getById(Long id) {
        return baseMapper.selectById(id);
    }

    @Override
    public Ticket getByTicketNo(String ticketNo) {
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ticket::getTicketNo, ticketNo);
        return baseMapper.selectOne(wrapper);
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setStatus(status);
        if (TicketStatus.COMPLETED.getCode().equals(status)) {
            ticket.setResolvedAt(LocalDateTime.now());
        }
        return updateById(ticket);
    }

    @Override
    public void updateAiResult(Long id, Map<String, Object> agentResult) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setAiCategory((String) agentResult.get("category"));

        Object confidenceObj = agentResult.get("confidence");
        if (confidenceObj != null) {
            ticket.setAiConfidence(new BigDecimal(confidenceObj.toString()));
        }

        ticket.setAiSolution(agentResult.get("options") != null ? agentResult.get("options").toString() : null);
        ticket.setStatus(TicketStatus.WAITING_HUMAN.getCode());
        updateById(ticket);

        // 记录 AI 分析日志
        saveLog(id, "system", ACTION_AI_ANALYZE,
                "AI分析完成，分类：" + agentResult.get("category"),
                "置信度：" + agentResult.get("confidence"));
    }

    @Override
    public void updateSolution(Long id, String solution) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setFinalSolution(solution);
        ticket.setStatus(TicketStatus.COMPLETED.getCode());
        ticket.setResolvedAt(LocalDateTime.now());
        updateById(ticket);
    }

    @Override
    @Transactional
    public void confirm(String ticketNo, String decision, String operator) {
        // 1. 查询工单
        Ticket ticket = getByTicketNo(ticketNo);
        if (ticket == null) {
            throw new RuntimeException("工单不存在");
        }

        // 2. 检查状态
        if (ticket.getStatus() != TicketStatus.WAITING_HUMAN.getCode()) {
            throw new RuntimeException("工单当前状态不允许确认");
        }

        // 3. 记录选择的方案
        String selectedTitle = "已选择方案：" + decision;

        // 4. 更新 MySQL
        updateSolution(ticket.getId(), selectedTitle);

        // 5. 更新 Redis
        statusService.updateSuccess(ticketNo, selectedTitle);

        // 6. 记录人工确认日志
        saveLog(ticket.getId(), operator, ACTION_CONFIRM, "人工确认方案：" + decision, null);
    }
}