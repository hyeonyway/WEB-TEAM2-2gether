package com.dbidding.auction.dto;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.dbidding.auction.domain.AuctionSort;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuctionCursorCodec {
    private static final String VERSION = "v2";

    public String encode(AuctionCursor cursor) {
        String raw = "%s|%s|%s|%s|%d|%s|%s".formatted(
                VERSION,
                cursor.sort().name(),
                cursor.value() == null ? "" : cursor.value(),
                cursor.timeValue() == null ? "" : cursor.timeValue(),
                cursor.auctionId(),
                cursor.auctionCount() == null ? "" : cursor.auctionCount(),
                cursor.versionSum() == null ? "" : cursor.versionSum()
        );
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public AuctionCursor decode(String encoded, AuctionSort requestedSort) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 7 || !VERSION.equals(parts[0])) {
                throw invalidCursor();
            }
            AuctionSort cursorSort = AuctionSort.valueOf(parts[1]);
            if (cursorSort != requestedSort) {
                throw invalidCursor();
            }
            Long value = parts[2].isBlank() ? null : Long.valueOf(parts[2]);
            LocalDateTime timeValue = parts[3].isBlank() ? null : LocalDateTime.parse(parts[3]);
            Integer auctionId = Integer.valueOf(parts[4]);
            Long auctionCount = parts[5].isBlank() ? null : Long.valueOf(parts[5]);
            Long versionSum = parts[6].isBlank() ? null : Long.valueOf(parts[6]);
            validate(cursorSort, value, timeValue, auctionId, auctionCount, versionSum);
            return new AuctionCursor(cursorSort, value, timeValue, auctionId, auctionCount, versionSum);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private void validate(
            AuctionSort sort,
            Long value,
            LocalDateTime timeValue,
            Integer auctionId,
            Long auctionCount,
            Long versionSum
    ) {
        if (auctionId == null || auctionId <= 0) {
            throw invalidCursor();
        }
        if (sort == AuctionSort.LATEST) {
            if (timeValue == null || value != null || auctionCount != null || versionSum != null) {
                throw invalidCursor();
            }
            return;
        }
        if (sort == AuctionSort.BID_COUNT && (value == null || value < 0 || value > Integer.MAX_VALUE)) {
            throw invalidCursor();
        }
        if (value == null || timeValue != null || auctionCount == null || auctionCount < 0
                || versionSum == null || versionSum < 0) {
            throw invalidCursor();
        }
    }

    private ResponseStatusException invalidCursor() {
        return new ResponseStatusException(BAD_REQUEST, "유효하지 않은 경매 cursor입니다.");
    }
}
