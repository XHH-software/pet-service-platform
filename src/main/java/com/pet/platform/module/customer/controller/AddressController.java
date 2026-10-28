package com.pet.platform.module.customer.controller;

import com.pet.platform.common.Result;
import com.pet.platform.common.UserContext;
import com.pet.platform.module.customer.entity.CustomerAddress;
import com.pet.platform.module.customer.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/list")
    public Result<List<CustomerAddress>> list() {
        return Result.ok(addressService.list(UserContext.getUserId()));
    }

    @PostMapping
    public Result<Void> create(@RequestBody CustomerAddress address) {
        addressService.create(address, UserContext.getUserId());
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody CustomerAddress address) {
        addressService.update(address, UserContext.getUserId());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(id, UserContext.getUserId());
        return Result.ok();
    }
}
