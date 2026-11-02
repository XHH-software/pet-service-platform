package com.pet.platform;

import com.pet.platform.common.BusinessException;
import com.pet.platform.module.appointment.entity.Appointment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 时间片网格的前提条件。
 *
 * <p>防超卖依赖 {@code (staff_id, appointment_date, slot_time)} 唯一索引，而它只拦得住
 * 「同一格重复插入」，拦不住「区间错位重叠」：已占 10:00 这一格时，从 10:20 开始的一单
 * 会落到 10:20 格，索引放行，实际却和 10:00-10:30 重叠。所以「开始时间落在这套网格上」
 * 是设计成立的前提，接口必须自己校验，不能假设调用方会传对。
 *
 * <p>结束时间同样要向上取整，否则库里出现「显示 10:45 结束、实际占到 11:00」的错位。
 */
class AppointmentTimeGridTest extends BaseIntegrationTest {

    @Test
    @DisplayName("开始时间不在整点/半点上，直接拒绝，且不落任何数据")
    void rejectsStartOffTheHalfHourGrid() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("指甲修剪", 20, 10);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        assertThatThrownBy(() -> appointmentService.create(
                bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 20)), customerId, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("整点或半点");

        assertThat(count("appointment")).isZero();
        assertThat(count("appointment_slot")).isZero();
    }

    @Test
    @DisplayName("开始时间带秒（10:00:30）同样拒绝，避免落在半格的缝里")
    void rejectsStartWithSeconds() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("指甲修剪", 20, 10);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        assertThatThrownBy(() -> appointmentService.create(
                bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0, 30)), customerId, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("整点或半点");

        assertThat(count("appointment")).isZero();
    }

    @Test
    @DisplayName("50 分钟的服务把结束时间向上取整到 11:00，占用 2 片且与真实占用一致")
    void roundsEndTimeUpToHalfHour() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("猫咪精致洗护", 50, 10);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        Map<String, Object> created = appointmentService.create(
                bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0)), customerId, false);

        Appointment saved = appointmentMapper.selectById((Long) created.get("id"));
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(saved.getEndTime())
                .as("10:00 + 50 分钟 = 10:50，向上取整到 11:00")
                .isEqualTo(LocalTime.of(11, 0));
        assertThat(count("appointment_slot")).isEqualTo(2);
    }

    @Test
    @DisplayName("20 分钟的短服务占 1 片，结束时间取整到 10:30")
    void shortServiceOccupiesSingleSlot() {
        Long staffId = seedStaff();
        Long customerId = seedCustomer();
        Long petId = seedPet(customerId);
        Long serviceId = seedService("指甲修剪", 20, 10);
        seedSchedule(staffId, LocalTime.of(9, 0), LocalTime.of(18, 0));

        Map<String, Object> created = appointmentService.create(
                bookingRequest(petId, staffId, serviceId, LocalTime.of(10, 0)), customerId, false);

        Appointment saved = appointmentMapper.selectById((Long) created.get("id"));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(count("appointment_slot"))
                .as("10:00-10:20 落在 10:00 这一格里，只占 1 片")
                .isEqualTo(1);
    }

    private int count(String table) {
        Integer n = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
