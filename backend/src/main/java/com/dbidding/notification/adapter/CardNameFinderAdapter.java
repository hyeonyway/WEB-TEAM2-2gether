package com.dbidding.notification.adapter;

// TODO: WishlistUserFinderAdapter와 같은 이유로 임시 배치. consumer-owned port(CardNameFinder)는
// notification이 정의한 게 맞지만, 구현체는 card 담당(정세호) 패키지(card.adapter)로 옮겨야 한다 - 팀 협의 후 리팩토링.
import com.dbidding.card.service.CardPriceService;
import com.dbidding.notification.port.CardNameFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CardNameFinderAdapter implements CardNameFinder {
    private final CardPriceService cardPriceService;

    @Override
    public String findNameById(Integer cardId) {
        return cardPriceService.getCard(cardId, 1).name();
    }
}
