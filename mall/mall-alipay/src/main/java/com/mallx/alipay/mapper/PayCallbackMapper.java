package com.mallx.alipay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallx.alipay.entity.PayCallbackLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayCallbackMapper extends BaseMapper<PayCallbackLog> {
}
