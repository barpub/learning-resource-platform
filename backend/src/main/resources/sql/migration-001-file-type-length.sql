USE learning_resource_platform;

ALTER TABLE resource
  MODIFY COLUMN file_type VARCHAR(128) NOT NULL COMMENT '文件类型';
