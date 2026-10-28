package com.pet.platform.module.customer.controller;

import com.pet.platform.common.Result;
import com.pet.platform.common.UserContext;
import com.pet.platform.module.customer.entity.Pet;
import com.pet.platform.module.customer.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/pet")
@RequiredArgsConstructor
public class UserPetController {

    private final PetService petService;

    @GetMapping("/list")
    public Result<List<Pet>> list() {
        return Result.ok(petService.listByCustomer(UserContext.getUserId()));
    }

    @PostMapping
    public Result<Void> create(@RequestBody Pet pet) {
        petService.create(pet, UserContext.getUserId());
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody Pet pet) {
        petService.update(pet, UserContext.getUserId());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        petService.delete(id, UserContext.getUserId());
        return Result.ok();
    }
}
