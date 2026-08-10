package com.dbidding.upload.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class UploadException extends ApiException {

    private UploadException(HttpStatus status, String code, String message, Throwable cause) {
        super(status, code, message, cause);
    }

    public static UploadException invalidContentType(String contentType) {
        return new UploadException(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD_CONTENT_TYPE", "허용되지 않는 이미지 형식입니다: " + contentType, null);
    }

    public static UploadException externalServiceUnavailable(Throwable cause) {
        return new UploadException(HttpStatus.SERVICE_UNAVAILABLE, "UPLOAD_EXTERNAL_SERVICE_UNAVAILABLE", "파일 업로드 서비스를 일시적으로 사용할 수 없습니다.", cause);
    }
}
