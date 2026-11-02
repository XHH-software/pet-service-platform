package com.pet.platform;

import com.pet.platform.module.appointment.entity.Appointment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Redis 故障降级用例。
 *
 * <p>application-test.yml 把 Redis 指向 127.0.0.1:1，连接必然失败。
 * 这里先证明「Redis 真的不可用」，再证明「下单依然成功」——
 * 两条断言缺一不可，否则测试可能什么都没验证到。
 */
class RedisDowntimeTest extends BaseIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("Redis 不可用时：写超时 ZSet 抛异常，但下单主流程照常成功")
    void bookingStillWorksWhenRedisIsDown() {
        // 前置断言：环境确实是「Redis 挂了」，不是悄悄连上了
        assertThrows(Exception.class,
                () -> redisTemplate.opsForZSet().add("appointment:timeout", "1", 1));

        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("基础洗护", 60, 100);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        appointmentService.create(
                bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0)), customerId, false);

        Appointment saved = appointmentMapper.selectList(null).get(0);
        assertThat(saved).as("Redis 挂了也必须落库，只是少了「准点关单」这层加速").isNotNull();
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getOrderNo()).startsWith("AP");
        assertThat(slotMapper.selectCount(null)).isEqualTo(2L);
    }

    @Test
    @DisplayName("Redis 不可用时：扫表兜底仍能找出待支付超时单")
    void scanFallbackFindsUnpaidAppointmentsWithoutRedis() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("基础洗护", 30, 100);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        appointmentService.create(
                bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0)), customerId, false);

        // 把创建时间推到 40 分钟前，模拟「30 分钟未支付」
        jdbcTemplate.update("UPDATE appointment SET create_time = DATEADD('MINUTE', -40, CURRENT_TIMESTAMP)");

        assertThat(appointmentService.findUnpaidBefore(30))
                .as("ZSet 用不了时，这条查询就是兜底关单的唯一入口")
                .hasSize(1);
    }
}
