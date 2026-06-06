CREATE TABLE IF NOT EXISTS forum_post (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL,
  image_urls TEXT,
  folder_path VARCHAR(500) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  like_count INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_forum_post_user (user_id),
  INDEX idx_forum_post_create_time (create_time),
  FULLTEXT INDEX ft_forum_post_title_content (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='forum posts';

CREATE TABLE IF NOT EXISTS forum_post_resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  folder_path VARCHAR(500) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_forum_post_resource_post (post_id),
  INDEX idx_forum_post_resource_resource (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='forum linked resources';

CREATE TABLE IF NOT EXISTS forum_comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  image_urls TEXT,
  parent_id BIGINT DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_forum_comment_post (post_id),
  INDEX idx_forum_comment_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='forum comments';
