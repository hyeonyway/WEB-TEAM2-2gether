package com.dbidding.card.adapter;

import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.statistics.port.StatisticsCardPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatisticsCardAdapter implements StatisticsCardPort {
    private final CardMetadataRepository cardRepository;

    @Override
    public boolean exists(Integer itemId) {
        return itemId != null && cardRepository.existsById(itemId);
    }
}
