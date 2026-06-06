USE learning_resource_platform;

INSERT INTO category (name, description, parent_id, sort_order)
SELECT '课程课件', '课程 PPT、讲义等课件资源', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '课程课件');

INSERT INTO category (name, description, parent_id, sort_order)
SELECT '教学视频', '课堂录播、知识点讲解视频', 0, 2
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '教学视频');

INSERT INTO category (name, description, parent_id, sort_order)
SELECT '电子书籍', '电子教材、参考书和学习读物', 0, 3
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '电子书籍');

INSERT INTO category (name, description, parent_id, sort_order)
SELECT '习题资料', '试题、作业和练习资料', 0, 4
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '习题资料');

INSERT INTO category (name, description, parent_id, sort_order)
SELECT '其他资源', '其他学习资源', 0, 5
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '其他资源');
