CREATE DATABASE IF NOT EXISTS dbidding
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE dbidding;


CREATE TABLE users
(
    id                 INT          NOT NULL AUTO_INCREMENT,
    email              VARCHAR(255) NOT NULL,
    nickname           VARCHAR(255) NOT NULL,
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    image_path         VARCHAR(255) NOT NULL,
    role               VARCHAR(255) NOT NULL,
    status             VARCHAR(255) NOT NULL,
    encrypted_password CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    salt               CHAR(32)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_nickname UNIQUE (nickname)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE authentication
(
    id            INT          NOT NULL AUTO_INCREMENT,
    user_id       INT          NOT NULL,
    refresh_token VARCHAR(255) NOT NULL,

    CONSTRAINT pk_authentication PRIMARY KEY (id),
    CONSTRAINT uk_authentication_user_id UNIQUE (user_id),
    CONSTRAINT uk_authentication_refresh_token UNIQUE (refresh_token),
    CONSTRAINT fk_authentication_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE addresses
(
    id               INT          NOT NULL AUTO_INCREMENT,
    user_id          INT          NOT NULL,
    address_name     VARCHAR(255) NOT NULL,
    address          VARCHAR(255) NOT NULL,
    detailed_address VARCHAR(255) NOT NULL,
    postal_code      VARCHAR(255) NOT NULL,
    is_default       BOOLEAN      NOT NULL,

    CONSTRAINT pk_addresses PRIMARY KEY (id),
    CONSTRAINT fk_addresses_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    INDEX idx_addresses_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE card_metadata
(
    id              INT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255) NOT NULL,
    set_name        VARCHAR(255) NOT NULL,
    image_path      VARCHAR(255) NOT NULL,
    country         VARCHAR(255) NOT NULL,
    card_number     VARCHAR(255) NOT NULL,
    language        VARCHAR(255) NOT NULL,
    release_year    INT          NOT NULL,
    grade_type      VARCHAR(255) NOT NULL,
    grade_value     VARCHAR(255) NOT NULL,
    psa_cert_number VARCHAR(255) NOT NULL,
    population      INT          NOT NULL,

    CONSTRAINT pk_card_metadata PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE item_statistics
(
    id            INT    NOT NULL AUTO_INCREMENT,
    item_id       INT    NOT NULL,
    avg_price     BIGINT NOT NULL,
    lowest_price  BIGINT NOT NULL,
    highest_price BIGINT NOT NULL,
    count         INT    NOT NULL,

    CONSTRAINT pk_item_statistics PRIMARY KEY (id),
    CONSTRAINT uk_item_statistics_item_id UNIQUE (item_id),
    CONSTRAINT fk_item_statistics_item
        FOREIGN KEY (item_id) REFERENCES card_metadata (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE auctions
(
    id                   INT          NOT NULL AUTO_INCREMENT,
    user_id              INT          NOT NULL,
    item_id              INT          NOT NULL,
    auction_name         VARCHAR(255) NOT NULL,
    description          VARCHAR(255) NOT NULL,
    start_price          BIGINT       NOT NULL,
    current_price        BIGINT       NOT NULL,
    buy_now_price        BIGINT       NOT NULL,
    delivery_fee         BIGINT       NOT NULL,
    status               VARCHAR(255) NOT NULL,
    open_time            TIMESTAMP(6) NOT NULL,
    estimated_close_time TIMESTAMP(6) NOT NULL,
    close_time           TIMESTAMP(6) NOT NULL,
    bid_count            INT          NOT NULL,
    bid_price_unit       BIGINT       NOT NULL,
    is_hyped             BOOLEAN      NOT NULL,

    CONSTRAINT pk_auctions PRIMARY KEY (id),
    CONSTRAINT fk_auctions_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_auctions_item
        FOREIGN KEY (item_id) REFERENCES card_metadata (id),

    INDEX idx_auctions_user_id (user_id),
    INDEX idx_auctions_item_id (item_id),
    INDEX idx_auctions_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE images
(
    id         INT          NOT NULL AUTO_INCREMENT,
    auction_id INT          NOT NULL,
    image_path VARCHAR(255) NOT NULL,

    CONSTRAINT pk_images PRIMARY KEY (id),
    CONSTRAINT fk_images_auction
        FOREIGN KEY (auction_id) REFERENCES auctions (id),

    INDEX idx_images_auction_id (auction_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE bids
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    INT          NOT NULL,
    auction_id INT          NOT NULL,
    bid_price  BIGINT       NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    status     VARCHAR(255) NOT NULL,

    CONSTRAINT pk_bids PRIMARY KEY (id),
    CONSTRAINT fk_bids_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bids_auction
        FOREIGN KEY (auction_id) REFERENCES auctions (id),

    INDEX idx_bids_user_id (user_id),
    INDEX idx_bids_auction_id (auction_id),
    INDEX idx_bids_auction_price (auction_id, bid_price)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE auto_bid_contracts
(
    id          INT          NOT NULL AUTO_INCREMENT,
    user_id     INT          NOT NULL,
    auction_id  INT          NOT NULL,
    max_price   BIGINT       NOT NULL,
    bid_unit    BIGINT       NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finish_time TIMESTAMP(6) NOT NULL,

    CONSTRAINT pk_auto_bid_contracts PRIMARY KEY (id),
    CONSTRAINT uk_auto_bid_contracts_user_auction
        UNIQUE (user_id, auction_id),
    CONSTRAINT fk_auto_bid_contracts_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_auto_bid_contracts_auction
        FOREIGN KEY (auction_id) REFERENCES auctions (id),

    INDEX idx_auto_bid_contracts_auction_id (auction_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE wallets
(
    id      INT    NOT NULL AUTO_INCREMENT,
    user_id INT    NOT NULL,
    point   BIGINT NOT NULL,

    CONSTRAINT pk_wallets PRIMARY KEY (id),
    CONSTRAINT uk_wallets_user_id UNIQUE (user_id),
    CONSTRAINT fk_wallets_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE wallet_holds
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    wallet_id  INT          NOT NULL,
    auction_id INT          NOT NULL,
    amount     BIGINT       NOT NULL,
    status     VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- NOTE: 현재 모든 컬럼을 NOT NULL로 통일했기 때문에 HELD 상태에서도 임시 해제 시각이 필요하다.
    -- 실제 동결 처리 구현 전, 해제되지 않은 hold를 표현할 수 있도록 NULL 허용 여부를 다시 검토한다.
    released_at TIMESTAMP(6) NOT NULL,

    CONSTRAINT pk_wallet_holds PRIMARY KEY (id),
    CONSTRAINT fk_wallet_holds_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT fk_wallet_holds_auction
        FOREIGN KEY (auction_id) REFERENCES auctions (id),

    INDEX idx_wallet_holds_wallet_id (wallet_id),
    INDEX idx_wallet_holds_auction_id (auction_id),
    INDEX idx_wallet_holds_wallet_status (wallet_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE point_records
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    wallet_id        INT          NOT NULL,
    auction_id       INT          NOT NULL,
    amount           BIGINT       NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    balance          BIGINT       NOT NULL,
    transaction_type VARCHAR(255) NOT NULL,

    CONSTRAINT pk_point_records PRIMARY KEY (id),
    CONSTRAINT fk_point_records_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT fk_point_records_auction
        FOREIGN KEY (auction_id) REFERENCES auctions (id),

    INDEX idx_point_records_wallet_id (wallet_id),
    INDEX idx_point_records_auction_id (auction_id),
    INDEX idx_point_records_wallet_created_at (wallet_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE wishlists
(
    id      INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    item_id INT NOT NULL,

    CONSTRAINT pk_wishlists PRIMARY KEY (id),
    CONSTRAINT uk_wishlists_user_item UNIQUE (user_id, item_id),
    CONSTRAINT fk_wishlists_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_wishlists_item
        FOREIGN KEY (item_id) REFERENCES card_metadata (id),

    INDEX idx_wishlists_item_id (item_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE notification
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    INT          NOT NULL,
    auction_id INT          NOT NULL,
    message    VARCHAR(255) NOT NULL,

    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notification_auction
        FOREIGN KEY (auction_id) REFERENCES auctions (id),

    INDEX idx_notification_user_id (user_id),
    INDEX idx_notification_auction_id (auction_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
