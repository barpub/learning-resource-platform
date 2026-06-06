# 学习资源共享平台用例图

## PlantUML 标准版本

```plantuml
@startuml
left to right direction
skinparam packageStyle rectangle

actor "游客" as Visitor
actor "普通用户" as User
actor "管理员" as Admin

rectangle "学习资源共享平台" {
  usecase "浏览首页" as UC_Home
  usecase "搜索资源" as UC_Search
  usecase "按分类浏览资源" as UC_Category
  usecase "查看资源详情" as UC_Detail
  usecase "在线预览资源" as UC_Preview
  usecase "注册账号" as UC_Register
  usecase "登录系统" as UC_Login

  usecase "上传资源" as UC_Upload
  usecase "批量上传文件/文件夹" as UC_BatchUpload
  usecase "创建子文件夹" as UC_CreateFolder
  usecase "管理目录树" as UC_TreeManage
  usecase "修改资源信息" as UC_EditResource
  usecase "移动资源位置" as UC_MoveResource
  usecase "删除自己上传的资源" as UC_DeleteOwn
  usecase "下载资源" as UC_Download
  usecase "收藏资源" as UC_Favorite
  usecase "取消收藏" as UC_Unfavorite
  usecase "分享资源链接" as UC_Share
  usecase "评论资源" as UC_Comment
  usecase "查看我的工作台" as UC_Mine
  usecase "查看上传统计" as UC_UploadStats
  usecase "查看浏览统计" as UC_ViewStats
  usecase "查看收藏统计" as UC_FavoriteStats
  usecase "查看下载趋势" as UC_DownloadTrend
  usecase "管理我的收藏" as UC_ManageFavorites

  usecase "审核资源" as UC_AuditResource
  usecase "管理全部资源" as UC_ManageResources
  usecase "删除任意资源" as UC_DeleteAny
  usecase "管理用户" as UC_ManageUsers
  usecase "启用/禁用用户" as UC_UserStatus
  usecase "管理分类" as UC_ManageCategories
  usecase "管理评论" as UC_ManageComments
}

Visitor --> UC_Home
Visitor --> UC_Search
Visitor --> UC_Category
Visitor --> UC_Detail
Visitor --> UC_Preview
Visitor --> UC_Register
Visitor --> UC_Login

User --> UC_Home
User --> UC_Search
User --> UC_Category
User --> UC_Detail
User --> UC_Preview
User --> UC_Upload
User --> UC_Download
User --> UC_Favorite
User --> UC_Unfavorite
User --> UC_Share
User --> UC_Comment
User --> UC_Mine

Admin --|> User
Admin --> UC_AuditResource
Admin --> UC_ManageResources
Admin --> UC_ManageUsers
Admin --> UC_ManageCategories
Admin --> UC_ManageComments

UC_BatchUpload ..> UC_Upload : <<extend>>
UC_CreateFolder ..> UC_Upload : <<extend>>
UC_TreeManage ..> UC_Upload : <<include>>
UC_EditResource ..> UC_TreeManage : <<include>>
UC_MoveResource ..> UC_TreeManage : <<include>>
UC_DeleteOwn ..> UC_TreeManage : <<include>>

UC_Mine ..> UC_UploadStats : <<include>>
UC_Mine ..> UC_ViewStats : <<include>>
UC_Mine ..> UC_FavoriteStats : <<include>>
UC_Mine ..> UC_DownloadTrend : <<include>>
UC_Mine ..> UC_ManageFavorites : <<include>>

UC_ManageResources ..> UC_AuditResource : <<include>>
UC_ManageResources ..> UC_DeleteAny : <<include>>
UC_ManageUsers ..> UC_UserStatus : <<include>>
@enduml
```

## Mermaid 预览版本

Mermaid 对 UML 用例图的支持在不同编辑器里不完全一致。下面使用 `flowchart` 近似表达同一组参与者和用例，适合在只支持 Mermaid 的 Markdown 环境中预览。

