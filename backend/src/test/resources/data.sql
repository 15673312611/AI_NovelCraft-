-- Seed minimal data for tests

INSERT INTO users (username, email, password, nickname, status, email_verified, created_at, updated_at)
VALUES ('admin', 'admin@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8imdqMNq4NjewGG6/0/Nt.jvJNjKu', '系统管理员', 'ACTIVE', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

