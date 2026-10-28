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
import com.pet.platform.module.service.mapper.PackageDetailMapper;
import com.pet.platform.module.service.mapper.ServiceItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceItemService {

    private final ServiceItemMapper serviceItemMapper;
    private final CategoryMapper categoryMapper;
    private final PackageDetailMapper packageDetailMapper;

    public PageResult<ServiceItem> page(int page, int pageSize, String name, Long categoryId, Integer status) {
        Page<ServiceItem> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<ServiceItem> wrapper = Wrappers.lambdaQuery();
        wrapper.like(StringUtils.hasText(name), ServiceItem::getName, name)
                .eq(categoryId != null, ServiceItem::getCategoryId, categoryId)
                .eq(status != null, ServiceItem::getStatus, status)
                .orderByDesc(ServiceItem::getId);
        serviceItemMapper.selectPage(p, wrapper);
        fillCategoryName(p.getRecords());
        return PageResult.of(p);
    }

    public ServiceItem detail(Long id) {
        ServiceItem item = serviceItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("服务项目不存在");
        }
        fillCategoryName(java.util.Collections.singletonList(item));
        return item;
    }

    @AutoFill(AutoFill.Type.INSERT)
    public void create(ServiceItem item) {
        validate(item);
        item.setId(null);
        if (item.getStatus() == null) {
            item.setStatus(1);
        }
        if (item.getPetSizeLimit() == null) {
            item.setPetSizeLimit(0);
        }
        if (item.getDailyLimit() == null) {
            item.setDailyLimit(10);
        }
        serviceItemMapper.insert(item);
    }

    @AutoFill(AutoFill.Type.UPDATE)
    public void update(ServiceItem item) {
        if (item.getId() == null || serviceItemMapper.selectById(item.getId()) == null) {
            throw new BusinessException("服务项目不存在");
        }
        validate(item);
        serviceItemMapper.updateById(item);
    }

    public void delete(Long id) {
        if (serviceItemMapper.selectById(id) == null) {
            throw new BusinessException("服务项目不存在");
        }
        long used = packageDetailMapper.selectCount(Wrappers.<PackageDetail>lambdaQuery()
                .eq(PackageDetail::getServiceItemId, id));
        if (used > 0) {
            throw new BusinessException("该项目已被套餐引用，无法删除");
        }
        serviceItemMapper.deleteById(id);
    }

    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态不合法");
        }
        ServiceItem item = new ServiceItem();
        item.setId(id);
        item.setStatus(status);
        serviceItemMapper.updateById(item);
    }

    /**
     * 小程序端返回数组（前端按 list.length 判断是否还有下一页）。
     */
    public List<ServiceItem> userList(int page, int pageSize, String name, Long categoryId) {
        Page<ServiceItem> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<ServiceItem> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ServiceItem::getStatus, 1)
                .like(StringUtils.hasText(name), ServiceItem::getName, name)
                .eq(categoryId != null, ServiceItem::getCategoryId, categoryId)
                .orderByDesc(ServiceItem::getId);
        serviceItemMapper.selectPage(p, wrapper);
        fillCategoryName(p.getRecords());
        return p.getRecords();
    }

    public ServiceItem userDetail(Long id) {
        ServiceItem item = serviceItemMapper.selectById(id);
        if (item == null || item.getStatus() == null || item.getStatus() != 1) {
            throw new BusinessException("服务项目不存在或已下架");
        }
        fillCategoryName(java.util.Collections.singletonList(item));
        return item;
    }

    private void validate(ServiceItem item) {
        if (!StringUtils.hasText(item.getName())) {
            throw new BusinessException("请输入服务名称");
        }
        if (item.getCategoryId() == null || categoryMapper.selectById(item.getCategoryId()) == null) {
            throw new BusinessException("请选择有效分类");
        }
        if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("请输入正确的价格");
        }
        if (item.getDuration() == null || item.getDuration() <= 0) {
            throw new BusinessException("请输入正确的服务时长");
        }
    }

    private void fillCategoryName(List<ServiceItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> ids = items.stream().map(ServiceItem::getCategoryId).distinct().collect(Collectors.toList());
        Map<Long, String> names = categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        items.forEach(item -> item.setCategoryName(names.get(item.getCategoryId())));
    }
}
