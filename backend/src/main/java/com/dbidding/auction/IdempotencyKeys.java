package com.dbidding.auction;

import com.dbidding.auction.exception.AuctionException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class IdempotencyKeys {
    private static final int MAX_LENGTH = 64;

    private IdempotencyKeys() {
    }

    public static void validate(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw AuctionException.invalidIdempotencyKey("Idempotency-Key 헤더가 필요합니다.");
        }
        if (idempotencyKey.length() > MAX_LENGTH) {
            throw AuctionException.invalidIdempotencyKey("Idempotency-Key는 64자 이하여야 합니다.");
        }
    }

    public static String sha256(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                appendDigestValue(digest, value);
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static void appendDigestValue(MessageDigest digest, Object value) {
        if (value instanceof List<?> list) {
            digest.update("[list]".getBytes(StandardCharsets.UTF_8));
            for (Object item : list) {
                appendDigestValue(digest, item);
                digest.update((byte) 1);
            }
            return;
        }
        digest.update((value == null ? "" : String.valueOf(value)).getBytes(StandardCharsets.UTF_8));
    }
}
