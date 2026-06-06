USE learning_resource_platform;

ALTER TABLE resource
  ADD COLUMN resource_type VARCHAR(20) NOT NULL DEFAULT 'FILE' COMMENT '资源类型 FILE/FOLDER' AFTER file_type,
  ADD COLUMN parent_id BIGINT DEFAULT NULL COMMENT '父资源ID，文件夹内文件指向父文件夹' AFTER resource_type,
  ADD COLUMN sort_order INT DEFAULT 0 COMMENT '文件夹内排序' AFTER parent_id,
  ADD COLUMN relative_path VARCHAR(500) DEFAULT NULL COMMENT '批量上传时的相对路径' AFTER sort_order,
  ADD COLUMN file_count INT DEFAULT 0 COMMENT '文件夹内文件数量' AFTER relative_path;

CREATE INDEX idx_resource_parent_id ON resource(parent_id);
