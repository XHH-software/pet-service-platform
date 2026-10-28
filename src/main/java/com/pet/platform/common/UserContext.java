package com.pet.platform.common;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录人上下文。拦截器写入，请求完成后在 afterCompletion 中清理，
 * 避免 Tomcat 线程复用时用户串号。
 */
public final class UserContext {

    public static final String ADMIN = "admin";
    public static final String USER = "user";

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long id, String type) {
        HOLDER.set(new LoginUser(id, type));
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getId();
    }

    public static String getType() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getType();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static boolean isLogin() {
        return HOLDER.get() != null;
    }

    @Data
    @AllArgsConstructor
    public static class LoginUser {
        private Long id;
        private String type;
    }
}
