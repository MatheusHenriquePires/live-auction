CREATE TABLE users (
  id            UUID PRIMARY KEY,
  email         VARCHAR(255) NOT NULL UNIQUE,
  display_name  VARCHAR(60)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER',   -- USER | ADMIN | BOT
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE auctions (
  id               UUID PRIMARY KEY,
  seller_id        UUID NOT NULL REFERENCES users(id),
  title            VARCHAR(140) NOT NULL,
  description      TEXT,
  image_url        VARCHAR(500),
  starting_price   BIGINT NOT NULL CHECK (starting_price > 0),   -- cents
  min_increment    BIGINT NOT NULL CHECK (min_increment > 0),    -- cents
  current_price    BIGINT NOT NULL,                              -- cents
  leader_id        UUID REFERENCES users(id),
  bid_count        INT  NOT NULL DEFAULT 0,
  status           VARCHAR(20) NOT NULL,   -- SCHEDULED | ACTIVE | ENDED | CANCELLED
  starts_at        TIMESTAMPTZ NOT NULL,
  ends_at          TIMESTAMPTZ NOT NULL,
  winner_id        UUID REFERENCES users(id),
  version          BIGINT NOT NULL DEFAULT 0,                    -- optimistic lock
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (ends_at > starts_at)
);
CREATE INDEX idx_auctions_status_ends ON auctions(status, ends_at);

CREATE TABLE bids (
  id          UUID PRIMARY KEY,              -- = client-provided bidId (idempotency key)
  auction_id  UUID NOT NULL REFERENCES auctions(id),
  bidder_id   UUID NOT NULL REFERENCES users(id),
  amount      BIGINT NOT NULL CHECK (amount > 0),
  placed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (auction_id, amount)                -- last-resort barrier against equal bids
);
CREATE INDEX idx_bids_auction_amount ON bids(auction_id, amount DESC);

CREATE TABLE outbox_events (
  id             UUID PRIMARY KEY,
  aggregate_type VARCHAR(40)  NOT NULL,      -- AUCTION
  aggregate_id   UUID         NOT NULL,
  event_type     VARCHAR(60)  NOT NULL,      -- BidPlaced | AuctionExtended | AuctionEnded | AuctionStarted
  payload        JSONB        NOT NULL,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_at   TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at) WHERE published_at IS NULL;

CREATE TABLE refresh_tokens (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES users(id),
  token_hash VARCHAR(100) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked    BOOLEAN NOT NULL DEFAULT false
);

-- ShedLock
CREATE TABLE shedlock (
  name       VARCHAR(64) PRIMARY KEY,
  lock_until TIMESTAMP NOT NULL,
  locked_at  TIMESTAMP NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
