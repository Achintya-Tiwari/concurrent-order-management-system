-- =============================================================================
-- V1__init_schema.sql
-- Flyway migration: initial schema for the Order Management System database.
--
-- Source: Migrated from the original project's order-management_db.sql dump
--         and Queries.txt (OMSMiniProject by rsrahul1000).
--
-- Naming conventions used:
--   - Tables:   snake_case, singular noun  (customer, stock_item, ...)
--   - PKs:      <table>_id
--   - FKs:      fk_<child_table>_<parent_table>
--   - Indexes:  idx_<table>_<column(s)>
--   - Uniques:  uq_<table>_<column>
--
-- Change log:
--   V1 (Phase 1): Base schema — customer, stock_item, purchase_order, order_item.
--   Added: quantity column to stock_item (inventory tracking — new in modernisation).
--   Added: actual_ship_date to purchase_order (was implicit in original, now explicit).
-- =============================================================================

-- Safety: ensure we start clean if re-running on an empty schema.
-- Flyway prevents this file from running twice via its checksums table.
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- Table: customer
--
-- Stores registered buyers.
-- Business rule: cell_number must be unique (duplicate inserts are rejected).
-- =============================================================================
CREATE TABLE IF NOT EXISTS customer (
    customer_id BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    address     VARCHAR(255),
    cell_number VARCHAR(15)  NOT NULL,

    CONSTRAINT pk_customer        PRIMARY KEY (customer_id),
    CONSTRAINT uq_customer_cell   UNIQUE      (cell_number)
);

-- Index on cell_number to speed up duplicate checks before inserts
CREATE INDEX idx_customer_cell_number ON customer (cell_number);

-- =============================================================================
-- Table: stock_item
--
-- Product catalogue.
-- Business rule: name must be unique (duplicate inserts are rejected).
-- Modernisation addition: quantity column for inventory tracking.
-- =============================================================================
CREATE TABLE IF NOT EXISTS stock_item (
    stock_item_id BIGINT         NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100)   NOT NULL,
    price         DECIMAL(10, 2) NOT NULL,
    -- NEW in modernisation: tracks available inventory.
    -- Defaults to 0 (out of stock) until explicitly stocked via the API.
    quantity      INT            NOT NULL DEFAULT 0,

    CONSTRAINT pk_stock_item      PRIMARY KEY (stock_item_id),
    CONSTRAINT uq_stock_item_name UNIQUE      (name),
    CONSTRAINT chk_stock_price    CHECK       (price > 0),
    CONSTRAINT chk_stock_qty      CHECK       (quantity >= 0)
);

-- =============================================================================
-- Table: purchase_order
--
-- A transaction record linking a customer to a set of line items.
--
-- Business rules:
--   - ship_date = placed_date + 4 days (set by the application, not DB).
--   - An order with status = 'PENDING' and ship_date < CURDATE() is delayed.
--   - On customer delete → cascade delete (FK below).
-- =============================================================================
CREATE TABLE IF NOT EXISTS purchase_order (
    order_id        BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id     BIGINT      NOT NULL,
    placed_date     DATE        NOT NULL,
    -- SLA deadline: placed_date + 4 days (set at insert time by application)
    ship_date       DATE        NOT NULL,
    -- Actual dispatch date — NULL while PENDING, populated when shipped
    actual_ship_date DATE,
    -- Enum stored as VARCHAR so values are human-readable in the DB
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    CONSTRAINT pk_purchase_order      PRIMARY KEY (order_id),
    CONSTRAINT fk_order_customer      FOREIGN KEY (customer_id)
        REFERENCES customer (customer_id)
        ON DELETE CASCADE,             -- mirrors original cascade behaviour
    CONSTRAINT chk_order_status       CHECK (status IN ('PENDING', 'SHIPPED')),
    CONSTRAINT chk_ship_after_placed  CHECK (ship_date >= placed_date)
);

-- Indexes to support common query patterns
CREATE INDEX idx_order_customer_id  ON purchase_order (customer_id);
CREATE INDEX idx_order_placed_date  ON purchase_order (placed_date);
CREATE INDEX idx_order_status       ON purchase_order (status);
-- Composite: the "delayed orders" query filters on both status and ship_date
CREATE INDEX idx_order_status_ship  ON purchase_order (status, ship_date);

-- =============================================================================
-- Table: order_item
--
-- A single line item within a purchase order.
--
-- unit_price is a SNAPSHOT of the stock item's price at order time —
-- stored here so billing reports remain accurate even if prices change later.
--
-- On order delete → cascade delete.
-- On stock item delete → cascade delete (matches original ON DELETE CASCADE).
-- =============================================================================
CREATE TABLE IF NOT EXISTS order_item (
    order_item_id BIGINT         NOT NULL AUTO_INCREMENT,
    order_id      BIGINT         NOT NULL,
    stock_item_id BIGINT         NOT NULL,
    quantity      INT            NOT NULL,
    -- Snapshot price at purchase time (do NOT derive from stock_item.price)
    unit_price    DECIMAL(10, 2) NOT NULL,

    CONSTRAINT pk_order_item          PRIMARY KEY (order_item_id),
    CONSTRAINT fk_order_item_order    FOREIGN KEY (order_id)
        REFERENCES purchase_order (order_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_order_item_stock    FOREIGN KEY (stock_item_id)
        REFERENCES stock_item (stock_item_id)
        ON DELETE CASCADE,             -- matches original: deleting a stock item cascades
    CONSTRAINT chk_item_quantity      CHECK (quantity >= 1),
    CONSTRAINT chk_item_price         CHECK (unit_price > 0)
);

CREATE INDEX idx_order_item_order_id      ON order_item (order_id);
CREATE INDEX idx_order_item_stock_item_id ON order_item (stock_item_id);

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- End of V1__init_schema.sql
-- Next migration: V2__seed_data.sql (optional dev seed data)
-- =============================================================================
