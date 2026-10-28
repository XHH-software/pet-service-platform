package com.pet.platform.module.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@TableName("appointment_slot")
public class AppointmentSlot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appointmentId;

    private Long staffId;

    private LocalDate appointmentDate;

    private LocalTime slotTime;
}
