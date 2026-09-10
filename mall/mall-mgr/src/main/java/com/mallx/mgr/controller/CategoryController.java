package com.mallx.mgr.controller;

import com.mallx.common.result.R;
import com.mallx.mgr.entity.Category;
import com.mallx.mgr.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品类目：平台类目管理 + 前台类目展示。
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMapper categoryMapper;

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
}
