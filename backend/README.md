# Learning Resource Platform Backend

## Run

1. Create MySQL schema:

```powershell
mysql -u root -p < src/main/resources/sql/schema.sql
mysql -u root -p learning_resource_platform < src/main/resources/sql/data.sql
```

2. Start backend:

```powershell
mvn spring-boot:run
```

Default accounts are seeded on first startup:

- Admin: `admin / admin123456`
- User: `user / user123456`
