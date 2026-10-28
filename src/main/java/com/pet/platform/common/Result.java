package com.pet.platform.common;

import lombok.Data;

/**
 * 统一响应体：code=1 成功，code=0 业务失败，code=401 未登录/过期。
 */
@Data
public class Result<T> {

    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 1;
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(String msg) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.msg = msg;
        return r;
    }

    public static <T> Result<T> unauthorized(String msg) {
        Result<T> r = new Result<>();
        r.code = 401;
        r.msg = msg;
        return r;
    }
}
