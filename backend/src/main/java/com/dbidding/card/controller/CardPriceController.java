package com.dbidding.card.controller;

import com.dbidding.card.dto.CardResponses;
import com.dbidding.card.domain.CardSort;
import com.dbidding.card.service.CardPriceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardPriceController {
    private final CardPriceService cardPriceService;

    @GetMapping
    public CardResponses.Page<CardResponses.CardSummary> getCards(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String psaGrade,
            @RequestParam(defaultValue = "REGISTERED") CardSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return cardPriceService.getCards(keyword, psaGrade, sort, page, size);
    }

    @GetMapping("/batch")
    public List<CardResponses.CardSummary> getCardsByIds(
            @RequestParam List<@Min(1) Integer> ids) {
        return cardPriceService.getCardsByIds(ids);
    }

    @GetMapping("/{cardId}")
    public CardResponses.CardDetail getCard(
            @PathVariable @Min(1) Integer cardId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return cardPriceService.getCard(cardId, days);
    }
}
