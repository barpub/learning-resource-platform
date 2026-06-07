-- 用户浏览历史表

CREATE TABLE IF NOT EXISTS view_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  resource_id BIGINT NOT NULL COMMENT '资源ID',
  view_duration INT DEFAULT 0 COMMENT '浏览时长（秒）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间',
  INDEX idx_user_id (user_id),
  INDEX idx_resource_id (resource_id),
  INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户浏览历史表';
