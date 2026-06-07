CREATE TABLE IF NOT EXISTS note_share (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '笔记分享ID',
  token VARCHAR(64) NOT NULL COMMENT '分享令牌',
  note_id BIGINT NOT NULL COMMENT '源笔记ID',
  owner_id BIGINT NOT NULL COMMENT '源笔记创建者ID',
  import_count INT NOT NULL DEFAULT 0 COMMENT '导入次数',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1有效 0失效',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_note_share_token (token),
  INDEX idx_note_share_note_owner (note_id, owner_id),
  INDEX idx_note_share_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记分享表';
