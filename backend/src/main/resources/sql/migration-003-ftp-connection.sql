-- MySQL migration: add ftp_connection table for remote FTP browsing feature
USE learning_resource_platform;

CREATE TABLE IF NOT EXISTS ftp_connection (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '连接ID',
  name VARCHAR(100) NOT NULL COMMENT '展示名称',
  host VARCHAR(255) NOT NULL COMMENT '主机地址',
  port INT NOT NULL DEFAULT 21 COMMENT '端口',
  username VARCHAR(100) NOT NULL DEFAULT 'anonymous' COMMENT '登录用户名',
  password_cipher VARCHAR(500) DEFAULT NULL COMMENT '加密后的登录密码',
  passive_mode TINYINT NOT NULL DEFAULT 1 COMMENT '是否被动模式 1是 0否',
  encoding VARCHAR(32) NOT NULL DEFAULT 'UTF-8' COMMENT '服务端编码',
  home_path VARCHAR(500) NOT NULL DEFAULT '/' COMMENT '默认起始目录',
  description VARCHAR(500) DEFAULT NULL COMMENT '备注',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='FTP 远程连接配置';
