package com.dbidding.auction.port;

public interface CurrentUserPort {
    CurrentUser currentUser();

    record CurrentUser(
            Integer id,
            String nickname,
            boolean seller,
            boolean restricted
    ) {
    }
}
