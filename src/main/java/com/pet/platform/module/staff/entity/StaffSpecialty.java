package com.pet.platform.module.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("staff_specialty")
public class StaffSpecialty {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long staffId;

    private Long serviceItemId;
}
