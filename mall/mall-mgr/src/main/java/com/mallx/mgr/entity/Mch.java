package com.mallx.mgr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商户。
 */
@Data
@TableName("t_mch")
public class Mch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String shopNum;

    private String shopName;

    private String shopPhone;

    private String shopAddress;

    private String username;

    private String password;

    /** 0-待审核 1-正常 2-禁用 */
    private Integer shopState;

    private String auditRemark;

    private BigDecimal balance;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
