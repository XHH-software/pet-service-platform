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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleService {

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
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BusinessException("请选择正确的日期范围");
        }
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
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new BusinessException("请选择正确的排班时间");
        }
        upsert(staffId, date, startTime, endTime, status == null ? 1 : status);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Schedule input) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BusinessException("排班不存在");
        }
        schedule.setStartTime(input.getStartTime());
        schedule.setEndTime(input.getEndTime());
        if (input.getStatus() != null) {
            schedule.setStatus(input.getStatus());
        }
        schedule.setVersion(schedule.getVersion() + 1);
        scheduleMapper.updateById(schedule);
    }

    public void delete(Long id) {
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
        for (Schedule source : previous) {
            upsert(source.getStaffId(), source.getScheduleDate().plusWeeks(1),
                    source.getStartTime(), source.getEndTime(), source.getStatus());
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
            exist.setStartTime(startTime);
            exist.setEndTime(endTime);
            exist.setStatus(status);
            exist.setBookedCount((int) booked);
            exist.setVersion(exist.getVersion() + 1);
            scheduleMapper.updateById(exist);
        }
    }

    private LocalDate mondayOf(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        return target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
