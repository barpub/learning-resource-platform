# API 文档

所有接口返回统一结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

认证接口：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`

用户接口：

- `GET /api/users/profile`
- `PUT /api/users/profile`
- `PUT /api/users/password`
- `GET /api/users`
- `PUT /api/users/{id}/status`
- `DELETE /api/users/{id}`

资源接口：

- `GET /api/resources`
- `GET /api/resources/search`
- `GET /api/resources/{id}`
- `POST /api/resources`
- `PUT /api/resources/{id}`
- `DELETE /api/resources/{id}`
- `GET /api/resources/{id}/download`

分类接口：

- `GET /api/categories`
- `GET /api/categories/{id}`
- `POST /api/categories`
- `PUT /api/categories/{id}`
- `DELETE /api/categories/{id}`

评论接口：

- `GET /api/comments`
- `POST /api/comments`
- `DELETE /api/comments/{id}`
- `GET /api/resources/{id}/comments`

收藏接口：

- `GET /api/favorites`
- `POST /api/favorites`
- `DELETE /api/favorites/{id}`
- `DELETE /api/favorites?resourceId={resourceId}`
