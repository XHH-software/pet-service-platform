package com.pet.platform.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义公共字段填充注解：标在 Service 的写方法上，由 AOP 统一填充
 * createTime / updateTime / createBy / updateBy。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {

    Type value() default Type.INSERT;

    enum Type {
        INSERT, UPDATE
    }
}
