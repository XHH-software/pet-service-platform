package com.pet.platform.module.review.controller;

import com.pet.platform.common.PageResult;
import com.pet.platform.common.Result;
import com.pet.platform.common.UserContext;
import com.pet.platform.module.review.entity.Review;
import com.pet.platform.module.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/review")
@RequiredArgsConstructor
public class UserReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public Result<Void> create(@RequestBody Review review) {
        reviewService.createForUser(review, UserContext.getUserId());
        return Result.ok();
    }

    @GetMapping("/list")
    public Result<PageResult<Review>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(reviewService.listForUser(UserContext.getUserId(), page, pageSize));
    }
}
