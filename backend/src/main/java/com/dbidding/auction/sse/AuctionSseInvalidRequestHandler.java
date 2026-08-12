package com.dbidding.auction.sse;

import com.dbidding.global.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.Comparator;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** EventSource의 text/event-stream Accept에도 JSON 400을 확실히 전달하는 전용 처리기다. */
@RestControllerAdvice(assignableTypes = AuctionSseController.class)
public class AuctionSseInvalidRequestHandler {
    private static final String INVALID_REQUEST = "INVALID_REQUEST";
    private static final String INVALID_REQUEST_MESSAGE = "요청 정보를 확인해 주세요.";
    private static final ObjectMapper ERROR_RESPONSE_MAPPER = new ObjectMapper();

    @ExceptionHandler(ConstraintViolationException.class)
    public void handleConstraintViolation(ConstraintViolationException exception, HttpServletResponse response) throws IOException {
        writeInvalidRequest(response, exception.getConstraintViolations().stream()
                .min(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .filter(message -> message != null && !message.isBlank())
                .orElse(INVALID_REQUEST_MESSAGE));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public void handleHandlerValidation(HandlerMethodValidationException exception, HttpServletResponse response) throws IOException {
        writeInvalidRequest(response, firstMessage(exception.getAllErrors()));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class, BindException.class})
    public void handleMissingParameter(Exception exception, HttpServletResponse response) throws IOException {
        writeInvalidRequest(response, INVALID_REQUEST_MESSAGE);
    }

    private void writeInvalidRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ERROR_RESPONSE_MAPPER.writeValue(response.getWriter(), new ApiErrorResponse(INVALID_REQUEST, message));
    }

    private String firstMessage(Iterable<? extends MessageSourceResolvable> errors) {
        for (MessageSourceResolvable error : errors) {
            if (error.getDefaultMessage() != null && !error.getDefaultMessage().isBlank()) return error.getDefaultMessage();
        }
        return INVALID_REQUEST_MESSAGE;
    }
}
