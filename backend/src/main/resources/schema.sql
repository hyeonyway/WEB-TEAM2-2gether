CREATE DATABASE IF NOT EXISTS dbidding
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE dbidding;


CREATE TABLE users
(
    id                 INT          NOT NULL AUTO_INCREMENT,
    email              VARCHAR(255) NOT NULL,
    nickname           VARCHAR(30)  NOT NULL,
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    role               VARCHAR(20)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
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
    refresh_token CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,

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
    address_name     VARCHAR(50)  NOT NULL,
    address          VARCHAR(255) NOT NULL,
    detailed_address VARCHAR(255) NULL,
    postal_code      CHAR(5)      CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    is_default       BOOLEAN      NOT NULL,

    CONSTRAINT pk_addresses PRIMARY KEY (id),
    CONSTRAINT fk_addresses_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    INDEX idx_addresses_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE card_sets
(
    id           INT          NOT NULL AUTO_INCREMENT,
    name         VARCHAR(150) NOT NULL,
    code         VARCHAR(50)  NULL,

    CONSTRAINT pk_card_sets PRIMARY KEY (id),
    CONSTRAINT uk_card_sets_code UNIQUE (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE card_metadata
(
    id              INT          NOT NULL AUTO_INCREMENT,
    card_set_id     INT          NOT NULL,
    name            VARCHAR(200) NOT NULL,
    language        VARCHAR(20)  NULL,
    psa_grade       VARCHAR(15)  NULL,
    rarity          VARCHAR(30)  NULL,
    image_path      VARCHAR(500) NULL,
    issued_year     CHAR(4)      NULL,
    card_number     VARCHAR(50)  NULL,

    CONSTRAINT pk_card_metadata PRIMARY KEY (id),
    CONSTRAINT fk_card_metadata_card_set
        FOREIGN KEY (card_set_id) REFERENCES card_sets (id),

    INDEX idx_card_metadata_card_set_id (card_set_id),
    INDEX idx_card_metadata_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE psa_certification_fixtures
(
    id                   INT         NOT NULL AUTO_INCREMENT,
    certification_number VARCHAR(10) NOT NULL,
    item_id              INT         NOT NULL,

    CONSTRAINT pk_psa_certification_fixtures PRIMARY KEY (id),
    CONSTRAINT uk_psa_certification_fixtures_number UNIQUE (certification_number),
    CONSTRAINT fk_psa_certification_fixtures_item
        FOREIGN KEY (item_id) REFERENCES card_metadata (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE item_statistics
(
    item_id              INT           NOT NULL,
    as_of_date           DATE          NOT NULL,
    latest_price         BIGINT        NULL,
    average_price_30d     BIGINT        NULL,
    lowest_price_30d      BIGINT        NULL,
    highest_price_30d     BIGINT        NULL,
    bid_count_30d         INT           NOT NULL DEFAULT 0,
    ended_auction_count_30d INT         NOT NULL DEFAULT 0,
    wishlist_count       INT           NOT NULL DEFAULT 0,
    daily_change_rate    DECIMAL(8, 2) NULL,
    weekly_change_rate   DECIMAL(8, 2) NULL,
    monthly_change_rate  DECIMAL(8, 2) NULL,
    updated_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_item_statistics PRIMARY KEY (item_id),
    CONSTRAINT fk_item_statistics_item
        FOREIGN KEY (item_id) REFERENCES card_metadata (id),

    INDEX idx_item_statistics_latest_price (latest_price),
    INDEX idx_item_statistics_daily_change_rate (daily_change_rate)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE item_daily_statistics
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    item_id               INT          NOT NULL,
    statistics_date       DATE         NOT NULL,
    latest_price          BIGINT       NULL,
    average_price         BIGINT       NULL,
    lowest_price          BIGINT       NULL,
    highest_price         BIGINT       NULL,
    bid_count             INT          NOT NULL DEFAULT 0,
    ended_auction_count   INT          NOT NULL DEFAULT 0,
    created_at             TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_item_daily_statistics PRIMARY KEY (id),
    CONSTRAINT uk_item_daily_statistics_item_date UNIQUE (item_id, statistics_date),
    CONSTRAINT fk_item_daily_statistics_item
        FOREIGN KEY (item_id) REFERENCES card_metadata (id),

    INDEX idx_item_daily_statistics_date_item (statistics_date, item_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE market_daily_statistics
(
    statistics_date       DATE         NOT NULL,
    average_price         BIGINT       NULL,
    lowest_price          BIGINT       NULL,
    highest_price         BIGINT       NULL,
    winning_price_total_30d BIGINT     NOT NULL DEFAULT 0,
    highest_price_30d     BIGINT       NOT NULL DEFAULT 0,
    bid_count_30d         INT          NOT NULL DEFAULT 0,
    ended_auction_count_30d INT        NOT NULL DEFAULT 0,
    bid_count             INT          NOT NULL DEFAULT 0,
    ended_auction_count   INT          NOT NULL DEFAULT 0,
    created_at             TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_market_daily_statistics PRIMARY KEY (statistics_date)
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
    buy_now_price        BIGINT       NULL,
    seller_memo          VARCHAR(1000) NULL,
    psa_certification    VARCHAR(32)  NULL,
    self_grade           VARCHAR(32)  NULL,
    psa_verified         BOOLEAN      NOT NULL DEFAULT FALSE,
    delivery_fee         BIGINT       NOT NULL,
    status               VARCHAR(255) NOT NULL,
    open_time            TIMESTAMP(6) NOT NULL,
    estimated_close_time TIMESTAMP(6) NOT NULL,
    close_time           TIMESTAMP(6) NOT NULL,
    bid_count            INT          NOT NULL,
    bid_price_unit       BIGINT       NOT NULL,
    last_bid_event_version BIGINT     NOT NULL DEFAULT 0,
    is_hyped             BOOLEAN      NOT NULL,
    idempotency_key      VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    idempotency_request_hash CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,

    CONSTRAINT pk_auctions PRIMARY KEY (id),
    CONSTRAINT uk_auctions_user_idempotency
        UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_auctions_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_auctions_item
        FOREIGN KEY (item_id) REFERENCES card_metadata (id),

    INDEX idx_auctions_user_id (user_id),
    INDEX idx_auctions_item_id (item_id),
    INDEX idx_auctions_status (status),
    INDEX idx_auctions_status_open_time_id (status, open_time DESC, id DESC),
    INDEX idx_auctions_item_status (item_id, status),
    INDEX idx_auctions_status_close_time (status, close_time),
    INDEX idx_auctions_status_bid_count_id (status, bid_count DESC, id DESC),
    INDEX idx_auctions_status_current_price (status, current_price)
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
    idempotency_key VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    idempotency_request_hash CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,

    CONSTRAINT pk_bids PRIMARY KEY (id),
    CONSTRAINT uk_bids_user_auction_idempotency
        UNIQUE (user_id, auction_id, idempotency_key),
    CONSTRAINT fk_bids_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bids_auction
        FOREIGN KEY (auction_id) REFERENCES auctions (id),

    INDEX idx_bids_user_id (user_id),
    INDEX idx_bids_auction_id (auction_id),
    INDEX idx_bids_auction_price (auction_id, bid_price),
    INDEX idx_bids_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE auction_bid_event_inbox
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    stream_id       VARCHAR(64)  NOT NULL,
    auction_id      INT,
    auction_version BIGINT,
    processed_at    TIMESTAMP(6) NOT NULL,

    CONSTRAINT pk_auction_bid_event_inbox PRIMARY KEY (id),
    CONSTRAINT uk_auction_bid_event_inbox_stream_id UNIQUE (stream_id),
    CONSTRAINT fk_auction_bid_event_inbox_auction
        FOREIGN KEY (auction_id) REFERENCES auctions (id),

    INDEX idx_auction_bid_event_inbox_auction_id (auction_id)
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
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    released_at TIMESTAMP(6) NULL,

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
    auction_id       INT          NULL,
    amount           BIGINT       NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    balance          BIGINT       NOT NULL,
    transaction_type VARCHAR(32)  NOT NULL,
    idempotency_key  VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,

    CONSTRAINT pk_point_records PRIMARY KEY (id),
    CONSTRAINT uk_point_records_wallet_idempotency
        UNIQUE (wallet_id, idempotency_key),
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
    type       VARCHAR(32)  NOT NULL,
    -- OUTBID는 실제 bid.id를 저장하고, bid와 무관한 나머지 타입(AUCTION_OPENED/WON/UNSOLD)은
    -- 0(사용 안 함을 뜻하는 sentinel)을 저장한다. MySQL UNIQUE 인덱스는 NULL만 서로 다른 값으로
    -- 취급하고 0은 일반 값과 동일하게 취급하므로, 이 컬럼 하나로 두 dedup 요구사항을 모두 만족한다:
    -- 1회성 타입은 (user_id, auction_id, type, 0)로 경매당 유저당 1회 dedup, OUTBID는
    -- (user_id, auction_id, type, bid_id)로 그 bid에 대해서만 dedup.
    bid_id     BIGINT       NOT NULL DEFAULT 0,
    message    VARCHAR(300) NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_notification_user_auction_type_bid
        UNIQUE (user_id, auction_id, type, bid_id),

    INDEX idx_notification_user_id (user_id),
    INDEX idx_notification_user_id_is_read (user_id, is_read)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE orders
(
    id         INT          NOT NULL AUTO_INCREMENT,
    auction_id INT          NOT NULL,
    buyer_id   INT          NOT NULL,
    seller_id  INT          NOT NULL,
    card_name  VARCHAR(200) NOT NULL,
    price      BIGINT       NOT NULL,
    status     VARCHAR(32)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uk_orders_auction UNIQUE (auction_id),
    CONSTRAINT fk_orders_auction
        FOREIGN KEY (auction_id) REFERENCES auctions (id),
    CONSTRAINT fk_orders_buyer
        FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT fk_orders_seller
        FOREIGN KEY (seller_id) REFERENCES users (id),

    INDEX idx_orders_buyer_status (buyer_id, status),
    INDEX idx_orders_seller_status (seller_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
