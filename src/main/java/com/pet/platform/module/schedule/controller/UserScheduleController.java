package com.pet.platform.module.schedule.controller;

import com.pet.platform.common.Result;
import com.pet.platform.module.appointment.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user/schedule")
@RequiredArgsConstructor
public class UserScheduleController {

    private final AppointmentService appointmentService;

    @GetMapping("/available")
    public Result<List<Map<String, Object>>> available(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long serviceItemId,
            @RequestParam(required = false) Long packageId) {
        return Result.ok(appointmentService.availableSlots(date, serviceItemId, packageId));
    }
}
