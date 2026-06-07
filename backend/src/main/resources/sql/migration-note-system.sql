-- 笔记系统数据库迁移脚本

-- 笔记表
CREATE TABLE IF NOT EXISTS note (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '笔记ID',
  title VARCHAR(200) NOT NULL COMMENT '笔记标题',
  content TEXT NOT NULL COMMENT '笔记内容（支持Markdown）',
  category VARCHAR(50) DEFAULT NULL COMMENT '学习方向分类（如：前端、后端、数据库）',
  resource_id BIGINT DEFAULT NULL COMMENT '关联的学习资源ID',
  anchor_type VARCHAR(20) DEFAULT NULL COMMENT '笔记片段类型 VIDEO/TEXT/DOCUMENT/RESOURCE',
  anchor_text TEXT DEFAULT NULL COMMENT '笔记记录的文本片段',
  anchor_image MEDIUMTEXT DEFAULT NULL COMMENT '笔记记录的视频截图或图片片段',
  anchor_seconds DECIMAL(10,3) DEFAULT NULL COMMENT '视频时间点（秒）',
  user_id BIGINT NOT NULL COMMENT '创建者ID',
  is_favorite TINYINT NOT NULL DEFAULT 0 COMMENT '是否收藏 1是 0否',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1正常 0删除',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_user_id (user_id),
  INDEX idx_resource_id (resource_id),
  INDEX idx_category (category),
  INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记表';

-- 笔记标签表
CREATE TABLE IF NOT EXISTS note_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
  name VARCHAR(50) NOT NULL COMMENT '标签名称（知识点）',
  user_id BIGINT NOT NULL COMMENT '创建者ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_user_name (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记标签表';

-- 笔记标签关联表
CREATE TABLE IF NOT EXISTS note_tag_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联ID',
  note_id BIGINT NOT NULL COMMENT '笔记ID',
  tag_id BIGINT NOT NULL COMMENT '标签ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_note_tag (note_id, tag_id),
  INDEX idx_note_id (note_id),
  INDEX idx_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记标签关联表';
