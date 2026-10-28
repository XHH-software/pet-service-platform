package com.pet.platform.module.customer.controller;

import com.pet.platform.common.PageResult;
import com.pet.platform.common.Result;
import com.pet.platform.module.customer.entity.Customer;
import com.pet.platform.module.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/page")
    public Result<PageResult<Customer>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "50") int pageSize,
                                             @RequestParam(required = false) String keyword) {
        return Result.ok(customerService.page(page, pageSize, keyword));
    }
}
