# 数据库说明

数据库脚本位于：

- `backend/src/main/resources/sql/schema.sql`
- `backend/src/main/resources/sql/data.sql`
- `backend/src/main/resources/sql/migration-001-file-type-length.sql`

核心表：

- `user`
- `category`
- `resource`
- `comment`
- `favorite`
- `download_record`

## Windows PowerShell 初始化

PowerShell 不能直接使用 `<` 输入重定向执行 MySQL 脚本，建议使用 `source`，并显式指定 `utf8mb4`，避免中文分类数据乱码或插入失败：

```powershell
mysql --default-character-set=utf8mb4 -u root -p -e "source E:/bishe/learning-resource-platform/backend/src/main/resources/sql/schema.sql"
mysql --default-character-set=utf8mb4 -u root -p -e "source E:/bishe/learning-resource-platform/backend/src/main/resources/sql/data.sql"
```

旧库升级时执行：

```powershell
mysql --default-character-set=utf8mb4 -u root -p -e "source E:/bishe/learning-resource-platform/backend/src/main/resources/sql/migration-001-file-type-length.sql"
```
