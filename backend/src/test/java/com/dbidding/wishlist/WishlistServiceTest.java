package com.dbidding.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock
    private WishlistRepository wishlistRepository;

    private WishlistService wishlistService;

    @BeforeEach
    void setUp() {
        wishlistService = new WishlistService(wishlistRepository);
    }

    @Test
    void 찜을_등록한다() {
        given(wishlistRepository.existsByUserIdAndCardId(1, 10)).willReturn(false);
        given(wishlistRepository.save(any(Wishlist.class))).willAnswer(invocation -> invocation.getArgument(0));

        Wishlist result = wishlistService.add(1, 10);

        assertThat(result.getUserId()).isEqualTo(1);
        assertThat(result.getCardId()).isEqualTo(10);
    }

    @Test
    void 이미_찜한_카드를_등록하면_예외가_발생한다() {
        given(wishlistRepository.existsByUserIdAndCardId(1, 10)).willReturn(true);

        assertThatThrownBy(() -> wishlistService.add(1, 10))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void 찜을_해제한다() {
        wishlistService.remove(1, 10);

        verify(wishlistRepository).deleteByUserIdAndCardId(1, 10);
    }

    @Test
    void 찜_목록을_조회한다() {
        given(wishlistRepository.findByUserId(1)).willReturn(List.of(Wishlist.of(1, 10)));

        List<Wishlist> result = wishlistService.findAll(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCardId()).isEqualTo(10);
    }
}
