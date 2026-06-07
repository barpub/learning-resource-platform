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

INSERT INTO ftp_connection (name, host, port, username, password_cipher, passive_mode, encoding, home_path, description, status)
SELECT 'Public Demo FTP', 'test.rebex.net', 21, 'demo', NULL, 1, 'UTF-8', '/', '公共测试 FTP（只读），用于演示远程浏览', 1
WHERE NOT EXISTS (SELECT 1 FROM ftp_connection WHERE name = 'Public Demo FTP');

INSERT INTO resource (title, description, file_name, file_path, file_size, file_type, resource_type, category_id, tags, user_id, download_count, view_count, rating, rating_count, status)
SELECT 'Learning Resource Platform Thesis Demo',
       'Demo resource for local search and Agent understanding in H2 mode.',
       'learning-resource-platform-thesis.txt',
       'src/main/resources/demo/demo-search.txt',
       480,
       'text/plain',
       'FILE',
       5,
       '论文,演示,Agent',
       1,
       12,
       45,
       4.80,
       5,
       1
WHERE NOT EXISTS (SELECT 1 FROM resource WHERE title = 'Learning Resource Platform Thesis Demo');
