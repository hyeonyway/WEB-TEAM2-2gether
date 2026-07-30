package com.dbidding.statistics.repository;

import java.time.LocalDate;

public interface PriceMovementCandidate {
    Integer getCardId();

    LocalDate getCurrentDate();

    Long getCurrentPrice();

    Integer getBidCount();

    LocalDate getPreviousDate();

    Long getPreviousPrice();
}
