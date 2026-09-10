package com.mallx.mgr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台操作日志。
 */
@Data
@TableName("t_operate_log")
public class OperateLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String module;

    private String content;

    private String operator;

    private String ip;

    private LocalDateTime createdAt;
}
