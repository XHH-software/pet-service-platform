package com.pet.platform;

import com.pet.platform.common.BusinessException;
import com.pet.platform.module.appointment.entity.Appointment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 每日限额（service_item.daily_limit / service_package.daily_limit）用例。
 *
 * <p>限额不是「先 count 再判断」实现的，而是让数据库唯一索引去抢名额令牌，
 * 所以这里既要验证单线程的边界，也要验证并发下不会多卖。
 */
class AppointmentDailyLimitTest extends BaseIntegrationTest {

    @Test
    @DisplayName("限额 3 的项目，第 4 单被拒，取消一单后可以再约")
    void rejectsTheFourthBookingAndAcceptsAfterCancel() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("限量洗护", 30, 3);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        appointmentService.create(bookingRequest(petId, staffId, serviceId, LocalTime.of(9, 0)), customerId, false);
        appointmentService.create(bookingRequest(petId, staffId, serviceId, LocalTime.of(9, 30)), customerId, false);
        appointmentService.create(bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0)), customerId, false);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                appointmentService.create(
                        bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 30)), customerId, false));
        assertThat(exception.getMessage()).contains("今日名额已满").contains("3");

        // 名额令牌只会占满 3 个 seq
        assertThat(quotaMapper.selectCount(null)).isEqualTo(3L);
        assertThat(appointmentMapper.selectCount(null)).isEqualTo(3L);

        // 取消第一单 → 令牌回收 → 第 4 单可以成交
        Appointment first = appointmentMapper.selectList(null).stream()
                .filter(a -> a.getStartTime().equals(LocalTime.of(9, 0)))
                .findFirst().orElseThrow(AssertionError::new);
        appointmentService.cancelByUser(first.getId(), "改期", customerId);
        assertThat(quotaMapper.selectCount(null)).isEqualTo(2L);

        appointmentService.create(bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 30)), customerId, false);
        assertThat(quotaMapper.selectCount(null)).isEqualTo(3L);
    }

    @Test
    @DisplayName("8 个并发请求抢 3 个名额，恰好成交 3 单，不多不少")
    void concurrentBookingsNeverExceedDailyLimit() throws Exception {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("限量洗护", 30, 3);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            // 每人一个互不冲突的时段，冲突只可能发生在「名额」这一层
            LocalTime start = LocalTime.of(9, 0).plusMinutes(30L * i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                fire.await(5, TimeUnit.SECONDS);
                try {
                    appointmentService.create(bookingRequest(petId, staffId, serviceId, start), customerId, false);
                    return "OK";
                } catch (BusinessException e) {
                    return "BIZ:" + e.getMessage();
                } catch (Exception e) {
                    return "ERR:" + e.getClass().getSimpleName() + ":" + e.getMessage();
                }
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        fire.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            results.add(future.get());
        }

        long success = results.stream().filter("OK"::equals).count();
        assertThat(results.stream().filter(r -> r.startsWith("ERR:")).collect(java.util.stream.Collectors.toList()))
                .as("不应出现非业务异常：%s", results).isEmpty();
        assertThat(success).as("名额 3 个，只允许成交 3 单：%s", results).isEqualTo(3);
        assertThat(quotaMapper.selectCount(null)).isEqualTo(3L);
    }

    @Test
    @DisplayName("不限量（daily_limit=0）的项目不受名额限制")
    void unlimitedServiceIsNotCapped() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("不限量洗护", 30, 0);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(12, 0));

        // 3 小时排班 = 6 个 30 分钟片，全部订满
        for (int i = 0; i < 6; i++) {
            appointmentService.create(
                    bookingRequest(petId, staffId, serviceId, LocalTime.of(9, 0).plusMinutes(30L * i)),
                    customerId, false);
        }

        assertThat(appointmentMapper.selectCount(null)).isEqualTo(6L);
        assertThat(quotaMapper.selectCount(null)).as("不限量不写令牌行").isZero();
    }
}
