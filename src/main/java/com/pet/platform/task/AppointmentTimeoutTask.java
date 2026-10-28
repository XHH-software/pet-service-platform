package com.pet.platform.task;

import com.pet.platform.module.appointment.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 订单超时关闭：
 * 1) Redis ZSet 按到期时间戳拉取，正常情况走这里；
 * 2) 每 5 分钟扫表兜底，防止 ZSet 写入失败导致订单永远不关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentTimeoutTask {

    private final StringRedisTemplate redisTemplate;
    private final AppointmentService appointmentService;

    @Value("${app.order.timeout-minutes:30}")
    private int timeoutMinutes;

    @Scheduled(fixedDelay = 30_000)
    public void closeExpiredFromRedis() {
        long now = System.currentTimeMillis() / 1000;
        Set<String> ids;
        try {
            ids = redisTemplate.opsForZSet().rangeByScore(AppointmentService.TIMEOUT_KEY, 0, now, 0, 100);
        } catch (Exception e) {
            log.warn("读取超时订单 ZSet 失败: {}", e.getMessage());
            return;
        }
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            try {
                appointmentService.expireUnpaid(Long.valueOf(id));
            } catch (Exception e) {
                log.warn("关闭超时预约 {} 失败: {}", id, e.getMessage());
            } finally {
                redisTemplate.opsForZSet().remove(AppointmentService.TIMEOUT_KEY, id);
            }
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void fallbackScan() {
        try {
            appointmentService.findUnpaidBefore(timeoutMinutes).forEach(id -> {
                try {
                    appointmentService.expireUnpaid(id);
                } catch (Exception e) {
                    log.warn("扫表关闭超时预约 {} 失败: {}", id, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("超时订单扫表失败: {}", e.getMessage());
        }
    }
}
