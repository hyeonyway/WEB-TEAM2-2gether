package com.dbidding.auction.stream;

import com.dbidding.global.exception.ApiException;
import org.springframework.http.HttpStatus;

public class StreamRecoveryAccessDeniedException extends ApiException {
    public StreamRecoveryAccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "STREAM_RECOVERY_ACCESS_DENIED", "관리자만 Stream 복구 기능을 사용할 수 있습니다.");
    }
}
