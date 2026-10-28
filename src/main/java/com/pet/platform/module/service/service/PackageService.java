package com.pet.platform.module.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pet.platform.common.AutoFill;
import com.pet.platform.common.BusinessException;
import com.pet.platform.common.PageResult;
import com.pet.platform.module.category.entity.Category;
import com.pet.platform.module.category.mapper.CategoryMapper;
import com.pet.platform.module.service.entity.PackageDetail;
import com.pet.platform.module.service.entity.ServiceItem;
import com.pet.platform.module.service.entity.ServicePackage;
import com.pet.platform.module.service.mapper.PackageDetailMapper;
import com.pet.platform.module.service.mapper.ServiceItemMapper;
import com.pet.platform.module.service.mapper.ServicePackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PackageService {

    private final ServicePackageMapper packageMapper;
    private final PackageDetailMapper detailMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final CategoryMapper categoryMapper;

    public PageResult<ServicePackage> page(int page, int pageSize, String name, Long categoryId, Integer status) {
        Page<ServicePackage> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<ServicePackage> wrapper = Wrappers.lambdaQuery();
        wrapper.like(StringUtils.hasText(name), ServicePackage::getName, name)
                .eq(categoryId != null, ServicePackage::getCategoryId, categoryId)
                .eq(status != null, ServicePackage::getStatus, status)
                .orderByDesc(ServicePackage::getId);
        packageMapper.selectPage(p, wrapper);
        fillDetails(p.getRecords());
        return PageResult.of(p);
    }

    public ServicePackage detail(Long id) {
        ServicePackage pkg = packageMapper.selectById(id);
        if (pkg == null) {
            throw new BusinessException("套餐不存在");
        }
        fillDetails(Collections.singletonList(pkg));
        return pkg;
    }

    @AutoFill(AutoFill.Type.INSERT)
    @Transactional(rollbackFor = Exception.class)
    public void create(ServicePackage pkg) {
        validate(pkg);
        pkg.setId(null);
        if (pkg.getStatus() == null) {
            pkg.setStatus(1);
        }
        if (pkg.getDailyLimit() == null) {
            pkg.setDailyLimit(5);
        }
        pkg.setOriginalPrice(calculateOriginalPrice(pkg));
        packageMapper.insert(pkg);
        saveDetails(pkg.getId(), pkg.getDetails());
    }

    @AutoFill(AutoFill.Type.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    public void update(ServicePackage pkg) {
        if (pkg.getId() == null || packageMapper.selectById(pkg.getId()) == null) {
            throw new BusinessException("套餐不存在");
        }
        validate(pkg);
        pkg.setOriginalPrice(calculateOriginalPrice(pkg));
        packageMapper.updateById(pkg);
        if (pkg.getDetails() != null) {
            saveDetails(pkg.getId(), pkg.getDetails());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (packageMapper.selectById(id) == null) {
            throw new BusinessException("套餐不存在");
        }
        detailMapper.delete(Wrappers.<PackageDetail>lambdaQuery().eq(PackageDetail::getPackageId, id));
        packageMapper.deleteById(id);
    }

    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态不合法");
        }
        ServicePackage pkg = new ServicePackage();
        pkg.setId(id);
        pkg.setStatus(status);
        packageMapper.updateById(pkg);
    }

    public List<ServicePackage> userList(int page, int pageSize, Long categoryId) {
        Page<ServicePackage> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<ServicePackage> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ServicePackage::getStatus, 1)
                .eq(categoryId != null, ServicePackage::getCategoryId, categoryId)
                .orderByDesc(ServicePackage::getId);
        packageMapper.selectPage(p, wrapper);
        fillDetails(p.getRecords());
        return p.getRecords();
    }

    public ServicePackage userDetail(Long id) {
        ServicePackage pkg = packageMapper.selectById(id);
        if (pkg == null || pkg.getStatus() == null || pkg.getStatus() != 1) {
            throw new BusinessException("套餐不存在或已下架");
        }
        fillDetails(Collections.singletonList(pkg));
        return pkg;
    }

    /**
     * 套餐总时长 = 各项目 duration × quantity 之和，供预约计算 endTime。
     */
    public int totalDuration(ServicePackage pkg) {
        if (pkg.getDetails() == null) {
            return 0;
        }
        return pkg.getDetails().stream()
                .mapToInt(d -> (d.getDuration() == null ? 0 : d.getDuration()) * (d.getQuantity() == null ? 1 : d.getQuantity()))
                .sum();
    }

    private void validate(ServicePackage pkg) {
        if (!StringUtils.hasText(pkg.getName())) {
            throw new BusinessException("请输入套餐名称");
        }
        if (pkg.getCategoryId() == null || categoryMapper.selectById(pkg.getCategoryId()) == null) {
            throw new BusinessException("请选择有效分类");
        }
        if (pkg.getSellingPrice() == null || pkg.getSellingPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("请输入正确的套餐售价");
        }
        if (pkg.getDetails() == null || pkg.getDetails().isEmpty()) {
            throw new BusinessException("请至少添加一个服务项目");
        }
    }

    private BigDecimal calculateOriginalPrice(ServicePackage pkg) {
        List<Long> itemIds = pkg.getDetails().stream().map(PackageDetail::getServiceItemId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (itemIds.isEmpty()) {
            throw new BusinessException("请至少添加一个服务项目");
        }
        Map<Long, ServiceItem> itemMap = serviceItemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(ServiceItem::getId, Function.identity()));
        BigDecimal total = BigDecimal.ZERO;
        for (PackageDetail detail : pkg.getDetails()) {
            ServiceItem item = itemMap.get(detail.getServiceItemId());
            if (item == null) {
                throw new BusinessException("套餐中包含不存在的服务项目");
            }
            int quantity = detail.getQuantity() == null ? 1 : detail.getQuantity();
            total = total.add(item.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }
        if (pkg.getOriginalPrice() == null || pkg.getOriginalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return total;
        }
        return pkg.getOriginalPrice();
    }

    private void saveDetails(Long packageId, List<PackageDetail> details) {
        detailMapper.delete(Wrappers.<PackageDetail>lambdaQuery().eq(PackageDetail::getPackageId, packageId));
        if (details == null) {
            return;
        }
        for (PackageDetail detail : details) {
            if (detail.getServiceItemId() == null) {
                continue;
            }
            detail.setId(null);
            detail.setPackageId(packageId);
            if (detail.getQuantity() == null || detail.getQuantity() <= 0) {
                detail.setQuantity(1);
            }
            detailMapper.insert(detail);
        }
    }

    private void fillDetails(List<ServicePackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        List<Long> packageIds = packages.stream().map(ServicePackage::getId).collect(Collectors.toList());
        List<PackageDetail> details = detailMapper.selectList(Wrappers.<PackageDetail>lambdaQuery()
                .in(PackageDetail::getPackageId, packageIds));
        Map<Long, ServiceItem> itemMap = details.isEmpty() ? Collections.emptyMap()
                : serviceItemMapper.selectBatchIds(details.stream().map(PackageDetail::getServiceItemId)
                        .distinct().collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(ServiceItem::getId, Function.identity()));
        Map<Long, List<PackageDetail>> grouped = new HashMap<>();
        for (PackageDetail detail : details) {
            ServiceItem item = itemMap.get(detail.getServiceItemId());
            if (item != null) {
                detail.setServiceName(item.getName());
                detail.setDuration(item.getDuration());
                detail.setPrice(item.getPrice());
            }
            grouped.computeIfAbsent(detail.getPackageId(), k -> new ArrayList<>()).add(detail);
        }
        List<Long> categoryIds = packages.stream().map(ServicePackage::getCategoryId).distinct().collect(Collectors.toList());
        Map<Long, String> categoryNames = categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        for (ServicePackage pkg : packages) {
            pkg.setDetails(grouped.getOrDefault(pkg.getId(), Collections.emptyList()));
            pkg.setCategoryName(categoryNames.get(pkg.getCategoryId()));
            pkg.setPrice(pkg.getSellingPrice());
            pkg.setDuration(pkg.getDetails().stream()
                    .mapToInt(d -> (d.getDuration() == null ? 0 : d.getDuration())
                            * (d.getQuantity() == null ? 1 : d.getQuantity()))
                    .sum());
        }
    }
}
