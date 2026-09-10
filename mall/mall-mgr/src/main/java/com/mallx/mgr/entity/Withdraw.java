package com.mallx.mgr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提现记录。
 */
@Data
@TableName("t_withdraw")
public class Withdraw {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String withdrawNo;

    private String shopNum;

    private BigDecimal money;

    /** 0-审核中 1-成功 2-失败 */
    private Integer state;

    private String remark;

    private LocalDateTime finishTime;

    private LocalDateTime createdAt;
}
