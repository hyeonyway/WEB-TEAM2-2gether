package com.dbidding.auction.adapter;

import com.dbidding.auction.port.CurrentUserPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("auction-mock")
public class MockCurrentUserAdapter implements CurrentUserPort {
    private static final Integer MOCK_USER_ID = 1;
    private static final String MOCK_NICKNAME = "mock-seller";

    @Override
    public CurrentUser currentUser() {
        return new CurrentUser(MOCK_USER_ID, MOCK_NICKNAME, true, false);
    }
}
