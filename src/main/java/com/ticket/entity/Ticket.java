package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketNo;

    @NotBlank(message = "工单标题不能为空")
    @Size(min = 2, max = 100, message = "标题长度 2-100 字")
    private String title;

    @NotBlank(message = "问题描述不能为空")
    @Size(min = 5, max = 1000, message = "描述长度 5-1000 字")
    private String description;

    private Integer categoryId;

    private Integer priority;

    private Integer status;

    private String submitterId;

    private String assigneeId;

    private String aiCategory;

    private BigDecimal aiConfidence;

    private String aiSolution;

    private String finalSolution;

    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    private LocalDateTime closedAt;
}