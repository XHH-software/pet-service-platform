package com.pet.platform.module.category.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pet.platform.common.AutoFill;
import com.pet.platform.common.BusinessException;
import com.pet.platform.common.PageResult;
import com.pet.platform.module.category.entity.Category;
import com.pet.platform.module.category.mapper.CategoryMapper;
import com.pet.platform.module.service.entity.ServiceItem;
import com.pet.platform.module.service.entity.ServicePackage;
import com.pet.platform.module.service.mapper.ServiceItemMapper;
import com.pet.platform.module.service.mapper.ServicePackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ServicePackageMapper servicePackageMapper;

    public List<Category> list(Integer type, boolean onlyEnabled) {
        LambdaQueryWrapper<Category> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(type != null, Category::getType, type)
                .eq(onlyEnabled, Category::getStatus, 1)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId);
        return categoryMapper.selectList(wrapper);
    }

    public PageResult<Category> page(int page, int pageSize, Integer type, String name, Integer status) {
        Page<Category> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Category> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(type != null, Category::getType, type)
                .like(StringUtils.hasText(name), Category::getName, name)
                .eq(status != null, Category::getStatus, status)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId);
        categoryMapper.selectPage(p, wrapper);
        return PageResult.of(p);
    }

    @AutoFill(AutoFill.Type.INSERT)
    public void create(Category category) {
        validate(category, null);
        if (category.getSort() == null) {
            category.setSort(0);
        }
        if (category.getStatus() == null) {
            category.setStatus(1);
        }
        categoryMapper.insert(category);
    }

    @AutoFill(AutoFill.Type.UPDATE)
    public void update(Category category) {
        if (category.getId() == null || categoryMapper.selectById(category.getId()) == null) {
            throw new BusinessException("分类不存在");
        }
        validate(category, category.getId());
        category.setType(null);
        categoryMapper.updateById(category);
    }

    public void delete(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        long itemCount = serviceItemMapper.selectCount(Wrappers.<ServiceItem>lambdaQuery()
                .eq(ServiceItem::getCategoryId, id));
        if (itemCount > 0) {
            throw new BusinessException("该分类下存在服务项目，无法删除");
        }
        long packageCount = servicePackageMapper.selectCount(Wrappers.<ServicePackage>lambdaQuery()
                .eq(ServicePackage::getCategoryId, id));
        if (packageCount > 0) {
            throw new BusinessException("该分类下存在服务套餐，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态不合法");
        }
        Category category = new Category();
        category.setId(id);
        category.setStatus(status);
        categoryMapper.updateById(category);
    }

    private void validate(Category category, Long excludeId) {
        if (!StringUtils.hasText(category.getName())) {
            throw new BusinessException("请输入分类名称");
        }
        if (category.getType() == null && excludeId == null) {
            throw new BusinessException("请选择分类类型");
        }
        LambdaQueryWrapper<Category> wrapper = Wrappers.<Category>lambdaQuery()
                .eq(Category::getName, category.getName())
                .eq(excludeId == null, Category::getType, category.getType());
        if (excludeId != null) {
            wrapper.ne(Category::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("分类名称已存在");
        }
    }
}
