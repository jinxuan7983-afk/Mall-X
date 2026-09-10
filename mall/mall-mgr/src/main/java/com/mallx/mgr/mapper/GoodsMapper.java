package com.mallx.mgr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallx.mgr.entity.Goods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {

    /** 条件扣减数据库库存：只有库存充足时才扣减，防止超卖 */
    @Update("UPDATE t_goods SET stock = stock - #{count} WHERE goods_num = #{goodsNum} AND stock >= #{count}")
    int deductStock(@Param("goodsNum") String goodsNum, @Param("count") int count);

    /** 关单回补库存 */
    @Update("UPDATE t_goods SET stock = stock + #{count} WHERE goods_num = #{goodsNum}")
    int restoreStock(@Param("goodsNum") String goodsNum, @Param("count") int count);
}
