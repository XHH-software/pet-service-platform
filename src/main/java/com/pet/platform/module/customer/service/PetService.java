package com.pet.platform.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pet.platform.common.AutoFill;
import com.pet.platform.common.BusinessException;
import com.pet.platform.common.PageResult;
import com.pet.platform.common.UserContext;
import com.pet.platform.module.appointment.entity.Appointment;
import com.pet.platform.module.appointment.entity.AppointmentItem;
import com.pet.platform.module.appointment.mapper.AppointmentItemMapper;
import com.pet.platform.module.appointment.mapper.AppointmentMapper;
import com.pet.platform.module.customer.entity.Customer;
import com.pet.platform.module.customer.entity.Pet;
import com.pet.platform.module.customer.mapper.CustomerMapper;
import com.pet.platform.module.customer.mapper.PetMapper;
import com.pet.platform.module.staff.entity.Staff;
import com.pet.platform.module.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetMapper petMapper;
    private final CustomerMapper customerMapper;
    private final AppointmentMapper appointmentMapper;
    private final AppointmentItemMapper appointmentItemMapper;
    private final StaffMapper staffMapper;

    public PageResult<Pet> page(Long customerId, int page, int pageSize) {
        if (customerId == null) {
            return PageResult.empty();
        }
        Page<Pet> p = new Page<>(page, pageSize);
        petMapper.selectPage(p, Wrappers.<Pet>lambdaQuery()
                .eq(Pet::getCustomerId, customerId)
                .orderByAsc(Pet::getId));
        p.getRecords().forEach(this::fillAlias);
        return PageResult.of(p);
    }

    public Pet detail(Long id) {
        Pet pet = petMapper.selectById(id);
        if (pet == null) {
            throw new BusinessException("宠物不存在");
        }
        Customer customer = customerMapper.selectById(pet.getCustomerId());
        pet.setCustomer(customer);
        if (customer != null) {
            pet.setCustomerNickname(customer.getNickname());
            pet.setCustomerPhone(customer.getPhone());
        }
        fillAlias(pet);
        List<Appointment> appointments = appointmentMapper.selectList(Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getPetId, id)
                .orderByDesc(Appointment::getAppointmentDate)
                .orderByDesc(Appointment::getId)
                .last("limit 10"));
        pet.setAppointments(buildAppointmentBriefs(appointments));
        return pet;
    }

    private void fillAlias(Pet pet) {
        pet.setVaccineExpiry(pet.getVaccineExpiryDate());
    }

    public List<Pet> listByCustomer(Long customerId) {
        List<Pet> list = petMapper.selectList(Wrappers.<Pet>lambdaQuery()
                .eq(Pet::getCustomerId, customerId)
                .orderByAsc(Pet::getId));
        list.forEach(this::fillAlias);
        return list;
    }

    @AutoFill(AutoFill.Type.INSERT)
    public void create(Pet pet, Long customerId) {
        validate(pet);
        pet.setId(null);
        pet.setCustomerId(customerId);
        if (pet.getNeutered() == null) {
            pet.setNeutered(false);
        }
        petMapper.insert(pet);
    }

    @AutoFill(AutoFill.Type.UPDATE)
    public void update(Pet pet, Long customerId) {
        Pet exist = petMapper.selectById(pet.getId());
        if (exist == null || !exist.getCustomerId().equals(customerId)) {
            throw new BusinessException("宠物不存在");
        }
        validate(pet);
        pet.setCustomerId(customerId);
        petMapper.updateById(pet);
    }

    public void delete(Long id, Long customerId) {
        Pet exist = petMapper.selectById(id);
        if (exist == null || !exist.getCustomerId().equals(customerId)) {
            throw new BusinessException("宠物不存在");
        }
        long active = appointmentMapper.selectCount(Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getPetId, id)
                .in(Appointment::getStatus, 1, 2, 3));
        if (active > 0) {
            throw new BusinessException("该宠物还有未完成的预约，暂时不能删除");
        }
        petMapper.deleteById(id);
    }

    public void checkOwner(Long petId, Long customerId) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !pet.getCustomerId().equals(customerId)) {
            throw new BusinessException("宠物不存在");
        }
    }

    private void validate(Pet pet) {
        if (!StringUtils.hasText(pet.getName())) {
            throw new BusinessException("请输入宠物名字");
        }
        if (pet.getSpecies() == null) {
            throw new BusinessException("请选择宠物类型");
        }
    }

    private List<Map<String, Object>> buildAppointmentBriefs(List<Appointment> appointments) {
        if (appointments.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = appointments.stream().map(Appointment::getId).collect(Collectors.toList());
        Map<Long, List<AppointmentItem>> itemMap = appointmentItemMapper.selectList(Wrappers.<AppointmentItem>lambdaQuery()
                        .in(AppointmentItem::getAppointmentId, ids)).stream()
                .collect(Collectors.groupingBy(AppointmentItem::getAppointmentId));
        Map<Long, String> staffNames = staffMapper.selectBatchIds(appointments.stream()
                        .map(Appointment::getStaffId).distinct().collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(Staff::getId, Staff::getName));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Appointment appointment : appointments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", appointment.getId());
            item.put("orderNo", appointment.getOrderNo());
            item.put("appointmentDate", appointment.getAppointmentDate());
            item.put("startTime", appointment.getStartTime());
            item.put("endTime", appointment.getEndTime());
            item.put("itemSummary", summary(itemMap.get(appointment.getId())));
            item.put("staffName", staffNames.get(appointment.getStaffId()));
            item.put("status", appointment.getStatus());
            item.put("totalAmount", appointment.getTotalAmount());
            item.put("amount", appointment.getTotalAmount());
            result.add(item);
        }
        return result;
    }

    private String summary(List<AppointmentItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream().map(i -> i.getServiceName() + " ×" + i.getQuantity())
                .collect(Collectors.joining("、"));
    }
}
