package com.dbidding.wishlist;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.wishlist.dto.WishlistCreateRequest;
import com.dbidding.wishlist.dto.WishlistResponse;

import jakarta.validation.Valid;

// TODO: 인증 미들웨어(JwtAuthFilter) 도입되면 @PathVariable Integer userId를 @CurrentUser Integer userId로 교체
@RestController
@RequestMapping("/api/users/{userId}/wishlists")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @PostMapping
    public ResponseEntity<WishlistResponse> add(
            @PathVariable Integer userId,
            @Valid @RequestBody WishlistCreateRequest request
    ) {
        Wishlist wishlist = wishlistService.add(userId, request.cardId());
        return ResponseEntity.status(HttpStatus.CREATED).body(WishlistResponse.from(wishlist));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> remove(@PathVariable Integer userId, @PathVariable Integer cardId) {
        wishlistService.remove(userId, cardId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<WishlistResponse> findAll(@PathVariable Integer userId) {
        return wishlistService.findAll(userId).stream()
                .map(WishlistResponse::from)
                .toList();
    }
}
