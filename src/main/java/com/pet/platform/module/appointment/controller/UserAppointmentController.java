package com.pet.platform.module.appointment.controller;

import com.pet.platform.common.PageResult;
import com.pet.platform.common.Result;
import com.pet.platform.common.UserContext;
import com.pet.platform.module.appointment.entity.Appointment;
import com.pet.platform.module.appointment.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/user/appointment")
@RequiredArgsConstructor
public class UserAppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Appointment appointment) {
        return Result.ok(appointmentService.create(appointment, UserContext.getUserId(), false));
    }

    @GetMapping("/page")
    public Result<PageResult<Appointment>> page(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) Integer status) {
        return Result.ok(appointmentService.pageForUser(UserContext.getUserId(), page, pageSize, status));
    }

    @GetMapping("/{id}")
    public Result<Appointment> detail(@PathVariable Long id) {
        return Result.ok(appointmentService.detailForUser(id, UserContext.getUserId()));
    }

    @PostMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable Long id) {
        appointmentService.pay(id, UserContext.getUserId());
        return Result.ok();
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestBody(required = false) Map<String, String> body) {
        appointmentService.cancelByUser(id, body == null ? null : body.get("cancelReason"),
                UserContext.getUserId());
        return Result.ok();
    }
}
