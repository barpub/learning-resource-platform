DROP TABLE IF EXISTS view_history;
DROP TABLE IF EXISTS media_enhancement_job;
DROP TABLE IF EXISTS note_share;
DROP TABLE IF EXISTS note_tag_relation;
DROP TABLE IF EXISTS note_tag;
DROP TABLE IF EXISTS note;
DROP TABLE IF EXISTS forum_comment;
DROP TABLE IF EXISTS forum_post_resource;
DROP TABLE IF EXISTS forum_post;
DROP TABLE IF EXISTS download_record;
DROP TABLE IF EXISTS favorite;
DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS resource;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  email VARCHAR(100) NOT NULL UNIQUE,
  nickname VARCHAR(50),
  avatar VARCHAR(255),
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  description VARCHAR(200),
  parent_id BIGINT DEFAULT 0,
  sort_order INT DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE resource (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  description CLOB,
  file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  file_size BIGINT NOT NULL,
  file_type VARCHAR(128) NOT NULL,
  resource_type VARCHAR(20) NOT NULL DEFAULT 'FILE',
  parent_id BIGINT,
  sort_order INT DEFAULT 0,
  relative_path VARCHAR(500),
  file_count INT DEFAULT 0,
  category_id BIGINT,
  tags VARCHAR(128),
  user_id BIGINT NOT NULL,
  download_count INT DEFAULT 0,
  view_count INT DEFAULT 0,
  rating DECIMAL(3,2) DEFAULT 0.00,
  rating_count INT DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  danmaku_enabled TINYINT NOT NULL DEFAULT 1,
  danmaku_permission VARCHAR(20) NOT NULL DEFAULT 'LOGGED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE media_enhancement_job (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_resource_id BIGINT NOT NULL,
  output_resource_id BIGINT,
  user_id BIGINT NOT NULL,
  media_type VARCHAR(20) NOT NULL,
  target_resolution VARCHAR(20) DEFAULT 'ORIGINAL',
  target_fps INT,
  video_preset VARCHAR(20) DEFAULT 'BALANCED',
  audio_preset VARCHAR(20) DEFAULT 'HIFI',
  ai_upscale TINYINT NOT NULL DEFAULT 0,
  frame_interpolation TINYINT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  progress INT NOT NULL DEFAULT 0,
  message VARCHAR(500),
  output_file_path VARCHAR(500),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_media_job_source ON media_enhancement_job(source_resource_id);
CREATE INDEX idx_media_job_user ON media_enhancement_job(user_id);
CREATE INDEX idx_media_job_status ON media_enhancement_job(status);
CREATE INDEX idx_media_job_output ON media_enhancement_job(output_resource_id);

CREATE TABLE note (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  content CLOB NOT NULL,
  category VARCHAR(50),
  resource_id BIGINT,
  anchor_type VARCHAR(20),
  anchor_text CLOB,
  anchor_image CLOB,
  anchor_seconds DECIMAL(10,3),
  user_id BIGINT NOT NULL,
  is_favorite TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_note_user_id ON note(user_id);
CREATE INDEX idx_note_resource_id ON note(resource_id);
CREATE INDEX idx_note_category ON note(category);
CREATE INDEX idx_note_create_time ON note(create_time);

CREATE TABLE note_tag (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  user_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_note_tag_user_name UNIQUE (user_id, name)
);

CREATE TABLE note_tag_relation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  note_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_note_tag_relation UNIQUE (note_id, tag_id)
);

CREATE INDEX idx_note_tag_relation_note_id ON note_tag_relation(note_id);
CREATE INDEX idx_note_tag_relation_tag_id ON note_tag_relation(tag_id);

CREATE TABLE note_share (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  token VARCHAR(64) NOT NULL,
  note_id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  import_count INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_note_share_token UNIQUE (token)
);

CREATE INDEX idx_note_share_note_owner ON note_share(note_id, owner_id);
CREATE INDEX idx_note_share_create_time ON note_share(create_time);

CREATE TABLE comment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  resource_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content CLOB NOT NULL,
  rating TINYINT,
  parent_id BIGINT DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE favorite (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_favorite_user_resource UNIQUE (user_id, resource_id)
);

CREATE TABLE download_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  download_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ip_address VARCHAR(50)
);

DROP TABLE IF EXISTS ftp_connection;

CREATE TABLE ftp_connection (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  host VARCHAR(255) NOT NULL,
  port INT NOT NULL DEFAULT 21,
  username VARCHAR(100) NOT NULL DEFAULT 'anonymous',
  password_cipher VARCHAR(500),
  passive_mode TINYINT NOT NULL DEFAULT 1,
  encoding VARCHAR(32) NOT NULL DEFAULT 'UTF-8',
  home_path VARCHAR(500) NOT NULL DEFAULT '/',
  description VARCHAR(500),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS danmaku;

CREATE TABLE danmaku (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  resource_id BIGINT NOT NULL,
  user_id BIGINT,
  content VARCHAR(255) NOT NULL,
  time_seconds DECIMAL(10, 3) NOT NULL DEFAULT 0,
  type VARCHAR(16) NOT NULL DEFAULT 'scroll',
  color VARCHAR(255) NOT NULL DEFAULT '#ffffff',
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE forum_post (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  content CLOB NOT NULL,
  image_urls CLOB,
  folder_path VARCHAR(500),
  status TINYINT NOT NULL DEFAULT 1,
  like_count INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE forum_post_resource (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  folder_path VARCHAR(500),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE forum_comment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content CLOB NOT NULL,
  image_urls CLOB,
  parent_id BIGINT DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE view_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  view_duration INT DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vh_user_id ON view_history(user_id);
CREATE INDEX idx_vh_resource_id ON view_history(resource_id);
CREATE INDEX idx_vh_create_time ON view_history(create_time);
