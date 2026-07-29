package com.dbidding.card.repository;

import java.time.LocalDate;

public interface PriceMovementCandidate {
    Integer getCardId();

    String getName();

    String getRarity();

    String getImageUrl();

    LocalDate getCurrentDate();

    Long getCurrentPrice();

    Integer getBidCount();

    LocalDate getPreviousDate();

    Long getPreviousPrice();
}
