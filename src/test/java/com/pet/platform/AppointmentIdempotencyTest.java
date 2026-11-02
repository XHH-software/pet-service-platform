package com.pet.platform;

import com.pet.platform.common.BusinessException;
import com.pet.platform.module.appointment.entity.Appointment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 下单幂等：requestId 由客户端生成，同一键重复提交只落一单。
 *
 * <p>要防的是两种真实情况：用户手抖连点两次；下单请求超时后前端自动重试。
 * 两者都会带着同一个 requestId 再打一次接口，如果没做幂等，结果要么多出一单，
 * 要么第二次拿到"该时段已被预约"——客户会以为第一次没成功。
 *
 * <p>幂等检查必须排在时段冲突预检之前，否则重复请求的时段已被首单占掉，
 * 第二次会走冲突分支，测不出重复提交。
 */
class AppointmentIdempotencyTest extends BaseIntegrationTest {

    @Test
    @DisplayName("同一 requestId 重复提交只落一单，第二次返回首次的订单号")
    void sameRequestIdReturnsTheSameOrder() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("基础洗护", 60, 10);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        Appointment first = bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0));
        first.setRequestId("req-20261102-0001");
        Map<String, Object> firstResult = appointmentService.create(first, customerId, false);

        Appointment retry = bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0));
        retry.setRequestId("req-20261102-0001");
        Map<String, Object> retryResult = appointmentService.create(retry, customerId, false);

        assertThat(retryResult.get("id"))
                .as("重复提交要返回首单，而不是新订单或时段冲突")
                .isEqualTo(firstResult.get("id"));
        assertThat(retryResult.get("orderNo")).isEqualTo(firstResult.get("orderNo"));
        assertThat(retryResult.get("duplicated")).isEqualTo(true);
        assertThat(count("appointment")).isEqualTo(1);
        assertThat(count("appointment_slot")).as("时间片也只占一次").isEqualTo(2);
    }

    @Test
    @DisplayName("8 个相同 requestId 的并发请求，只落一单，且都拿到同一个订单")
    void concurrentSameRequestIdYieldsSingleOrder() throws Exception {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("基础洗护", 60, 10);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                fire.await(5, TimeUnit.SECONDS);
                Appointment request = bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0));
                request.setRequestId("req-concurrent-0001");
                try {
                    return appointmentService.create(request, customerId, false).get("id");
                } catch (Exception e) {
                    return "ERR:" + e.getClass().getSimpleName() + ":" + e.getMessage();
                }
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        fire.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get());
        }

        assertThat(count("appointment"))
                .as("同一幂等键并发提交只能落一单，实际：%s", results)
                .isEqualTo(1);
        assertThat(results)
                .as("每个请求都应拿到同一个订单 id，而不是报错：%s", results)
                .allMatch(results.get(0)::equals);
    }

    @Test
    @DisplayName("不传 requestId 时保持原有行为：不同时段可以各下一单")
    void withoutRequestIdRepeatedBookingsStillWork() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("基础洗护", 60, 10);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        appointmentService.create(
                bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0)), customerId, false);
        appointmentService.create(
                bookingRequest(petId, staffId, serviceId, LocalTime.of(14, 0)), customerId, false);

        assertThat(count("appointment")).isEqualTo(2);
    }

    @Test
    @DisplayName("拿别人的 requestId 提交，查不到对方订单，也不会把订单泄露出来")
    void requestIdOfAnotherCustomerIsRejected() {
        Long staffId = seedStaff();
        Long ownerId = seedCustomer();
        Long ownerPetId = seedPet(ownerId);
        Long serviceId = seedService("基础洗护", 60, 10);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        Appointment ownerRequest = bookingRequest(ownerPetId, staffId, serviceId, LocalTime.of(10, 0));
        ownerRequest.setRequestId("req-owned-by-someone-else");
        appointmentService.create(ownerRequest, ownerId, false);

        Long otherId = seedCustomer();
        Long otherPetId = seedPet(otherId);
        Appointment otherRequest = bookingRequest(otherPetId, staffId, serviceId, LocalTime.of(15, 0));
        otherRequest.setRequestId("req-owned-by-someone-else");

        assertThatThrownBy(() -> appointmentService.create(otherRequest, otherId, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请求参数非法");
    }

    private int count(String table) {
        Integer n = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
