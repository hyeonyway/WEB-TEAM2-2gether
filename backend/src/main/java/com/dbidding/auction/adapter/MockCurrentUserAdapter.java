package com.dbidding.auction.adapter;

import com.dbidding.auction.port.CurrentUserPort;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("auction-mock")
public class MockCurrentUserAdapter implements CurrentUserPort {
    private static final Integer MOCK_USER_ID = 1;
    private static final String MOCK_NICKNAME = "mock-seller";
    private static final String MOCK_USER_ID_HEADER = "X-Mock-User-Id";
    private static final String MOCK_NICKNAME_HEADER = "X-Mock-User-Nickname";
    private static final String MOCK_SELLER_HEADER = "X-Mock-User-Seller";

    private final ObjectProvider<HttpServletRequest> requestProvider;

    public MockCurrentUserAdapter(ObjectProvider<HttpServletRequest> requestProvider) {
        this.requestProvider = requestProvider;
    }

    @Override
    public CurrentUser currentUser() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request == null) {
            return new CurrentUser(MOCK_USER_ID, MOCK_NICKNAME, true, false);
        }
        Integer userId = parseUserId(request.getHeader(MOCK_USER_ID_HEADER));
        String nickname = headerOrDefault(request.getHeader(MOCK_NICKNAME_HEADER), "mock-user-" + userId);
        boolean seller = Boolean.parseBoolean(headerOrDefault(request.getHeader(MOCK_SELLER_HEADER), "true"));
        return new CurrentUser(userId, nickname, seller, false);
    }

    private Integer parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return MOCK_USER_ID;
        }
        return Integer.valueOf(value);
    }

    private String headerOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
