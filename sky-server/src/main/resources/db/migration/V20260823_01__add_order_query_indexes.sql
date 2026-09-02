CREATE INDEX idx_orders_status_order_time ON orders (status, order_time);
CREATE INDEX idx_order_detail_order_id ON order_detail (order_id);
