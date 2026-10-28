package com.pet.platform.module.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pet.platform.common.AutoFill;
import com.pet.platform.common.BusinessException;
import com.pet.platform.common.PageResult;
import com.pet.platform.module.appointment.entity.Appointment;
import com.pet.platform.module.appointment.mapper.AppointmentMapper;
import com.pet.platform.module.customer.entity.Customer;
import com.pet.platform.module.customer.entity.Pet;
import com.pet.platform.module.customer.mapper.CustomerMapper;
import com.pet.platform.module.customer.mapper.PetMapper;
import com.pet.platform.module.review.entity.Review;
import com.pet.platform.module.review.mapper.ReviewMapper;
import com.pet.platform.module.staff.entity.Staff;
import com.pet.platform.module.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final AppointmentMapper appointmentMapper;
    private final CustomerMapper customerMapper;
    private final StaffMapper staffMapper;
    private final PetMapper petMapper;
    private final ObjectMapper objectMapper;

    public PageResult<Review> page(int page, int pageSize, Integer rating, Integer score,
                                   LocalDate startDate, LocalDate endDate, Integer replied) {
        Integer targetScore = rating != null ? rating : score;
        Page<Review> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Review> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(targetScore != null, Review::getScore, targetScore)
                .ge(startDate != null, Review::getCreateTime, startDate == null ? null : startDate.atStartOfDay())
                .lt(endDate != null, Review::getCreateTime, endDate == null ? null : endDate.plusDays(1).atStartOfDay())
                .isNull(replied != null && replied == 0, Review::getReplyContent)
                .isNotNull(replied != null && replied == 1, Review::getReplyContent)
                .orderByDesc(Review::getCreateTime)
                .orderByDesc(Review::getId);
        reviewMapper.selectPage(p, wrapper);
        fill(p.getRecords());
        return PageResult.of(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reply(Long id, String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("请输入回复内容");
        }
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        if (StringUtils.hasText(review.getReplyContent())) {
            throw new BusinessException("该评价已经回复过了");
        }
        review.setReplyContent(content);
        review.setReplyTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());
        reviewMapper.updateById(review);
    }

    @AutoFill(AutoFill.Type.INSERT)
    @Transactional(rollbackFor = Exception.class)
    public void createForUser(Review input, Long customerId) {
        if (input.getAppointmentId() == null) {
            throw new BusinessException("请选择要评价的预约");
        }
        Appointment appointment = appointmentMapper.selectById(input.getAppointmentId());
        if (appointment == null || !appointment.getCustomerId().equals(customerId)) {
            throw new BusinessException("预约不存在");
        }
        if (appointment.getStatus() != 5) {
            throw new BusinessException("服务完成后才能评价");
        }
        if (reviewMapper.selectCount(Wrappers.<Review>lambdaQuery()
                .eq(Review::getAppointmentId, input.getAppointmentId())) > 0) {
            throw new BusinessException("该预约已评价过了");
        }
        if (input.getScore() == null || input.getScore() < 1 || input.getScore() > 5) {
            throw new BusinessException("评分需在 1-5 分之间");
        }
        List<String> images = input.getImages();
        if (images != null && images.size() > 3) {
            throw new BusinessException("最多上传 3 张图片");
        }
        input.setId(null);
        input.setCustomerId(customerId);
        input.setStaffId(appointment.getStaffId());
        input.setImagesJson(toJson(images));
        input.setReplyContent(null);
        input.setReplyTime(null);
        reviewMapper.insert(input);

        appointment.setStatus(6);
        appointment.setUpdateTime(LocalDateTime.now());
        appointmentMapper.updateById(appointment);
    }

    public PageResult<Review> listForUser(Long customerId, int page, int pageSize) {
        Page<Review> p = new Page<>(page, pageSize);
        reviewMapper.selectPage(p, Wrappers.<Review>lambdaQuery()
                .eq(Review::getCustomerId, customerId)
                .orderByDesc(Review::getCreateTime)
                .orderByDesc(Review::getId));
        fill(p.getRecords());
        return PageResult.of(p);
    }

    private void fill(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return;
        }
        List<Long> customerIds = reviews.stream().map(Review::getCustomerId).distinct().collect(Collectors.toList());
        Map<Long, Customer> customerMap = customerMapper.selectBatchIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<Long, Staff> staffMap = staffMapper.selectBatchIds(reviews.stream()
                        .map(Review::getStaffId).distinct().collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(Staff::getId, Function.identity()));
        Map<Long, Appointment> appointmentMap = appointmentMapper.selectBatchIds(reviews.stream()
                        .map(Review::getAppointmentId).distinct().collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(Appointment::getId, Function.identity()));
        Map<Long, Pet> petMap = appointmentMap.isEmpty() ? Collections.emptyMap()
                : petMapper.selectBatchIds(appointmentMap.values().stream()
                        .map(Appointment::getPetId).distinct().collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(Pet::getId, Function.identity()));

        for (Review review : reviews) {
            Customer customer = customerMap.get(review.getCustomerId());
            Staff staff = staffMap.get(review.getStaffId());
            Appointment appointment = appointmentMap.get(review.getAppointmentId());
            review.setCustomerName(customer == null ? "" : customer.getNickname());
            review.setStaffName(staff == null ? "" : staff.getName());
            review.setPetName(appointment == null || petMap.get(appointment.getPetId()) == null
                    ? "" : petMap.get(appointment.getPetId()).getName());
            review.setRating(review.getScore());
            review.setImages(fromJson(review.getImagesJson()));
        }
    }

    private String toJson(List<String> images) {
        try {
            return objectMapper.writeValueAsString(images == null ? Collections.emptyList() : images);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
