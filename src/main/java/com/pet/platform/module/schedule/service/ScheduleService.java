package com.pet.platform.module.schedule.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pet.platform.common.BusinessException;
import com.pet.platform.module.appointment.entity.AppointmentSlot;
import com.pet.platform.module.appointment.mapper.AppointmentSlotMapper;
import com.pet.platform.module.schedule.entity.Schedule;
import com.pet.platform.module.schedule.mapper.ScheduleMapper;
import com.pet.platform.module.staff.entity.Staff;
import com.pet.platform.module.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    /** 单次批量排班的跨度上限，防止误传年份把库写爆 */
    private static final int MAX_BATCH_DAYS = 90;

    /** 一个最小可约时段（与预约时间片粒度一致） */
    private static final int SLOT_MINUTES = 30;

    private final ScheduleMapper scheduleMapper;
    private final StaffMapper staffMapper;
    private final AppointmentSlotMapper slotMapper;

    public List<Map<String, Object>> week(LocalDate startDate) {
        LocalDate monday = mondayOf(startDate);
        List<Staff> staffList = staffMapper.selectList(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getStatus, 1)
                .orderByAsc(Staff::getPosition)
                .orderByDesc(Staff::getLevel)
                .orderByAsc(Staff::getId));
        List<Schedule> schedules = scheduleMapper.selectList(Wrappers.<Schedule>lambdaQuery()
                .between(Schedule::getScheduleDate, monday, monday.plusDays(6)));
        Map<String, Schedule> scheduleMap = schedules.stream()
                .collect(Collectors.toMap(s -> s.getStaffId() + "_" + s.getScheduleDate(), s -> s, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Staff staff : staffList) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("staffId", staff.getId());
            row.put("staffName", staff.getName());
            row.put("level", staff.getLevel());
            row.put("avatar", staff.getAvatar());
            List<Map<String, Object>> days = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate date = monday.plusDays(i);
                Schedule schedule = scheduleMap.get(staff.getId() + "_" + date);
                Map<String, Object> day = new LinkedHashMap<>();
                day.put("date", date);
                if (schedule == null) {
                    day.put("scheduleId", null);
                    day.put("startTime", null);
                    day.put("endTime", null);
                    day.put("status", 2);
                    day.put("bookedCount", 0);
                } else {
                    day.put("scheduleId", schedule.getId());
                    day.put("startTime", schedule.getStartTime());
                    day.put("endTime", schedule.getEndTime());
                    day.put("status", schedule.getStatus());
                    day.put("bookedCount", schedule.getBookedCount());
                }
                days.add(day);
            }
            row.put("days", days);
            result.add(row);
        }
        return result;
    }

    /**
     * 批量排班：已存在则覆盖，已约数按真实占用时间片重新统计。
     */
    @Transactional(rollbackFor = Exception.class)
    public void batch(List<Long> staffIds, LocalDate startDate, LocalDate endDate,
                      LocalTime startTime, LocalTime endTime, Integer status) {
        if (staffIds == null || staffIds.isEmpty()) {
            throw new BusinessException("请选择员工");
        }
        if (startDate == null || endDate == null) {
            throw new BusinessException("请选择正确的日期范围");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("请选择正确的日期范围");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_BATCH_DAYS) {
            throw new BusinessException("单次批量排班不能超过 " + MAX_BATCH_DAYS + " 天");
        }
        // 原来只有 save() 校验时间，批量入口没校验：start >= end 或时间为空会写进库，
        // 结果是「这天有排班但一个时段都约不了」，下单时只报一句「超出技师排班范围」。
        validateTimeRange(startTime, endTime);
        if (status == null) {
            status = 1;
        }
        for (Long staffId : staffIds) {
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                upsert(staffId, date, startTime, endTime, status);
            }
        }
    }

    /**
     * 单日排班保存（管理端点击表格空格新增排班）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(Long staffId, LocalDate date, LocalTime startTime, LocalTime endTime, Integer status) {
        if (staffId == null) {
            throw new BusinessException("请选择员工");
        }
        if (date == null) {
            throw new BusinessException("请选择排班日期");
        }
        validateTimeRange(startTime, endTime);
        upsert(staffId, date, startTime, endTime, status == null ? 1 : status);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Schedule input) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BusinessException("排班不存在");
        }
        LocalTime startTime = input.getStartTime() == null ? schedule.getStartTime() : input.getStartTime();
        LocalTime endTime = input.getEndTime() == null ? schedule.getEndTime() : input.getEndTime();
        validateTimeRange(startTime, endTime);
        ensureCoversBookedSlots(schedule.getStaffId(), schedule.getScheduleDate(), startTime, endTime,
                "该日已有预约落在新排班时段之外，请先取消相关预约");
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        if (input.getStatus() != null) {
            schedule.setStatus(input.getStatus());
        }
        schedule.setVersion(schedule.getVersion() + 1);
        scheduleMapper.updateById(schedule);
    }

    /**
     * 删除排班。
     *
     * <p>直接 deleteById 会留下孤儿时间片：预约还在，排班行没了，技师当天从日历上消失，
     * availableSlots 也查不到（它按排班行遍历），客户到店才发现「系统里没有这单」。
     * 所以有占用时拒绝，并给出两个可操作的方向。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BusinessException("排班不存在");
        }
        List<AppointmentSlot> occupied = occupiedSlots(schedule.getStaffId(), schedule.getScheduleDate());
        if (!occupied.isEmpty()) {
            long orders = occupied.stream().map(AppointmentSlot::getAppointmentId).distinct().count();
            throw new BusinessException("该日已有 " + occupied.size() + " 个时段被预约（涉及 " + orders
                    + " 单），请先取消相关预约；若只是临时停业，请把排班状态改为休息");
        }
        scheduleMapper.deleteById(id);
    }

    /**
     * 把上一周同一员工、同星期的排班复制到目标周。
     */
    @Transactional(rollbackFor = Exception.class)
    public void copyPreviousWeek(LocalDate targetStartDate) {
        LocalDate targetMonday = mondayOf(targetStartDate);
        LocalDate previousMonday = targetMonday.minusWeeks(1);
        List<Schedule> previous = scheduleMapper.selectList(Wrappers.<Schedule>lambdaQuery()
                .between(Schedule::getScheduleDate, previousMonday, previousMonday.plusDays(6)));
        int skipped = 0;
        for (Schedule source : previous) {
            if (!isValidRange(source.getStartTime(), source.getEndTime())) {
                // 历史脏数据不该阻断整周复制，跳过并留痕
                skipped++;
                continue;
            }
            upsert(source.getStaffId(), source.getScheduleDate().plusWeeks(1),
                    source.getStartTime(), source.getEndTime(), source.getStatus());
        }
        if (skipped > 0) {
            log.warn("复制上周排班：跳过 {} 条时间非法的源排班", skipped);
        }
    }

    private void upsert(Long staffId, LocalDate date, LocalTime startTime, LocalTime endTime, Integer status) {
        Schedule exist = scheduleMapper.selectOne(Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getStaffId, staffId)
                .eq(Schedule::getScheduleDate, date)
                .last("limit 1"));
        long booked = slotMapper.selectCount(Wrappers.<AppointmentSlot>lambdaQuery()
                .eq(AppointmentSlot::getStaffId, staffId)
                .eq(AppointmentSlot::getAppointmentDate, date));
        if (exist == null) {
            Schedule schedule = new Schedule();
            schedule.setStaffId(staffId);
            schedule.setScheduleDate(date);
            schedule.setStartTime(startTime);
            schedule.setEndTime(endTime);
            schedule.setStatus(status);
            schedule.setBookedCount((int) booked);
            schedule.setVersion(0);
            scheduleMapper.insert(schedule);
        } else {
            boolean timeChanged = !Objects.equals(exist.getStartTime(), startTime)
                    || !Objects.equals(exist.getEndTime(), endTime);
            if (timeChanged) {
                ensureCoversBookedSlots(staffId, date, startTime, endTime,
                        "该日已有预约落在新排班时段之外，请先取消相关预约");
            }
            exist.setStartTime(startTime);
            exist.setEndTime(endTime);
            exist.setStatus(status);
            exist.setBookedCount((int) booked);
            exist.setVersion(exist.getVersion() + 1);
            scheduleMapper.updateById(exist);
        }
    }

    // ==================== 校验 ====================

    private boolean isValidRange(LocalTime startTime, LocalTime endTime) {
        return startTime != null && endTime != null
                && startTime.isBefore(endTime)
                && Duration.between(startTime, endTime).toMinutes() >= SLOT_MINUTES;
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BusinessException("请选择排班开始与结束时间");
        }
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException("排班开始时间必须早于结束时间");
        }
        if (Duration.between(startTime, endTime).toMinutes() < SLOT_MINUTES) {
            throw new BusinessException("排班时长至少 " + SLOT_MINUTES + " 分钟，否则当天没有一个时段可约");
        }
    }

    private List<AppointmentSlot> occupiedSlots(Long staffId, LocalDate date) {
        return slotMapper.selectList(Wrappers.<AppointmentSlot>lambdaQuery()
                .eq(AppointmentSlot::getStaffId, staffId)
                .eq(AppointmentSlot::getAppointmentDate, date));
    }

    /**
     * 已占用的时间片必须全部落在新的排班区间内，否则那些预约会变成“系统里查不到”的孤儿单。
     */
    private void ensureCoversBookedSlots(Long staffId, LocalDate date,
                                         LocalTime startTime, LocalTime endTime, String message) {
        for (AppointmentSlot slot : occupiedSlots(staffId, date)) {
            LocalTime time = slot.getSlotTime();
            if (time == null || time.isBefore(startTime) || !time.isBefore(endTime)) {
                throw new BusinessException(message + "（" + time + " 已有预约）");
            }
        }
    }

    private LocalDate mondayOf(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        return target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
