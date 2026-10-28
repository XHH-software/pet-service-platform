package com.pet.platform.module.service.controller;

import com.pet.platform.common.PageResult;
import com.pet.platform.common.Result;
import com.pet.platform.module.service.entity.ServiceItem;
import com.pet.platform.module.service.service.ServiceItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/service-item")
@RequiredArgsConstructor
public class UserServiceItemController {

    private final ServiceItemService serviceItemService;

    /**
     * 小程序端返回数组，便于上拉加载时用 length 判断是否还有下一页。
     */
    @GetMapping("/list")
    public Result<List<ServiceItem>> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String name,
                                          @RequestParam(required = false) Long categoryId) {
        return Result.ok(serviceItemService.userList(page, pageSize, name, categoryId));
    }

    @GetMapping("/{id}")
    public Result<ServiceItem> detail(@PathVariable Long id) {
        return Result.ok(serviceItemService.userDetail(id));
    }
}
