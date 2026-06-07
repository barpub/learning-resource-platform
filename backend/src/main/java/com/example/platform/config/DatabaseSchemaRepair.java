package com.example.platform.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!h2")
public class DatabaseSchemaRepair implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureResourceColumns();
        ensureMediaEnhancementTable();
        ensureViewHistoryTable();
        ensureNoteTables();
        ensureFtpConnectionTable();
        ensureDanmakuSupport();
        ensureForumTables();
    }

    private void ensureResourceColumns() {
        ensureColumnLengthAtLeast("resource", "file_type", 128,
                "ALTER TABLE resource MODIFY COLUMN file_type VARCHAR(128) NOT NULL COMMENT '文件类型'");
        ensureColumn("resource", "resource_type",
                "ALTER TABLE resource ADD COLUMN resource_type VARCHAR(20) NOT NULL DEFAULT 'FILE' COMMENT '资源类型 FILE/FOLDER' AFTER file_type");
        ensureColumn("resource", "parent_id",
                "ALTER TABLE resource ADD COLUMN parent_id BIGINT DEFAULT NULL COMMENT '父资源ID，文件夹内文件指向父文件夹' AFTER resource_type");
        ensureColumn("resource", "sort_order",
                "ALTER TABLE resource ADD COLUMN sort_order INT DEFAULT 0 COMMENT '文件夹内排序' AFTER parent_id");
        ensureColumn("resource", "relative_path",
                "ALTER TABLE resource ADD COLUMN relative_path VARCHAR(500) DEFAULT NULL COMMENT '批量上传时的相对路径' AFTER sort_order");
        ensureColumn("resource", "file_count",
                "ALTER TABLE resource ADD COLUMN file_count INT DEFAULT 0 COMMENT '文件夹内文件数量' AFTER relative_path");
        ensureColumn("resource", "tags",
                "ALTER TABLE resource ADD COLUMN tags VARCHAR(128) DEFAULT NULL COMMENT '上传者自定义标签，逗号分隔' AFTER category_id");
        ensureIndex("resource", "idx_resource_parent_id",
                "CREATE INDEX idx_resource_parent_id ON resource(parent_id)");
    }

    private void ensureMediaEnhancementTable() {
        jdbcTemplate.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='media enhancement jobs'
                """);
    }

    private void ensureViewHistoryTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS view_history (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
                  user_id BIGINT NOT NULL COMMENT '用户ID',
                  resource_id BIGINT NOT NULL COMMENT '资源ID',
                  view_duration INT DEFAULT 0 COMMENT '浏览时长（秒）',
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间',
                  INDEX idx_view_history_user_id (user_id),
                  INDEX idx_view_history_resource_id (resource_id),
                  INDEX idx_view_history_create_time (create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户浏览历史表'
                """);
    }

    private void ensureNoteTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS note (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '笔记ID',
                  title VARCHAR(200) NOT NULL COMMENT '笔记标题',
                  content TEXT NOT NULL COMMENT '笔记内容（支持Markdown）',
                  category VARCHAR(50) DEFAULT NULL COMMENT '学习方向分类',
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记表'
                """);
        ensureNoteAnchorColumns();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS note_tag (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
                  name VARCHAR(50) NOT NULL COMMENT '标签名称',
                  user_id BIGINT NOT NULL COMMENT '创建者ID',
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  UNIQUE KEY uk_note_tag_user_name (user_id, name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记标签表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS note_tag_relation (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联ID',
                  note_id BIGINT NOT NULL COMMENT '笔记ID',
                  tag_id BIGINT NOT NULL COMMENT '标签ID',
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  UNIQUE KEY uk_note_tag_relation (note_id, tag_id),
                  INDEX idx_note_tag_relation_note_id (note_id),
                  INDEX idx_note_tag_relation_tag_id (tag_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记标签关联表'
                """);
        jdbcTemplate.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记分享表'
                """);
    }

    private void ensureFtpConnectionTable() {
        jdbcTemplate.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='FTP 远程连接配置'
                """);
    }

    private void ensureDanmakuSupport() {
        ensureColumn("resource", "danmaku_enabled",
                "ALTER TABLE resource ADD COLUMN danmaku_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用弹幕'");
        ensureColumn("resource", "danmaku_permission",
                "ALTER TABLE resource ADD COLUMN danmaku_permission VARCHAR(20) NOT NULL DEFAULT 'LOGGED' COMMENT '发送权限 EVERYONE/LOGGED/OWNER'");
        jdbcTemplate.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频弹幕'
                """);
        try {
            jdbcTemplate.execute("ALTER TABLE danmaku MODIFY COLUMN color VARCHAR(255) NOT NULL DEFAULT '#ffffff'");
        } catch (RuntimeException ignored) {
            // Fresh schemas already use the wider color column.
        }
    }

    private void ensureNoteAnchorColumns() {
        ensureColumn("note", "anchor_type",
                "ALTER TABLE note ADD COLUMN anchor_type VARCHAR(20) DEFAULT NULL COMMENT '笔记片段类型 VIDEO/TEXT/DOCUMENT/RESOURCE' AFTER resource_id");
        ensureColumn("note", "anchor_text",
                "ALTER TABLE note ADD COLUMN anchor_text TEXT DEFAULT NULL COMMENT '笔记记录的文本片段' AFTER anchor_type");
        ensureColumn("note", "anchor_image",
                "ALTER TABLE note ADD COLUMN anchor_image MEDIUMTEXT DEFAULT NULL COMMENT '笔记记录的视频截图或图片片段' AFTER anchor_text");
        ensureColumn("note", "anchor_seconds",
                "ALTER TABLE note ADD COLUMN anchor_seconds DECIMAL(10,3) DEFAULT NULL COMMENT '视频时间点（秒）' AFTER anchor_image");
    }

    private void ensureColumn(String tableName, String columnName, String alterSql) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND COLUMN_NAME = ?
                    """, Integer.class, tableName, columnName);
            if (count == null || count == 0) {
                jdbcTemplate.execute(alterSql);
            }
        } catch (RuntimeException ignored) {
            // Fresh schemas already include the column. This repair is best-effort for older MySQL databases.
        }
    }

    private void ensureIndex(String tableName, String indexName, String createSql) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND INDEX_NAME = ?
                    """, Integer.class, tableName, indexName);
            if (count == null || count == 0) {
                jdbcTemplate.execute(createSql);
            }
        } catch (RuntimeException ignored) {
            // Fresh schemas already include the index. This repair is best-effort for older MySQL databases.
        }
    }

    private void ensureColumnLengthAtLeast(String tableName, String columnName, int minLength, String alterSql) {
        try {
            Integer length = jdbcTemplate.queryForObject("""
                    SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND COLUMN_NAME = ?
                    """, Integer.class, tableName, columnName);
            if (length != null && length < minLength) {
                jdbcTemplate.execute(alterSql);
            }
        } catch (RuntimeException ignored) {
            // Fresh schemas already include the column. This repair is best-effort for older MySQL databases.
        }
    }

    private void ensureForumTables() {
        jdbcTemplate.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='forum posts'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS forum_post_resource (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  post_id BIGINT NOT NULL,
                  resource_id BIGINT NOT NULL,
                  folder_path VARCHAR(500) DEFAULT NULL,
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_forum_post_resource_post (post_id),
                  INDEX idx_forum_post_resource_resource (resource_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='forum linked resources'
                """);
        jdbcTemplate.execute("""
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='forum comments'
                """);
    }
}
