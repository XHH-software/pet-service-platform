package com.pet.platform;

import com.pet.platform.common.BusinessException;
import com.pet.platform.module.schedule.entity.Schedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 排班的边界校验。
 *
 * <p>这几条都不是「理论上的洁癖」，断言的正是原来的真实缺口：
 * batch / copy 入口不校验时间，可以写进 start >= end 的排班；
 * delete 直接物理删除，留下孤儿时间片——预约还在、排班没了，技师当天从日历上消失。
 */
class ScheduleValidationTest extends BaseIntegrationTest {

    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime SIX = LocalTime.of(18, 0);

    @Test
    @DisplayName("批量排班：开始晚于结束被拒绝")
    void batchRejectsReversedRange() {
        Long staffId = seedStaff();
        assertThatThrownBy(() -> scheduleService.batch(
                Collections.singletonList(staffId), bookingDate, bookingDate, SIX, NINE, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始时间必须早于结束时间");
        assertThat(scheduleMapper.selectCount(null)).isZero();
    }

    @Test
    @DisplayName("批量排班：时间为空被拒绝")
    void batchRejectsNullTime() {
        Long staffId = seedStaff();
        assertThatThrownBy(() -> scheduleService.batch(
                Collections.singletonList(staffId), bookingDate, bookingDate, null, null, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择排班开始与结束时间");
        assertThat(scheduleMapper.selectCount(null)).isZero();
    }

    @Test
    @DisplayName("批量排班：不足 30 分钟排不出任何可约时段，直接拒绝")
    void batchRejectsTooShortRange() {
        Long staffId = seedStaff();
        assertThatThrownBy(() -> scheduleService.batch(
                Collections.singletonList(staffId), bookingDate, bookingDate,
                LocalTime.of(9, 0), LocalTime.of(9, 15), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少 30 分钟");
    }

    @Test
    @DisplayName("批量排班：跨度超过 90 天被拒绝")
    void batchRejectsTooLongSpan() {
        Long staffId = seedStaff();
        assertThatThrownBy(() -> scheduleService.batch(
                Collections.singletonList(staffId), bookingDate, bookingDate.plusDays(91), NINE, SIX, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过 90 天");
    }

    @Test
    @DisplayName("删除排班：当天已有预约时拒绝，避免产生孤儿时间片")
    void deleteRejectedWhenDayHasBookings() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("基础洗护", 30, 100);
        Long scheduleId = seedSchedule(staffId, NINE, SIX);
        appointmentService.create(bookingRequest(petId, staffId, serviceId, LocalTime.of(15, 0)), customerId, false);

        assertThatThrownBy(() -> scheduleService.delete(scheduleId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("被预约");
        assertThat(scheduleMapper.selectById(scheduleId)).as("排班不能被删掉").isNotNull();
        assertThat(slotMapper.selectCount(null)).isEqualTo(1L);
    }

    @Test
    @DisplayName("删除排班：无占用时可以正常删除")
    void deleteWorksWhenDayIsFree() {
        Long staffId = seedStaff();
        Long scheduleId = seedSchedule(staffId, NINE, SIX);

        scheduleService.delete(scheduleId);
        assertThat(scheduleMapper.selectById(scheduleId)).isNull();
    }

    @Test
    @DisplayName("修改排班：时间区间不能缩到已预约时段之外")
    void updateRejectsShrinkingBelowBookedSlot() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("基础洗护", 30, 100);
        Long scheduleId = seedSchedule(staffId, NINE, SIX);
        appointmentService.create(bookingRequest(petId, staffId, serviceId, LocalTime.of(15, 0)), customerId, false);

        Schedule patch = new Schedule();
        patch.setStartTime(NINE);
        patch.setEndTime(LocalTime.of(12, 0));

        assertThatThrownBy(() -> scheduleService.update(scheduleId, patch))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有预约落在新排班时段之外");

        Schedule saved = scheduleMapper.selectById(scheduleId);
        assertThat(saved.getEndTime()).as("校验失败不应写入").isEqualTo(SIX);
    }

    @Test
    @DisplayName("修改排班：把区间放大是允许的")
    void updateAllowsWideningRange() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("基础洗护", 30, 100);
        Long scheduleId = seedSchedule(staffId, NINE, SIX);
        appointmentService.create(bookingRequest(petId, staffId, serviceId, LocalTime.of(15, 0)), customerId, false);

        Schedule patch = new Schedule();
        patch.setEndTime(LocalTime.of(21, 0));

        assertThatCode(() -> scheduleService.update(scheduleId, patch)).doesNotThrowAnyException();
        assertThat(scheduleMapper.selectById(scheduleId).getEndTime()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    @DisplayName("复制上周排班：跳过历史脏数据，不影响其他记录")
    void copySkipsInvalidSourceRows() {
        Long staffId = seedStaff();
        LocalDate lastWeek = bookingDate.minusWeeks(1);
        seedScheduleAt(staffId, lastWeek, NINE, SIX);
        seedScheduleAt(staffId, lastWeek.plusDays(1), LocalTime.of(18, 0), LocalTime.of(9, 0));

        scheduleService.copyPreviousWeek(bookingDate);

        List<Schedule> copied = scheduleMapper.selectList(null);
        assertThat(copied).as("上周 2 条（1 合法 1 非法）+ 本周复制的 1 条").hasSize(3);
        assertThat(copied.stream().filter(s -> s.getScheduleDate().equals(bookingDate)).count())
                .as("合法排班被复制到本周同一天").isEqualTo(1);
        assertThat(copied.stream().anyMatch(s -> s.getScheduleDate().equals(bookingDate.plusDays(1))))
                .as("非法排班未复制").isFalse();
    }

    private void seedScheduleAt(Long staffId, LocalDate date, LocalTime start, LocalTime end) {
        Schedule schedule = new Schedule();
        schedule.setStaffId(staffId);
        schedule.setScheduleDate(date);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setStatus(1);
        schedule.setBookedCount(0);
        schedule.setVersion(0);
        scheduleMapper.insert(schedule);
    }
}
