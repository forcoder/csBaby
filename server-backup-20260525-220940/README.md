# 客服小秘云端同步服务端

## 部署到 Render

1. 在 Render 创建新的 Web Service
2. 连接到包含此 `server/` 目录的 Git 仓库
3. 设置：
   - **Runtime**: Node
   - **Build Command**: `cd server && npm install`
   - **Start Command**: `cd server && npm start`
   - **Environment Variables**:
     - `NODE_ENV`: `production`
     - `JWT_SECRET`: （自动生成或自定义）
     - `PORT`: `8080`

## API 接口

### 认证
- `POST /auth/register` - 注册
- `POST /auth/login` - 登录
- `POST /auth/refresh` - 刷新 Token

### 同步（需 Bearer Token）
- `GET /sync/all?tenantId=` - 全量同步
- `GET /sync/changes?tenantId=&since=` - 增量同步
- `POST /sync/push` - 推送变更
- `POST /sync/resolve` - 冲突解决

## 数据库

使用 sql.js（纯 JS SQLite），数据保存在 `data.db` 文件中。
Render 的磁盘挂载在 `/opt/render/project/src`，确保数据持久化。
