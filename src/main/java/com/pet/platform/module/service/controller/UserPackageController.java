package com.pet.platform.module.service.controller;

import com.pet.platform.common.Result;
import com.pet.platform.module.service.entity.ServicePackage;
import com.pet.platform.module.service.service.PackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/package")
@RequiredArgsConstructor
public class UserPackageController {

    private final PackageService packageService;

    @GetMapping("/list")
    public Result<List<ServicePackage>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) Long categoryId) {
        return Result.ok(packageService.userList(page, pageSize, categoryId));
    }

    @GetMapping("/{id}")
    public Result<ServicePackage> detail(@PathVariable Long id) {
        return Result.ok(packageService.userDetail(id));
    }
}
