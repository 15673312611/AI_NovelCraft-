-- H2 schema for tests (minimal tables used by NovelService and repositories)

DROP TABLE IF EXISTS novels;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    avatar_url VARCHAR(255),
    bio VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    last_login_at TIMESTAMP NULL,
    email_verified BOOLEAN,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);

CREATE TABLE novels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    subtitle VARCHAR(200),
    description VARCHAR(2000),
    cover_image_url VARCHAR(500),
    status VARCHAR(32) NOT NULL,
    genre VARCHAR(100),
    tags VARCHAR(500),
    target_audience VARCHAR(200),
    word_count INT DEFAULT 0,
    chapter_count INT DEFAULT 0,
    estimated_completion TIMESTAMP NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    is_public BOOLEAN,
    rating DOUBLE,
    rating_count INT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_novels_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