```mermaid
flowchart LR
  Visitor[游客]
  User[普通用户]
  Admin[管理员]

  subgraph System[学习资源共享平台]
    UC_Home([浏览首页])
    UC_Search([搜索资源])
    UC_Category([按分类浏览资源])
    UC_Detail([查看资源详情])
    UC_Preview([在线预览资源])
    UC_Register([注册账号])
    UC_Login([登录系统])

    UC_Upload([上传资源])
    UC_BatchUpload([批量上传文件/文件夹])
    UC_CreateFolder([创建子文件夹])
    UC_TreeManage([管理目录树])
    UC_EditResource([修改资源信息])
    UC_MoveResource([移动资源位置])
    UC_DeleteOwn([删除自己上传的资源])
    UC_Download([下载资源])
    UC_Favorite([收藏资源])
    UC_Unfavorite([取消收藏])
    UC_Share([分享资源链接])
    UC_Comment([评论资源])
    UC_Mine([查看我的工作台])
    UC_UploadStats([查看上传统计])
    UC_ViewStats([查看浏览统计])
    UC_FavoriteStats([查看收藏统计])
    UC_DownloadTrend([查看下载趋势])
    UC_ManageFavorites([管理我的收藏])

    UC_AuditResource([审核资源])
    UC_ManageResources([管理全部资源])
    UC_DeleteAny([删除任意资源])
    UC_ManageUsers([管理用户])
    UC_UserStatus([启用/禁用用户])
    UC_ManageCategories([管理分类])
    UC_ManageComments([管理评论])
  end

  Visitor --> UC_Home
  Visitor --> UC_Search
  Visitor --> UC_Category
  Visitor --> UC_Detail
  Visitor --> UC_Preview
  Visitor --> UC_Register
  Visitor --> UC_Login

  User --> UC_Home
  User --> UC_Search
  User --> UC_Category
  User --> UC_Detail
  User --> UC_Preview
  User --> UC_Upload
  User --> UC_Download
  User --> UC_Favorite
  User --> UC_Unfavorite
  User --> UC_Share
  User --> UC_Comment
  User --> UC_Mine

  Admin -.继承.-> User
  Admin --> UC_AuditResource
  Admin --> UC_ManageResources
  Admin --> UC_ManageUsers
  Admin --> UC_ManageCategories
  Admin --> UC_ManageComments

  UC_BatchUpload -.extend.-> UC_Upload
  UC_CreateFolder -.extend.-> UC_Upload
  UC_TreeManage -.include.-> UC_Upload
  UC_EditResource -.include.-> UC_TreeManage
  UC_MoveResource -.include.-> UC_TreeManage
  UC_DeleteOwn -.include.-> UC_TreeManage

  UC_Mine -.include.-> UC_UploadStats
  UC_Mine -.include.-> UC_ViewStats
  UC_Mine -.include.-> UC_FavoriteStats
  UC_Mine -.include.-> UC_DownloadTrend
  UC_Mine -.include.-> UC_ManageFavorites

  UC_ManageResources -.include.-> UC_AuditResource
  UC_ManageResources -.include.-> UC_DeleteAny
  UC_ManageUsers -.include.-> UC_UserStatus
```

## 角色说明

| 角色 | 说明 |
| --- | --- |
| 游客 | 未登录用户，可以浏览、搜索、查看详情和在线预览公开资源，也可以注册或登录。 |
| 普通用户 | 登录后的用户，可以上传资源、管理自己上传的文件夹和目录树、收藏、评论、下载、分享资源，并查看个人统计工作台。 |
| 管理员 | 拥有普通用户能力，同时可以审核资源、管理全部资源、管理用户、管理分类和管理评论。 |

## 核心用例说明

| 用例 | 说明 |
| --- | --- |
| 上传资源 | 用户上传课程文件、教学视频、文档、图片等资源。 |
| 批量上传文件/文件夹 | 用户可以选择多个文件或整个文件夹上传，系统按目录结构聚合。 |
| 管理目录树 | 用户可以在可视化目录树中创建子文件夹、修改、移动、删除自己上传的文件或文件夹。 |
| 在线预览资源 | 系统支持常见文件类型预览，不支持预览的文件仅提供上传、下载和管理能力。 |
| 查看我的工作台 | 用户集中查看自己的上传、浏览、收藏、下载趋势等统计信息。 |
| 分享资源链接 | 用户可以在资源卡片或资源详情页复制/分享资源访问链接。 |
| 审核资源 | 管理员对用户上传的资源进行审核和状态管理。 |
| 管理全部资源 | 管理员可以查看、审核、删除平台内所有资源。 |
