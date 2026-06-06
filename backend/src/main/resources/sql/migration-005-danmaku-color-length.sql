-- MySQL migration: widen danmaku.color to hold gradient specs.
USE learning_resource_platform;

ALTER TABLE danmaku MODIFY COLUMN color VARCHAR(255) NOT NULL DEFAULT '#ffffff';
