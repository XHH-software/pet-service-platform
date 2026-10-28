package com.pet.platform.module.shop.controller;

import com.pet.platform.common.Result;
import com.pet.platform.module.shop.entity.Shop;
import com.pet.platform.module.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/shop")
@RequiredArgsConstructor
public class UserShopController {

    private final ShopService shopService;

    @GetMapping("/status")
    public Result<Shop> status() {
        return Result.ok(shopService.get());
    }
}
