package com.curtinhonestly.backend.resource;


import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewResource {
    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<Review> createReview(@RequestBody Review review){
        return ResponseEntity.created(URI.create("/units/" + review.getId())).body(reviewService.createReview(review));
    }

}
