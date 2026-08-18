package com.dbidding.wishlist.controller;

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

import com.dbidding.global.security.CurrentUser;
import com.dbidding.card.dto.CardResponses;
import com.dbidding.card.service.CardPriceService;
import com.dbidding.wishlist.domain.Wishlist;
import com.dbidding.wishlist.dto.WishlistCreateRequest;
import com.dbidding.wishlist.dto.WishlistResponse;
import com.dbidding.wishlist.service.WishlistService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/wishlists")
public class WishlistController {

    private final WishlistService wishlistService;
    private final CardPriceService cardPriceService;

    public WishlistController(WishlistService wishlistService, CardPriceService cardPriceService) {
        this.wishlistService = wishlistService;
        this.cardPriceService = cardPriceService;
    }

    @PostMapping
    public ResponseEntity<WishlistResponse> add(
            @CurrentUser Integer userId,
            @Valid @RequestBody WishlistCreateRequest request
    ) {
        Wishlist wishlist = wishlistService.add(userId, request.cardId());
        return ResponseEntity.status(HttpStatus.CREATED).body(WishlistResponse.from(wishlist));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> remove(@CurrentUser Integer userId, @PathVariable Integer cardId) {
        wishlistService.remove(userId, cardId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<WishlistResponse> findAll(@CurrentUser Integer userId) {
        return wishlistService.findAll(userId).stream()
                .map(WishlistResponse::from)
                .toList();
    }

    @GetMapping("/cards")
    public List<CardResponses.CardSummary> findAllCards(@CurrentUser Integer userId) {
        return cardPriceService.getWishlistedCards(userId);
    }
}
