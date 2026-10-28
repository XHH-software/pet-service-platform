package com.pet.platform.module.dashboard.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pet.platform.module.appointment.entity.Appointment;
import com.pet.platform.module.appointment.mapper.AppointmentMapper;
import com.pet.platform.module.appointment.mapper.AppointmentSlotMapper;
import com.pet.platform.module.schedule.entity.Schedule;
import com.pet.platform.module.schedule.mapper.ScheduleMapper;
import com.pet.platform.module.staff.entity.Staff;
import com.pet.platform.module.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AppointmentMapper appointmentMapper;
    private final AppointmentSlotMapper slotMapper;
    private final ScheduleMapper scheduleMapper;
    private final StaffMapper staffMapper;

    public Map<String, Object> summary() {
        LocalDate today = LocalDate.now();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayAppointmentCount", appointmentMapper.countByDate(today));
        result.put("todayPendingCount", appointmentMapper.selectCount(Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getAppointmentDate, today)
                .eq(Appointment::getStatus, 2)));
        result.put("todayTurnover", appointmentMapper.turnoverByDate(today));

        long staffTotal = staffMapper.selectCount(Wrappers.<Staff>lambdaQuery().eq(Staff::getStatus, 1));
        List<Long> onDutyStaffIds = scheduleMapper.selectList(Wrappers.<Schedule>lambdaQuery()
                        .eq(Schedule::getScheduleDate, today)
                        .eq(Schedule::getStatus, 1))
                .stream().map(Schedule::getStaffId).distinct().collect(Collectors.toList());
        long onDuty = onDutyStaffIds.isEmpty() ? 0
                : staffMapper.selectCount(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getStatus, 1)
                .in(Staff::getId, onDutyStaffIds));
        result.put("staffOnDutyCount", onDuty);
        result.put("staffTotalCount", staffTotal);

        Integer capacity = scheduleMapper.capacitySlots(today);
        long used = slotMapper.countByDate(today);
        BigDecimal rate = BigDecimal.ZERO;
        if (capacity != null && capacity > 0) {
            rate = BigDecimal.valueOf(used).divide(BigDecimal.valueOf(capacity), 2, RoundingMode.HALF_UP);
            if (rate.compareTo(BigDecimal.ONE) > 0) {
                rate = BigDecimal.ONE;
            }
        }
        result.put("occupancyRate", rate);
        return result;
    }

    public List<Map<String, Object>> trend() {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("orderCount", appointmentMapper.countByDate(date));
            item.put("turnover", appointmentMapper.turnoverByDate(date));
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> staffRank() {
        LocalDate today = LocalDate.now();
        return appointmentMapper.staffRank(today.minusDays(29), today);
    }
}
