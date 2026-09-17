package com.mallx.mgr.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mallx.common.exception.BizException;
import com.mallx.common.result.R;
import com.mallx.mgr.entity.Category;
import com.mallx.mgr.entity.Goods;
import com.mallx.mgr.mapper.CategoryMapper;
import com.mallx.mgr.mapper.GoodsMapper;
import com.mallx.mgr.support.AdminGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 商品类目：平台类目管理 + 前台类目展示。
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMapper categoryMapper;
    private final GoodsMapper goodsMapper;
    private final AdminGuard adminGuard;

    /** 类目列表（前台展示） */
    @GetMapping("/category/list")
    public R list() {
        return R.ok("查询成功").set("records", categoryMapper.selectList(null));
    }

    /** 平台类目维护 */
    @PostMapping("/admin/category/save")
    public R save(@RequestBody Category category) {
        if (category.getId() != null) {
            categoryMapper.updateById(category);
            return R.ok("类目更新成功");
        }
        category.setParentId(category.getParentId() == null ? 0L : category.getParentId());
        category.setSort(category.getSort() == null ? 0 : category.getSort());
        categoryMapper.insert(category);
        return R.ok("类目创建成功").set("categoryId", category.getId());
    }

    /**
     * 平台删除类目（仅 ADMIN 可执行）。
     * 超级管理员不受限制：即使存在子类目或商品引用也可物理删除，是否继续由前端确认框把关。
     */
    @PostMapping("/admin/category/delete")
    public R delete(@RequestBody Map<String, Object> req,
                    @RequestHeader(value = "X-Admin-Token", required = false) String operatorToken) {
        adminGuard.requireAdmin(operatorToken);
        Object idValue = req.get("categoryId");
        if (idValue == null || String.valueOf(idValue).isBlank()) {
            throw new BizException("请指定要删除的类目");
        }
        Long categoryId = Long.parseLong(String.valueOf(idValue));
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException("类目不存在或已被删除");
        }
        categoryMapper.deleteById(categoryId);
        return R.ok("类目「" + category.getCategoryName() + "」已删除");
    }
}
