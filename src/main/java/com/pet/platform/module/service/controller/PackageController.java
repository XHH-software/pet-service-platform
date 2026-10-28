package com.pet.platform.module.service.controller;

import com.pet.platform.common.PageResult;
import com.pet.platform.common.Result;
import com.pet.platform.module.service.entity.ServicePackage;
import com.pet.platform.module.service.service.PackageService;
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

@RestController
@RequestMapping("/admin/package")
@RequiredArgsConstructor
public class PackageController {

    private final PackageService packageService;

    @GetMapping("/page")
    public Result<PageResult<ServicePackage>> page(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int pageSize,
                                                   @RequestParam(required = false) String name,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) Integer status) {
        return Result.ok(packageService.page(page, pageSize, name, categoryId, status));
    }

    @GetMapping("/{id}")
    public Result<ServicePackage> detail(@PathVariable Long id) {
        return Result.ok(packageService.detail(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody ServicePackage pkg) {
        packageService.create(pkg);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody ServicePackage pkg) {
        packageService.update(pkg);
        return Result.ok();
    }

    @DeleteMapping
    public Result<Void> delete(@RequestParam Long id) {
        packageService.delete(id);
        return Result.ok();
    }

    @PostMapping("/status/{status}")
    public Result<Void> updateStatus(@PathVariable Integer status, @RequestParam Long id) {
        packageService.updateStatus(id, status);
        return Result.ok();
    }
}
