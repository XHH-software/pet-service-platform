package com.pet.platform.module.appointment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pet.platform.common.AutoFill;
import com.pet.platform.common.BusinessException;
import com.pet.platform.common.PageResult;
import com.pet.platform.module.appointment.entity.Appointment;
import com.pet.platform.module.appointment.entity.AppointmentItem;
import com.pet.platform.module.appointment.entity.AppointmentSlot;
import com.pet.platform.module.appointment.mapper.AppointmentItemMapper;
import com.pet.platform.module.appointment.mapper.AppointmentMapper;
import com.pet.platform.module.appointment.mapper.AppointmentSlotMapper;
import com.pet.platform.module.customer.entity.Customer;
import com.pet.platform.module.customer.entity.Pet;
import com.pet.platform.module.customer.mapper.CustomerMapper;
import com.pet.platform.module.customer.mapper.PetMapper;
import com.pet.platform.module.review.entity.Review;
import com.pet.platform.module.review.mapper.ReviewMapper;
import com.pet.platform.module.schedule.entity.Schedule;
import com.pet.platform.module.schedule.mapper.ScheduleMapper;
import com.pet.platform.module.service.entity.ServiceItem;
import com.pet.platform.module.service.entity.ServicePackage;
import com.pet.platform.module.service.mapper.ServiceItemMapper;
import com.pet.platform.module.service.mapper.ServicePackageMapper;
import com.pet.platform.module.service.service.PackageService;
import com.pet.platform.module.staff.entity.Staff;
import com.pet.platform.module.staff.entity.StaffSpecialty;
import com.pet.platform.module.staff.mapper.StaffMapper;
import com.pet.platform.module.staff.mapper.StaffSpecialtyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    public static final String TIMEOUT_KEY = "appointment:timeout";
    private static final LocalTime OPEN = LocalTime.of(0, 0);

    private final AppointmentMapper appointmentMapper;
    private final AppointmentItemMapper itemMapper;
    private final AppointmentSlotMapper slotMapper;
    private final StaffMapper staffMapper;
    private final StaffSpecialtyMapper staffSpecialtyMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ServicePackageMapper servicePackageMapper;
    private final PackageService packageService;
    private final ScheduleMapper scheduleMapper;
    private final PetMapper petMapper;
    private final CustomerMapper customerMapper;
    private final ReviewMapper reviewMapper;
    private final OrderNoService orderNoService;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.order.timeout-minutes:30}")
    private int timeoutMinutes;

    // ==================== 查询 ====================

    public PageResult<Appointment> pageForAdmin(int page, int pageSize, LocalDate startDate, LocalDate endDate,
                                                Integer status, Long staffId, String keyword, LocalDate appointmentDate) {
        Page<Appointment> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Appointment> wrapper = Wrappers.lambdaQuery();
        wrapper.ge(startDate != null, Appointment::getAppointmentDate, startDate)
                .le(endDate != null, Appointment::getAppointmentDate, endDate)
                .eq(appointmentDate != null, Appointment::getAppointmentDate, appointmentDate)
                .eq(status != null, Appointment::getStatus, status)
                .eq(staffId != null, Appointment::getStaffId, staffId);
        if (StringUtils.hasText(keyword)) {
            List<Long> customerIds = customerMapper.selectList(Wrappers.<Customer>lambdaQuery()
                            .like(Customer::getNickname, keyword).or().like(Customer::getPhone, keyword))
                    .stream().map(Customer::getId).collect(Collectors.toList());
            List<Long> petIds = petMapper.selectList(Wrappers.<Pet>lambdaQuery().like(Pet::getName, keyword))
                    .stream().map(Pet::getId).collect(Collectors.toList());
            wrapper.and(w -> {
                w.like(Appointment::getOrderNo, keyword);
                if (!customerIds.isEmpty()) {
                    w.or().in(Appointment::getCustomerId, customerIds);
                }
                if (!petIds.isEmpty()) {
                    w.or().in(Appointment::getPetId, petIds);
                }
            });
        }
        wrapper.orderByDesc(Appointment::getAppointmentDate)
                .orderByDesc(Appointment::getStartTime)
                .orderByDesc(Appointment::getId);
        appointmentMapper.selectPage(p, wrapper);
        fillList(p.getRecords(), false);
        return PageResult.of(p);
    }

    public PageResult<Appointment> pageForUser(Long customerId, int page, int pageSize, Integer status) {
        Page<Appointment> p = new Page<>(page, pageSize);
        appointmentMapper.selectPage(p, Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getCustomerId, customerId)
                .eq(status != null, Appointment::getStatus, status)
                .orderByDesc(Appointment::getCreateTime)
                .orderByDesc(Appointment::getId));
        fillList(p.getRecords(), false);
        return PageResult.of(p);
    }

    public Appointment detailForAdmin(Long id) {
        Appointment appointment = requireAppointment(id);
        fillList(Collections.singletonList(appointment), true);
        appointment.setItems(itemMapper.selectList(Wrappers.<AppointmentItem>lambdaQuery()
                .eq(AppointmentItem::getAppointmentId, id)));
        return appointment;
    }

    public Appointment detailForUser(Long id, Long customerId) {
        Appointment appointment = requireAppointment(id);
        if (!appointment.getCustomerId().equals(customerId)) {
            throw new BusinessException("预约不存在");
        }
        fillList(Collections.singletonList(appointment), true);
        appointment.setItems(itemMapper.selectList(Wrappers.<AppointmentItem>lambdaQuery()
                .eq(AppointmentItem::getAppointmentId, id)));
        return appointment;
    }

    /**
     * 管理端预约日历：只返回当天有排班的员工，slots 中只包含已预约块。
     */
    public List<Map<String, Object>> calendar(LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        List<Schedule> schedules = scheduleMapper.selectList(Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getScheduleDate, target)
                .eq(Schedule::getStatus, 1));
        if (schedules.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> staffIds = schedules.stream().map(Schedule::getStaffId).distinct().collect(Collectors.toList());
        Map<Long, Staff> staffMap = staffMapper.selectBatchIds(staffIds).stream()
                .collect(Collectors.toMap(Staff::getId, Function.identity()));

        List<Appointment> appointments = appointmentMapper.selectList(Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getAppointmentDate, target)
                .ne(Appointment::getStatus, 4)
                .orderByAsc(Appointment::getStartTime));
        Map<Long, List<AppointmentItem>> itemMap = appointments.isEmpty() ? Collections.emptyMap()
                : itemMapper.selectList(Wrappers.<AppointmentItem>lambdaQuery()
                        .in(AppointmentItem::getAppointmentId,
                                appointments.stream().map(Appointment::getId).collect(Collectors.toList())))
                .stream().collect(Collectors.groupingBy(AppointmentItem::getAppointmentId));
        Map<Long, Pet> petMap = loadPetMap(appointments);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long staffId : staffIds) {
            Staff staff = staffMap.get(staffId);
            if (staff == null || staff.getStatus() == null || staff.getStatus() != 1) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("staffId", staff.getId());
            row.put("staffName", staff.getName());
            row.put("level", staff.getLevel());
            row.put("avatar", staff.getAvatar());
            List<Map<String, Object>> slots = new ArrayList<>();
            for (Appointment appointment : appointments) {
                if (!appointment.getStaffId().equals(staffId)) {
                    continue;
                }
                Pet pet = petMap.get(appointment.getPetId());
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("startTime", appointment.getStartTime());
                slot.put("endTime", appointment.getEndTime());
                slot.put("appointmentId", appointment.getId());
                slot.put("petName", pet == null ? "" : pet.getName());
                slot.put("species", pet == null ? 0 : pet.getSpecies());
                slot.put("breed", pet == null ? "" : pet.getBreed());
                slot.put("itemSummary", summary(itemMap.get(appointment.getId())));
                slot.put("status", appointment.getStatus());
                slots.add(slot);
            }
            row.put("slots", slots);
            result.add(row);
        }
        return result;
    }

    /**
     * 可预约时段：30 分钟切片，按服务总时长检查连续空闲。
     */
    public List<Map<String, Object>> availableSlots(LocalDate date, Long serviceItemId, Long packageId) {
        LocalDate target = date == null ? LocalDate.now() : date;
        int duration = 60;
        if (serviceItemId != null) {
            ServiceItem item = serviceItemMapper.selectById(serviceItemId);
            if (item == null || item.getStatus() == null || item.getStatus() != 1) {
                throw new BusinessException("服务项目不存在或已下架");
            }
            duration = item.getDuration();
        } else if (packageId != null) {
            ServicePackage pkg = packageService.detail(packageId);
            duration = packageService.totalDuration(pkg);
        }
        if (duration <= 0) {
            duration = 60;
        }

        List<Schedule> schedules = scheduleMapper.selectList(Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getScheduleDate, target)
                .eq(Schedule::getStatus, 1)
                .isNotNull(Schedule::getStartTime)
                .isNotNull(Schedule::getEndTime)
                .orderByAsc(Schedule::getStaffId));
        if (schedules.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> qualifiedStaff = null;
        if (serviceItemId != null) {
            qualifiedStaff = staffSpecialtyMapper.selectList(Wrappers.<StaffSpecialty>lambdaQuery()
                            .eq(StaffSpecialty::getServiceItemId, serviceItemId))
                    .stream().map(StaffSpecialty::getStaffId).collect(Collectors.toSet());
        }

        List<AppointmentSlot> bookedSlots = slotMapper.selectList(Wrappers.<AppointmentSlot>lambdaQuery()
                .eq(AppointmentSlot::getAppointmentDate, target));
        Map<Long, Set<LocalTime>> bookedMap = bookedSlots.stream().collect(Collectors.groupingBy(
                AppointmentSlot::getStaffId, Collectors.mapping(AppointmentSlot::getSlotTime, Collectors.toSet())));

        boolean today = target.isEqual(LocalDate.now());
        LocalTime now = LocalTime.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Schedule schedule : schedules) {
            Staff staff = staffMapper.selectById(schedule.getStaffId());
            if (staff == null || staff.getStatus() == null || staff.getStatus() != 1) {
                continue;
            }
            if (qualifiedStaff != null && !qualifiedStaff.contains(staff.getId())) {
                continue;
            }
            Set<LocalTime> booked = bookedMap.getOrDefault(staff.getId(), Collections.emptySet());
            List<String> slots = new ArrayList<>();
            LocalTime time = schedule.getStartTime();
            while (!time.plusMinutes(duration).isAfter(schedule.getEndTime())) {
                if (!(today && time.isBefore(now)) && isFree(time, duration, booked)) {
                    slots.add(time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
                }
                time = time.plusMinutes(30);
            }
            if (!slots.isEmpty()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("staffId", staff.getId());
                row.put("staffName", staff.getName());
                row.put("level", staff.getLevel());
                row.put("avatar", staff.getAvatar());
                row.put("slots", slots);
                result.add(row);
            }
        }
        return result;
    }

    // ==================== 下单与状态流转 ====================

    @AutoFill(AutoFill.Type.INSERT)
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Appointment request, Long customerId, boolean admin) {
        if (request.getPetId() == null) {
            throw new BusinessException("请选择宠物");
        }
        if (request.getStaffId() == null) {
            throw new BusinessException("请选择技师");
        }
        Pet pet = petMapper.selectById(request.getPetId());
        if (pet == null) {
            throw new BusinessException("宠物不存在");
        }
        if (admin) {
            customerId = request.getCustomerId() != null ? request.getCustomerId() : pet.getCustomerId();
        }
        if (customerId == null || !pet.getCustomerId().equals(customerId)) {
            throw new BusinessException("宠物不属于当前客户");
        }
        if (pet.getVaccineExpiryDate() == null) {
            throw new BusinessException("请先完善宠物疫苗有效期");
        }
        if (pet.getVaccineExpiryDate().isBefore(LocalDate.now())) {
            throw new BusinessException("宠物疫苗已过期，暂不可预约");
        }
        LocalDate date = request.getAppointmentDate() == null ? LocalDate.now() : request.getAppointmentDate();
        if (date.isBefore(LocalDate.now())) {
            throw new BusinessException("不能预约过去的日期");
        }
        if (request.getStartTime() == null) {
            throw new BusinessException("请选择开始时间");
        }

        int petSize = petSize(pet.getWeight());
        List<AppointmentItem> items = new ArrayList<>();
        int duration;
        BigDecimal amount;
        if (request.getPackageId() != null) {
            ServicePackage pkg = packageService.detail(request.getPackageId());
            if (pkg.getStatus() == null || pkg.getStatus() != 1) {
                throw new BusinessException("套餐已下架");
            }
            duration = packageService.totalDuration(pkg);
            amount = pkg.getSellingPrice();
            AppointmentItem item = new AppointmentItem();
            item.setPackageId(pkg.getId());
            item.setServiceName(pkg.getName());
            item.setPrice(amount);
            item.setDuration(duration);
            item.setQuantity(1);
            item.setSubtotal(amount);
            items.add(item);
        } else {
            List<Long> serviceItemIds = request.getServiceItemIds();
            if (serviceItemIds == null || serviceItemIds.isEmpty()) {
                serviceItemIds = request.getServiceItemId() == null
                        ? Collections.emptyList() : Collections.singletonList(request.getServiceItemId());
            }
            serviceItemIds = serviceItemIds.stream().filter(java.util.Objects::nonNull)
                    .distinct().collect(Collectors.toList());
            if (serviceItemIds.isEmpty()) {
                throw new BusinessException("请选择服务项目");
            }
            List<ServiceItem> serviceItems = serviceItemMapper.selectBatchIds(serviceItemIds);
            if (serviceItems.size() != serviceItemIds.size()) {
                throw new BusinessException("服务项目不存在");
            }
            duration = 0;
            amount = BigDecimal.ZERO;
            for (ServiceItem serviceItem : serviceItems) {
                if (serviceItem.getStatus() == null || serviceItem.getStatus() != 1) {
                    throw new BusinessException("「" + serviceItem.getName() + "」已下架");
                }
                validatePetSize(serviceItem, petSize);
                duration += serviceItem.getDuration();
                amount = amount.add(serviceItem.getPrice());
                AppointmentItem item = new AppointmentItem();
                item.setServiceItemId(serviceItem.getId());
                item.setServiceName(serviceItem.getName());
                item.setPrice(serviceItem.getPrice());
                item.setDuration(serviceItem.getDuration());
                item.setQuantity(1);
                item.setSubtotal(serviceItem.getPrice());
                items.add(item);
            }
        }
        if (duration <= 0) {
            throw new BusinessException("服务时长异常，请重新选择服务");
        }

        Staff staff = staffMapper.selectById(request.getStaffId());
        if (staff == null || staff.getStatus() == null || staff.getStatus() != 1) {
            throw new BusinessException("技师不存在或已停用");
        }
        Schedule schedule = scheduleMapper.selectOne(Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getStaffId, staff.getId())
                .eq(Schedule::getScheduleDate, date)
                .last("limit 1"));
        if (schedule == null || schedule.getStatus() == null || schedule.getStatus() != 1
                || schedule.getStartTime() == null || schedule.getEndTime() == null) {
            throw new BusinessException("该技师当日不可约");
        }
        LocalTime start = request.getStartTime();
        LocalTime end;
        // 管理端代下单允许手工指定结束时间（日历代客预约表单会传 endTime），
        // 小程序端不传，按所选服务的总时长自动计算。
        if (admin && request.getEndTime() != null && request.getEndTime().isAfter(start)) {
            end = ceilToHalfHour(request.getEndTime());
        } else {
            end = start.plusMinutes(duration);
        }
        if (start.isBefore(schedule.getStartTime()) || end.isAfter(schedule.getEndTime())) {
            throw new BusinessException("预约时间超出技师排班范围");
        }
        if (date.isEqual(LocalDate.now()) && start.isBefore(LocalTime.now())) {
            throw new BusinessException("该时段已过，请选择更晚的时间");
        }
        long conflict = slotMapper.selectCount(Wrappers.<AppointmentSlot>lambdaQuery()
                .eq(AppointmentSlot::getStaffId, staff.getId())
                .eq(AppointmentSlot::getAppointmentDate, date)
                .ge(AppointmentSlot::getSlotTime, start)
                .lt(AppointmentSlot::getSlotTime, end));
        if (conflict > 0) {
            throw new BusinessException("该时段已被预约，请选择其他时间");
        }

        Appointment appointment = request;
        appointment.setId(null);
        appointment.setCustomerId(customerId);
        appointment.setStaffId(staff.getId());
        appointment.setAppointmentDate(date);
        appointment.setStartTime(start);
        appointment.setEndTime(end);
        appointment.setTotalAmount(amount);
        appointment.setPaidAmount(BigDecimal.ZERO);
        appointment.setPayStatus(0);
        appointment.setStatus(admin ? 2 : 1);
        appointment.setSource(admin ? 2 : 1);
        appointment.setCancelReason(null);
        appointment.setPayTime(null);
        appointment.setOrderNo(orderNoService.next(date));
        appointmentMapper.insert(appointment);

        for (AppointmentItem item : items) {
            item.setId(null);
            item.setAppointmentId(appointment.getId());
            itemMapper.insert(item);
        }
        try {
            for (LocalTime time = start; time.isBefore(end); time = time.plusMinutes(30)) {
                AppointmentSlot slot = new AppointmentSlot();
                slot.setAppointmentId(appointment.getId());
                slot.setStaffId(staff.getId());
                slot.setAppointmentDate(date);
                slot.setSlotTime(time);
                slotMapper.insert(slot);
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该时段刚刚被预约，请选择其他时间");
        }
        changeBooked(schedule.getId(), true);

        if (!admin) {
            addTimeout(appointment.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", appointment.getId());
        result.put("orderNo", appointment.getOrderNo());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void pay(Long id, Long customerId) {
        Appointment appointment = requireAppointment(id);
        if (!appointment.getCustomerId().equals(customerId)) {
            throw new BusinessException("预约不存在");
        }
        if (appointment.getStatus() != 1) {
            throw new BusinessException("该预约当前状态不可支付");
        }
        appointment.setStatus(2);
        appointment.setPayStatus(1);
        appointment.setPaidAmount(appointment.getTotalAmount());
        appointment.setPayTime(java.time.LocalDateTime.now());
        appointment.setUpdateTime(java.time.LocalDateTime.now());
        appointmentMapper.updateById(appointment);
        removeTimeout(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void checkin(Long id) {
        Appointment appointment = requireAppointment(id);
        if (appointment.getStatus() != 2) {
            throw new BusinessException("仅待服务状态的预约可以核销");
        }
        appointment.setStatus(3);
        appointment.setUpdateTime(java.time.LocalDateTime.now());
        appointmentMapper.updateById(appointment);
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        Appointment appointment = requireAppointment(id);
        if (appointment.getStatus() != 3) {
            throw new BusinessException("仅服务中的预约可以完成");
        }
        appointment.setStatus(5);
        if (appointment.getPayStatus() == null || appointment.getPayStatus() != 1) {
            appointment.setPayStatus(1);
            appointment.setPaidAmount(appointment.getTotalAmount());
            appointment.setPayTime(java.time.LocalDateTime.now());
        }
        appointment.setUpdateTime(java.time.LocalDateTime.now());
        appointmentMapper.updateById(appointment);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelByAdmin(Long id, String reason) {
        cancel(id, reason, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelByUser(Long id, String reason, Long customerId) {
        cancel(id, reason, customerId);
    }

    private void cancel(Long id, String reason, Long customerId) {
        Appointment appointment = requireAppointment(id);
        if (customerId != null && !appointment.getCustomerId().equals(customerId)) {
            throw new BusinessException("预约不存在");
        }
        if (appointment.getStatus() != 1 && appointment.getStatus() != 2) {
            throw new BusinessException("该预约当前状态不可取消");
        }
        appointment.setStatus(4);
        appointment.setCancelReason(StringUtils.hasText(reason) ? reason : "用户取消");
        if (appointment.getPayStatus() != null && appointment.getPayStatus() == 1) {
            appointment.setPayStatus(2);
        }
        appointment.setUpdateTime(java.time.LocalDateTime.now());
        appointmentMapper.updateById(appointment);
        releaseSlots(appointment);
        removeTimeout(id);
    }

    /**
     * 超时未支付：定时任务调用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void expireUnpaid(Long id) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null || appointment.getStatus() == null || appointment.getStatus() != 1) {
            return;
        }
        appointment.setStatus(4);
        appointment.setCancelReason("超时未支付，系统自动取消");
        appointment.setUpdateTime(java.time.LocalDateTime.now());
        appointmentMapper.updateById(appointment);
        releaseSlots(appointment);
        removeTimeout(id);
        log.info("预约 {} 超时未支付，已自动取消", appointment.getOrderNo());
    }

    public List<Long> findUnpaidBefore(int minutes) {
        return appointmentMapper.selectList(Wrappers.<Appointment>lambdaQuery()
                        .eq(Appointment::getStatus, 1)
                        .lt(Appointment::getCreateTime, java.time.LocalDateTime.now().minusMinutes(minutes)))
                .stream().map(Appointment::getId).collect(Collectors.toList());
    }

    // ==================== 内部工具 ====================

    /**
     * Redis ZSet 只用于“加速”超时关闭，Redis 不可用时降级为定时扫表，不能影响下单主流程。
     */
    private void addTimeout(Long appointmentId) {
        try {
            long expireAt = Instant.now().plusSeconds(timeoutMinutes * 60L).getEpochSecond();
            redisTemplate.opsForZSet().add(TIMEOUT_KEY, String.valueOf(appointmentId), expireAt);
        } catch (Exception e) {
            log.warn("写入超时订单 ZSet 失败，将由扫表任务兜底: {}", e.getMessage());
        }
    }

    private void removeTimeout(Long appointmentId) {
        try {
            redisTemplate.opsForZSet().remove(TIMEOUT_KEY, String.valueOf(appointmentId));
        } catch (Exception e) {
            log.warn("移除超时订单 ZSet 失败: {}", e.getMessage());
        }
    }

    private LocalTime ceilToHalfHour(LocalTime time) {
        int minutes = time.getHour() * 60 + time.getMinute();
        int rounded = (minutes + 29) / 30 * 30;
        if (rounded >= 24 * 60) {
            return LocalTime.of(23, 59);
        }
        return LocalTime.of(rounded / 60, rounded % 60);
    }

    private void releaseSlots(Appointment appointment) {
        slotMapper.delete(Wrappers.<AppointmentSlot>lambdaQuery()
                .eq(AppointmentSlot::getAppointmentId, appointment.getId()));
        Schedule schedule = scheduleMapper.selectOne(Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getStaffId, appointment.getStaffId())
                .eq(Schedule::getScheduleDate, appointment.getAppointmentDate())
                .last("limit 1"));
        if (schedule != null) {
            changeBooked(schedule.getId(), false);
        }
    }

    /**
     * 已约数并发更新：version 乐观锁 + 重试，避免计数丢失。
     */
    private void changeBooked(Long scheduleId, boolean increase) {
        for (int i = 0; i < 5; i++) {
            Schedule current = scheduleMapper.selectById(scheduleId);
            if (current == null) {
                return;
            }
            int rows = increase
                    ? scheduleMapper.increaseBooked(current.getId(), current.getVersion())
                    : scheduleMapper.decreaseBooked(current.getId(), current.getVersion());
            if (rows > 0) {
                return;
            }
        }
        log.warn("排班 {} 已约数更新失败（乐观锁重试 5 次）", scheduleId);
    }

    private boolean isFree(LocalTime start, int duration, Set<LocalTime> booked) {
        LocalTime end = start.plusMinutes(duration);
        for (LocalTime time = start; time.isBefore(end); time = time.plusMinutes(30)) {
            if (booked.contains(time)) {
                return false;
            }
        }
        return true;
    }

    private int petSize(BigDecimal weight) {
        if (weight == null) {
            return 0;
        }
        double value = weight.doubleValue();
        if (value <= 10) {
            return 1;
        }
        if (value <= 25) {
            return 2;
        }
        return 3;
    }

    private void validatePetSize(ServiceItem item, int size) {
        if (item.getPetSizeLimit() == null || item.getPetSizeLimit() == 0 || size == 0) {
            return;
        }
        if (size > item.getPetSizeLimit()) {
            throw new BusinessException("「" + item.getName() + "」不适用于该体型的宠物");
        }
    }

    private Appointment requireAppointment(Long id) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            throw new BusinessException("预约不存在");
        }
        return appointment;
    }

    private void fillList(List<Appointment> list, boolean fullPhone) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<Long, Staff> staffMap = mapById(staffMapper.selectBatchIds(list.stream()
                .map(Appointment::getStaffId).distinct().collect(Collectors.toList())), Staff::getId);
        Map<Long, Customer> customerMap = mapById(customerMapper.selectBatchIds(list.stream()
                .map(Appointment::getCustomerId).distinct().collect(Collectors.toList())), Customer::getId);
        List<Pet> pets = petMapper.selectBatchIds(list.stream()
                .map(Appointment::getPetId).distinct().collect(Collectors.toList()));
        Map<Long, Pet> petMap = mapById(pets, Pet::getId);
        List<Long> ids = list.stream().map(Appointment::getId).collect(Collectors.toList());
        Map<Long, List<AppointmentItem>> itemMap = itemMapper.selectList(Wrappers.<AppointmentItem>lambdaQuery()
                        .in(AppointmentItem::getAppointmentId, ids))
                .stream().collect(Collectors.groupingBy(AppointmentItem::getAppointmentId));

        Set<Long> reviewedIds = reviewMapper.selectList(Wrappers.<Review>lambdaQuery()
                        .in(Review::getAppointmentId, ids))
                .stream().map(Review::getAppointmentId).collect(Collectors.toSet());

        List<AppointmentItem> allItems = itemMap.values().stream()
                .flatMap(List::stream).collect(Collectors.toList());
        Set<Long> serviceItemIds = allItems.stream().map(AppointmentItem::getServiceItemId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ServiceItem> serviceItemMap = serviceItemIds.isEmpty() ? Collections.emptyMap()
                : mapById(serviceItemMapper.selectBatchIds(serviceItemIds), ServiceItem::getId);
        Set<Long> packageIds = allItems.stream().map(AppointmentItem::getPackageId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ServicePackage> packageMap = packageIds.isEmpty() ? Collections.emptyMap()
                : mapById(servicePackageMapper.selectBatchIds(packageIds), ServicePackage::getId);

        for (Appointment appointment : list) {
            Staff staff = staffMap.get(appointment.getStaffId());
            Customer customer = customerMap.get(appointment.getCustomerId());
            Pet pet = petMap.get(appointment.getPetId());
            List<AppointmentItem> items = itemMap.getOrDefault(appointment.getId(), Collections.emptyList());

            appointment.setStaffName(staff == null ? "" : staff.getName());
            appointment.setStaffAvatar(staff == null ? "" : staff.getAvatar());
            appointment.setStaffLevel(staff == null ? null : staff.getLevel());
            appointment.setStaffLevelLabel(staff == null ? "" : staffLevelLabel(staff.getLevel()));
            appointment.setCustomerName(customer == null ? "" : customer.getNickname());
            appointment.setCustomerPhone(customer == null ? "" :
                    (fullPhone ? customer.getPhone() : maskPhone(customer.getPhone())));
            appointment.setPetName(pet == null ? "" : pet.getName());
            appointment.setPetSpecies(pet == null ? 0 : pet.getSpecies());
            appointment.setPetSpeciesLabel(pet == null ? "" : speciesLabel(pet.getSpecies()));
            appointment.setPetBreed(pet == null ? "" : pet.getBreed());
            if (pet != null) {
                appointment.setPetWeight(pet.getWeight());
                appointment.setVaccineExpiryDate(pet.getVaccineExpiryDate());
                appointment.setVaccineExpiry(pet.getVaccineExpiryDate());
            }
            appointment.setItemSummary(summary(items));
            appointment.setServiceItemName(serviceNames(items));
            appointment.setServiceName(appointment.getServiceItemName());
            appointment.setTimeSlotName(slotName(appointment));
            appointment.setServiceItemIcon(resolveIcon(items, serviceItemMap, packageMap));
            appointment.setDuration(totalDuration(items));
            appointment.setAmount(appointment.getTotalAmount());
            appointment.setHasReview(reviewedIds.contains(appointment.getId()));
        }
    }

    private Map<Long, Pet> loadPetMap(List<Appointment> appointments) {
        if (appointments.isEmpty()) {
            return Collections.emptyMap();
        }
        return mapById(petMapper.selectBatchIds(appointments.stream()
                .map(Appointment::getPetId).distinct().collect(Collectors.toList())), Pet::getId);
    }

    private <T> Map<Long, T> mapById(List<T> list, Function<T, Long> idGetter) {
        return list.stream().collect(Collectors.toMap(idGetter, Function.identity(), (a, b) -> a));
    }

    private String summary(List<AppointmentItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream().map(i -> i.getServiceName() + " ×" + i.getQuantity())
                .collect(Collectors.joining("、"));
    }

    /**
     * 小程序评价页展示用的时段文案，如 18:00-19:30。
     */
    private String slotName(Appointment appointment) {
        if (appointment.getStartTime() == null || appointment.getEndTime() == null) {
            return "";
        }
        return appointment.getStartTime() + "-" + appointment.getEndTime();
    }

    /**
     * 小程序预约卡片只展示一行服务名称。
     */
    private String serviceNames(List<AppointmentItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(i -> i.getServiceName() + (i.getQuantity() != null && i.getQuantity() > 1
                        ? "×" + i.getQuantity() : ""))
                .collect(Collectors.joining("、"));
    }

    private int totalDuration(List<AppointmentItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        return items.stream().mapToInt(i -> (i.getDuration() == null ? 0 : i.getDuration())
                * (i.getQuantity() == null ? 1 : i.getQuantity())).sum();
    }

    private String resolveIcon(List<AppointmentItem> items,
                               Map<Long, ServiceItem> serviceItemMap,
                               Map<Long, ServicePackage> packageMap) {
        if (items == null) {
            return "";
        }
        for (AppointmentItem item : items) {
            if (item.getServiceItemId() != null) {
                ServiceItem serviceItem = serviceItemMap.get(item.getServiceItemId());
                if (serviceItem != null && serviceItem.getImage() != null) {
                    return serviceItem.getImage();
                }
            }
            if (item.getPackageId() != null) {
                ServicePackage servicePackage = packageMap.get(item.getPackageId());
                if (servicePackage != null && servicePackage.getImage() != null) {
                    return servicePackage.getImage();
                }
            }
        }
        return "";
    }

    private String speciesLabel(Integer species) {
        if (species == null) {
            return "";
        }
        switch (species) {
            case 1:
                return "狗";
            case 2:
                return "猫";
            case 3:
                return "其他";
            default:
                return "";
        }
    }

    private String staffLevelLabel(Integer level) {
        if (level == null) {
            return "";
        }
        switch (level) {
            case 1:
                return "初级";
            case 2:
                return "中级";
            case 3:
                return "高级";
            default:
                return "";
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
