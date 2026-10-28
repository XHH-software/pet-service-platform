package com.pet.platform.module.category.controller;

import com.pet.platform.common.PageResult;
import com.pet.platform.common.Result;
import com.pet.platform.module.category.entity.Category;
import com.pet.platform.module.category.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/list")
    public Result<List<Category>> list(@RequestParam(required = false) Integer type) {
        return Result.ok(categoryService.list(type, false));
    }

    @GetMapping("/page")
    public Result<PageResult<Category>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) Integer type,
                                             @RequestParam(required = false) String name,
                                             @RequestParam(required = false) Integer status) {
        return Result.ok(categoryService.page(page, pageSize, type, name, status));
    }

    @PostMapping
    public Result<Void> create(@RequestBody Category category) {
        categoryService.create(category);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody Category category) {
        categoryService.update(category);
        return Result.ok();
    }

    @DeleteMapping
    public Result<Void> delete(@RequestParam Long id) {
        categoryService.delete(id);
        return Result.ok();
    }

    @PostMapping("/status/{status}")
    public Result<Void> updateStatus(@PathVariable Integer status, @RequestParam Long id) {
        categoryService.updateStatus(id, status);
        return Result.ok();
    }
}
