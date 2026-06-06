-- MySQL migration: add danmaku support
USE learning_resource_platform;

ALTER TABLE resource
  ADD COLUMN danmaku_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用弹幕',
  ADD COLUMN danmaku_permission VARCHAR(20) NOT NULL DEFAULT 'LOGGED' COMMENT '发送权限 EVERYONE/LOGGED/OWNER';

CREATE TABLE IF NOT EXISTS danmaku (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '弹幕ID',
  resource_id BIGINT NOT NULL COMMENT '资源ID',
  user_id BIGINT DEFAULT NULL COMMENT '发送者ID，匿名为 NULL',
  content VARCHAR(255) NOT NULL COMMENT '弹幕内容',
  time_seconds DECIMAL(10, 3) NOT NULL DEFAULT 0 COMMENT '视频时间点(秒)',
  type VARCHAR(16) NOT NULL DEFAULT 'scroll' COMMENT '类型 scroll/top/bottom',
  color VARCHAR(255) NOT NULL DEFAULT '#ffffff' COMMENT '颜色',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1显示 0隐藏',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_danmaku_resource_time (resource_id, time_seconds),
  INDEX idx_danmaku_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频弹幕';
