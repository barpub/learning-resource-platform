CREATE DATABASE IF NOT EXISTS learning_resource_platform
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE learning_resource_platform;

CREATE TABLE IF NOT EXISTS `user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  password VARCHAR(100) NOT NULL COMMENT '加密密码',
  email VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
  nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  avatar VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色 USER/ADMIN',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
  name VARCHAR(50) NOT NULL COMMENT '分类名称',
  description VARCHAR(200) DEFAULT NULL COMMENT '分类描述',
  parent_id BIGINT DEFAULT 0 COMMENT '父分类ID，0为顶级',
  sort_order INT DEFAULT 0 COMMENT '排序序号',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';

CREATE TABLE IF NOT EXISTS resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '资源ID',
  title VARCHAR(200) NOT NULL COMMENT '资源标题',
  description TEXT COMMENT '资源描述',
  file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_path VARCHAR(500) NOT NULL COMMENT '文件存储路径',
  file_size BIGINT NOT NULL COMMENT '文件大小',
  file_type VARCHAR(128) NOT NULL COMMENT '文件类型',
  resource_type VARCHAR(20) NOT NULL DEFAULT 'FILE' COMMENT '资源类型 FILE/FOLDER',
  parent_id BIGINT DEFAULT NULL COMMENT '父资源ID，文件夹内文件指向父文件夹',
  sort_order INT DEFAULT 0 COMMENT '文件夹内排序',
  relative_path VARCHAR(500) DEFAULT NULL COMMENT '批量上传时的相对路径',
  file_count INT DEFAULT 0 COMMENT '文件夹内文件数量',
  category_id BIGINT DEFAULT NULL COMMENT '分类ID',
  tags VARCHAR(128) DEFAULT NULL COMMENT '上传者自定义标签，逗号分隔',
  user_id BIGINT NOT NULL COMMENT '上传者ID',
  download_count INT DEFAULT 0 COMMENT '下载次数',
  view_count INT DEFAULT 0 COMMENT '浏览次数',
  rating DECIMAL(3,2) DEFAULT 0.00 COMMENT '平均评分',
  rating_count INT DEFAULT 0 COMMENT '评分人数',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1正常 0下架',
  danmaku_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用弹幕',
  danmaku_permission VARCHAR(20) NOT NULL DEFAULT 'LOGGED' COMMENT '发送权限 EVERYONE/LOGGED/OWNER',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_resource_category_id (category_id),
  INDEX idx_resource_user_id (user_id),
  INDEX idx_resource_parent_id (parent_id),
  INDEX idx_resource_create_time (create_time),
  FULLTEXT INDEX ft_resource_title_desc (title, description)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源表';

CREATE TABLE IF NOT EXISTS media_enhancement_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'media enhancement job ID',
  source_resource_id BIGINT NOT NULL COMMENT 'source resource ID',
  output_resource_id BIGINT DEFAULT NULL COMMENT 'generated resource ID',
  user_id BIGINT NOT NULL COMMENT 'owner user ID',
  media_type VARCHAR(20) NOT NULL COMMENT 'VIDEO/AUDIO',
  target_resolution VARCHAR(20) DEFAULT 'ORIGINAL' COMMENT 'ORIGINAL/720P/1080P/2K/4K',
  target_fps INT DEFAULT NULL COMMENT 'target FPS for video',
  video_preset VARCHAR(20) DEFAULT 'BALANCED' COMMENT 'FAST/BALANCED/QUALITY',
  audio_preset VARCHAR(20) DEFAULT 'HIFI' COMMENT 'HIFI/VOCAL/BEAT',
  ai_upscale TINYINT NOT NULL DEFAULT 0 COMMENT 'reserved AI upscale flag',
  frame_interpolation TINYINT NOT NULL DEFAULT 0 COMMENT 'use FFmpeg motion interpolation',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
  progress INT NOT NULL DEFAULT 0 COMMENT 'progress percentage',
  message VARCHAR(500) DEFAULT NULL COMMENT 'status message',
  output_file_path VARCHAR(500) DEFAULT NULL COMMENT 'generated file path',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  INDEX idx_media_job_source (source_resource_id),
  INDEX idx_media_job_user (user_id),
  INDEX idx_media_job_status (status),
  INDEX idx_media_job_output (output_resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='media enhancement jobs';

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
  INDEX idx_note_user_id (user_id),
  INDEX idx_note_resource_id (resource_id),
  INDEX idx_note_category (category),
  INDEX idx_note_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记表';

CREATE TABLE IF NOT EXISTS note_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
  name VARCHAR(50) NOT NULL COMMENT '标签名称（知识点）',
  user_id BIGINT NOT NULL COMMENT '创建者ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_note_tag_user_name (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记标签表';

CREATE TABLE IF NOT EXISTS note_tag_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联ID',
  note_id BIGINT NOT NULL COMMENT '笔记ID',
  tag_id BIGINT NOT NULL COMMENT '标签ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_note_tag_relation (note_id, tag_id),
  INDEX idx_note_tag_relation_note_id (note_id),
  INDEX idx_note_tag_relation_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记标签关联表';

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

CREATE TABLE IF NOT EXISTS comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
  resource_id BIGINT NOT NULL COMMENT '资源ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  content TEXT NOT NULL COMMENT '评论内容',
  rating TINYINT DEFAULT NULL COMMENT '评分 1-5',
  parent_id BIGINT DEFAULT 0 COMMENT '父评论ID，0为顶级',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1显示 0隐藏',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_comment_resource_id (resource_id),
  INDEX idx_comment_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

CREATE TABLE IF NOT EXISTS favorite (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收藏ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  resource_id BIGINT NOT NULL COMMENT '资源ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_favorite_user_resource (user_id, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

CREATE TABLE IF NOT EXISTS download_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  resource_id BIGINT NOT NULL COMMENT '资源ID',
  download_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下载时间',
  ip_address VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
  INDEX idx_download_user_id (user_id),
  INDEX idx_download_resource_id (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='下载记录表';

CREATE TABLE IF NOT EXISTS view_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  resource_id BIGINT NOT NULL COMMENT '资源ID',
  view_duration INT DEFAULT 0 COMMENT '浏览时长（秒）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间',
  INDEX idx_view_history_user_id (user_id),
  INDEX idx_view_history_resource_id (resource_id),
  INDEX idx_view_history_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户浏览历史表';

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


-- 弹幕功能（建表时请同步增加 resource 字段；旧库执行 migration-004-danmaku.sql）
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
