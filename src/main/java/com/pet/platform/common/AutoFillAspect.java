package com.pet.platform.common;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
public class AutoFillAspect {

    @Pointcut("@annotation(autoFill)")
    public void fillPointcut(AutoFill autoFill) {
    }

    @Around(value = "fillPointcut(autoFill)", argNames = "pjp,autoFill")
    public Object around(ProceedingJoinPoint pjp, AutoFill autoFill) throws Throwable {
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof BaseEntity) {
                fill((BaseEntity) arg, autoFill.value());
            }
        }
        return pjp.proceed();
    }

    private void fill(BaseEntity entity, AutoFill.Type type) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserContext.getUserId();
        entity.setUpdateTime(now);
        entity.setUpdateBy(userId);
        if (type == AutoFill.Type.INSERT) {
            entity.setCreateTime(now);
            entity.setCreateBy(userId);
        }
    }
}
