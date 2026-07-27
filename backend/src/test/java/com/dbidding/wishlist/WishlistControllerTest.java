package com.dbidding.wishlist;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(WishlistController.class)
class WishlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WishlistService wishlistService;

    @Test
    void 찜을_등록하면_201과_생성된_리소스를_반환한다() throws Exception {
        given(wishlistService.add(1, 10)).willReturn(Wishlist.of(1, 10));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/users/1/wishlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardId\":10}"))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.cardId").value(10));
    }

    @Test
    void cardId가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/users/1/wishlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void 찜을_해제하면_204를_반환한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/users/1/wishlists/10"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        verify(wishlistService).remove(1, 10);
    }

    @Test
    void 찜_목록을_조회하면_200과_목록을_반환한다() throws Exception {
        given(wishlistService.findAll(1)).willReturn(List.of(Wishlist.of(1, 10), Wishlist.of(1, 20)));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/users/1/wishlists"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].cardId").value(20));
    }
}
