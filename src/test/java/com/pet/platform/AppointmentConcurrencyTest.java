package com.pet.platform;

import com.pet.platform.common.BusinessException;
import com.pet.platform.module.appointment.entity.Appointment;
import com.pet.platform.module.schedule.entity.Schedule;
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

/**
 * 防超卖的核心用例：N 个线程同时抢同一个技师、同一天、同一个 30 分钟切片。
 *
 * <p>这里不用 mock，直接跑真实事务与真实唯一索引，验证的是「数据库约束兜底」这一层，
 * 而不是「先 selectCount 再 insert」那一层——后者在并发下必然漏。
 */
class AppointmentConcurrencyTest extends BaseIntegrationTest {

    @Test
    @DisplayName("16 个并发请求抢同一时段，只有 1 单成功，且占用片数不多不少")
    void onlyOneBookingWinsForTheSameSlot() throws Exception {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("基础洗护", 60, 100);
        Long scheduleId = seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                fire.await(5, TimeUnit.SECONDS);   // 所有线程就位后同时开抢
                try {
                    appointmentService.create(
                            bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0)), customerId, false);
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
        long bizFail = results.stream().filter(r -> r.startsWith("BIZ:")).count();
        long unexpected = results.stream().filter(r -> r.startsWith("ERR:")).count();

        assertThat(unexpected)
                .as("不应出现业务异常之外的错误：%s", results)
                .isZero();
        assertThat(success)
                .as("同一时段只能成交 1 单，实际 %d 单：%s", success, results)
                .isEqualTo(1);
        assertThat(bizFail).isEqualTo(threads - 1);

        // 60 分钟服务 = 2 个 30 分钟切片；16 个人抢，也只会留下赢家那 2 行
        assertThat(slotMapper.selectCount(null))
                .as("时间片占用只能是赢家的 2 片，多余的行意味着超卖")
                .isEqualTo(2L);

        Schedule schedule = scheduleMapper.selectById(scheduleId);
        assertThat(schedule.getBookedCount()).as("已约数只 +1 次").isEqualTo(1);
    }

    @Test
    @DisplayName("相邻时段互不影响：10:00 被占后 10:30 仍可下单")
    void adjacentSlotStaysAvailableAfterBooking() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("基础洗护", 30, 100);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        appointmentService.create(bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0)), customerId, false);
        appointmentService.create(bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 30)), customerId, false);

        assertThat(slotMapper.selectCount(null)).isEqualTo(2L);
    }

    @Test
    @DisplayName("服务时长跨片冲突：60 分钟服务会让 10:30 这个起点失效")
    void longServiceBlocksEverySlotItCovers() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long longService = seedService("深度护理", 60, 100);
        Long shortService = seedService("修剪指甲", 30, 100);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        appointmentService.create(
                bookingRequest(petId, staffId, longService, LocalTime.of(10, 0)), customerId, false);

        // 10:00-11:00 已被整段占掉，10:30 起点的 30 分钟服务理应被拒
        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, () ->
                appointmentService.create(
                        bookingRequest(petId, staffId, shortService, LocalTime.of(10, 30)), customerId, false));
        assertThat(exception.getMessage()).contains("已被预约");
    }

    @Test
    @DisplayName("取消后时间片与名额都会被回收")
    void cancelReleasesSlots() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("基础洗护", 60, 100);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        appointmentService.create(
                bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0)), customerId, false);
        Appointment saved = appointmentMapper.selectList(null).get(0);
        appointmentService.cancelByUser(saved.getId(), "临时有事", customerId);

        assertThat(slotMapper.selectCount(null)).as("取消后时间片应释放").isZero();

        // 释放后同一时段可以被别人重新订
        appointmentService.create(
                bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0)), customerId, false);
        assertThat(slotMapper.selectCount(null)).isEqualTo(2L);
    }
}
