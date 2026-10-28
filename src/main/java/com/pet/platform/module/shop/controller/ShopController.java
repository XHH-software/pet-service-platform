package com.pet.platform.module.shop.controller;

import com.pet.platform.common.Result;
import com.pet.platform.module.shop.entity.Shop;
import com.pet.platform.module.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @GetMapping
    public Result<Shop> get() {
        return Result.ok(shopService.get());
    }

    @PutMapping
    public Result<Void> update(@RequestBody Shop shop) {
        shopService.update(shop);
        return Result.ok();
    }
}
