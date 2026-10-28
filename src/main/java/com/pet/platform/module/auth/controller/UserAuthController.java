package com.pet.platform.module.auth.controller;

import com.pet.platform.common.Result;
import com.pet.platform.common.UserContext;
import com.pet.platform.module.auth.service.AuthService;
import com.pet.platform.module.customer.entity.Customer;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserAuthController {

    private final AuthService authService;

    /**
     * 微信登录：开发期 app.wx.mock=true，返回固定演示客户。
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody(required = false) LoginRequest request) {
        return Result.ok(authService.userLogin(request == null ? null : request.getCode()));
    }

    @GetMapping("/info")
    public Result<Customer> info() {
        return Result.ok(authService.userInfo(UserContext.getUserId()));
    }

    @PutMapping("/info")
    public Result<Void> update(@RequestBody Customer customer) {
        authService.updateUserInfo(UserContext.getUserId(), customer);
        return Result.ok();
    }

    @Data
    public static class LoginRequest {
        private String code;
    }
}
