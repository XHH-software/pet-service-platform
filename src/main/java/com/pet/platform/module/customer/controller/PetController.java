package com.pet.platform.module.customer.controller;

import com.pet.platform.common.PageResult;
import com.pet.platform.common.Result;
import com.pet.platform.module.customer.entity.Pet;
import com.pet.platform.module.customer.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/pet")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    @GetMapping("/page")
    public Result<PageResult<Pet>> page(@RequestParam Long customerId,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "50") int pageSize) {
        return Result.ok(petService.page(customerId, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<Pet> detail(@PathVariable Long id) {
        return Result.ok(petService.detail(id));
    }
}
