package com.dbidding.auction.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.dbidding.auction.port.AuctionCardPort;
import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.domain.CardSet;
import com.dbidding.card.repository.CardMetadataRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuctionCardAdapterTest {
    @Mock
    private CardMetadataRepository cardMetadataRepository;

    @Test
    void 카드_메타데이터를_경매_카드_snapshot으로_변환한다() {
        AuctionCardAdapter adapter = new AuctionCardAdapter(cardMetadataRepository);
        CardMetadata card = card(1, "Pikachu", "Scarlet Violet", "JP", "10", "/cards/pikachu.png");
        when(cardMetadataRepository.findById(1)).thenReturn(Optional.of(card));

        AuctionCardPort.CardSnapshot snapshot = adapter.getCardSnapshot(1);

        assertThat(snapshot.itemId()).isEqualTo(1);
        assertThat(snapshot.name()).isEqualTo("Pikachu");
        assertThat(snapshot.setName()).isEqualTo("Scarlet Violet");
        assertThat(snapshot.language()).isEqualTo("JP");
        assertThat(snapshot.psaGrade()).isEqualTo("10");
        assertThat(snapshot.thumbnailUrl()).isEqualTo("/cards/pikachu.png");
    }

    @Test
    void 카드가_없으면_404로_실패한다() {
        AuctionCardAdapter adapter = new AuctionCardAdapter(cardMetadataRepository);
        when(cardMetadataRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.getCardSnapshot(999))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void 여러_카드_메타데이터를_item_id로_조회할_수_있는_snapshot_map으로_변환한다() {
        AuctionCardAdapter adapter = new AuctionCardAdapter(cardMetadataRepository);
        CardMetadata pikachu = card(1, "Pikachu", "Scarlet Violet", "JP", "10", "/cards/pikachu.png");
        CardMetadata charizard = card(2, "Charizard", "Base Set", "EN", "9", "/cards/charizard.png");
        when(cardMetadataRepository.findAllById(List.of(1, 2))).thenReturn(List.of(pikachu, charizard));

        var snapshots = adapter.getCardSnapshots(List.of(1, 2));

        assertThat(snapshots).containsOnlyKeys(1, 2);
        assertThat(snapshots.get(1).name()).isEqualTo("Pikachu");
        assertThat(snapshots.get(2).setName()).isEqualTo("Base Set");
    }

    private CardMetadata card(
            Integer id,
            String name,
            String setName,
            String language,
            String psaGrade,
            String imagePath
    ) {
        CardSet cardSet = new CardSet(setName, setName.toLowerCase().replace(" ", "-"));
        CardMetadata card = new CardMetadata(cardSet, name, language, psaGrade, "rare", imagePath);
        ReflectionTestUtils.setField(card, "id", id);
        return card;
    }
}
