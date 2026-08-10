package com.dbidding.auction.dto;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.dbidding.auction.domain.AuctionSort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuctionCursorCodec {
    private static final String VERSION = "v4";

    public String encode(AuctionCursor cursor) {
        String raw = "%s|%s|%s|%s|%d".formatted(
                VERSION,
                cursor.sort().name(),
                cursor.value() == null ? "" : cursor.value(),
                cursor.timeValue() == null ? "" : cursor.timeValue(),
                cursor.auctionId()
        );
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public AuctionCursor decode(String encoded, AuctionSort requestedSort) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 5 || !VERSION.equals(parts[0])) {
                throw invalidCursor();
            }
            AuctionSort cursorSort = AuctionSort.valueOf(parts[1]);
            if (cursorSort != requestedSort) {
                throw invalidCursor();
            }
            Long value = parts[2].isBlank() ? null : Long.valueOf(parts[2]);
            Instant timeValue = parts[3].isBlank() ? null : Instant.parse(parts[3]);
            Integer auctionId = Integer.valueOf(parts[4]);
            validate(cursorSort, value, timeValue, auctionId);
            return new AuctionCursor(cursorSort, value, timeValue, auctionId);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private void validate(
            AuctionSort sort,
            Long value,
            Instant timeValue,
            Integer auctionId
    ) {
        if (auctionId == null || auctionId <= 0) {
            throw invalidCursor();
        }
        if (sort == AuctionSort.LATEST) {
            if (timeValue == null || value != null) {
                throw invalidCursor();
            }
            return;
        }
        if (sort == AuctionSort.BID_COUNT && (value == null || value < 0 || value > Integer.MAX_VALUE)) {
            throw invalidCursor();
        }
        if (value == null || timeValue != null) {
            throw invalidCursor();
        }
    }

    private ResponseStatusException invalidCursor() {
        return new ResponseStatusException(BAD_REQUEST, "유효하지 않은 경매 cursor입니다.");
    }
}
