package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ticket_category")
public class TicketCategory {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    private String groupName;

    private Integer slaHours;

    private Integer autoClose;

    private Integer sort;

    private LocalDateTime createdAt;
}