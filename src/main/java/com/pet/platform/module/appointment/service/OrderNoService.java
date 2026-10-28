package com.pet.platform.module.appointment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pet.platform.module.appointment.entity.Appointment;
import com.pet.platform.module.appointment.mapper.AppointmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 订单号：AP + yyyyMMdd + 4 位当日序号。
 */
@Component
@RequiredArgsConstructor
public class OrderNoService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AppointmentMapper appointmentMapper;

    public synchronized String next(LocalDate date) {
        String prefix = "AP" + date.format(DAY);
        Appointment last = appointmentMapper.selectOne(Wrappers.<Appointment>lambdaQuery()
                .likeRight(Appointment::getOrderNo, prefix)
                .orderByDesc(Appointment::getOrderNo)
                .last("limit 1"));
        int sequence = 1;
        if (last != null && last.getOrderNo() != null && last.getOrderNo().length() == prefix.length() + 4) {
            try {
                sequence = Integer.parseInt(last.getOrderNo().substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                sequence = 1;
            }
        }
        return prefix + String.format("%04d", sequence);
    }
}
