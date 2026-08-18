package com.dbidding.wishlist.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.dbidding.global.security.CurrentUserProvider;
import com.dbidding.card.service.CardPriceService;
import com.dbidding.wishlist.domain.Wishlist;
import com.dbidding.wishlist.exception.WishlistException;
import com.dbidding.wishlist.service.WishlistService;

@WebMvcTest(WishlistController.class)
class WishlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WishlistService wishlistService;

    @MockitoBean
    private CardPriceService cardPriceService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUp() {
        given(currentUserProvider.getCurrentUserId()).willReturn(1);
    }

    @Test
    void 찜한_카드_목록은_인증_사용자_기준으로_조회한다() throws Exception {
        given(cardPriceService.getWishlistedCards(1)).willReturn(List.of());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/wishlists/cards"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("[]"));

        verify(cardPriceService).getWishlistedCards(1);
    }

    @Test
    void 찜을_등록하면_201과_생성된_리소스를_반환한다() throws Exception {
        given(wishlistService.add(1, 10)).willReturn(Wishlist.of(1, 10));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/wishlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardId\":10}"))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.card_id").value(10));
    }

    @Test
    void 중복_찜은_공통_오류_응답으로_반환한다() throws Exception {
        given(wishlistService.add(1, 10)).willThrow(WishlistException.alreadyExists());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/wishlists").contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":10}"))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("WISHLIST_ALREADY_EXISTS"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("이미 찜한 카드입니다."));
    }

    @Test
    void cardId가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/wishlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void 찜을_해제하면_204를_반환한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/wishlists/10"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        verify(wishlistService).remove(1, 10);
    }

    @Test
    void 찜_목록을_조회하면_200과_목록을_반환한다() throws Exception {
        given(wishlistService.findAll(1)).willReturn(List.of(Wishlist.of(1, 10), Wishlist.of(1, 20)));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/wishlists"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].card_id").value(20));
    }
}
