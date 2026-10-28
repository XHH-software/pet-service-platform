package com.pet.platform.module.service.controller;

import com.pet.platform.common.PageResult;
import com.pet.platform.common.Result;
import com.pet.platform.module.service.entity.ServiceItem;
import com.pet.platform.module.service.service.ServiceItemService;
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
@RequestMapping("/admin/service-item")
@RequiredArgsConstructor
public class ServiceItemController {

    private final ServiceItemService serviceItemService;

    @GetMapping("/page")
    public Result<PageResult<ServiceItem>> page(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) String name,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) Integer status) {
        return Result.ok(serviceItemService.page(page, pageSize, name, categoryId, status));
    }

    @GetMapping("/{id}")
    public Result<ServiceItem> detail(@PathVariable Long id) {
        return Result.ok(serviceItemService.detail(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody ServiceItem item) {
        serviceItemService.create(item);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody ServiceItem item) {
        serviceItemService.update(item);
        return Result.ok();
    }

    @DeleteMapping
    public Result<Void> delete(@RequestParam Long id) {
        serviceItemService.delete(id);
        return Result.ok();
    }

    @PostMapping("/status/{status}")
    public Result<Void> updateStatus(@PathVariable Integer status, @RequestParam Long id) {
        serviceItemService.updateStatus(id, status);
        return Result.ok();
    }
}
