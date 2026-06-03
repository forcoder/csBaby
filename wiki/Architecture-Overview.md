# Architecture Overview

## System Architecture

csBaby采用Clean Architecture + MVVM模式构建，分为五层：

```
┌─────────────────────────────────────────────┐
│         PRESENTATION LAYER                  │
│  (Activities, ViewModels, Compose UI)      │
├─────────────────────────────────────────────┤
│           DOMAIN LAYER                       │
│  (Entities, UseCases, Repository Interfaces)│
├─────────────────────────────────────────────┤
│            DATA LAYER                        │
│  (Repositories, DAOs, Remote APIs)         │
├─────────────────────────────────────────────┤
│       INFRASTRUCTURE LAYER                   │
│  (Notification, AI, Knowledge, Sync)        │
├─────────────────────────────────────────────┤
│         DEPENDENCY INJECTION                 │
│  (Hilt Modules)                             │
└─────────────────────────────────────────────┘
```

## Package Structure

```
com.csbaby.kefu
├── presentation/      # 展示层
│   ├── screens/       # UI页面
│   ├── navigation/     # 导航
│   └── theme/         # 主题
├── domain/           # 领域层
│   ├── model/        # 领域模型
│   └── repository/   # 仓储接口
├── data/             # 数据层
│   ├── local/        # 本地数据库
│   ├── remote/       # 远程API
│   └── repository/   # 仓储实现
├── infrastructure/   # 基础设施
│   ├── notification/ # 通知服务
│   ├── ai/          # AI服务
│   └── sync/        # 同步服务
└── di/              # 依赖注入
```

## Core Components

### Message Flow
1. [[Notification Service]] 检测新消息
2. [[Knowledge Base]] 进行关键词匹配
3. 匹配成功 → 应用规则回复
4. 匹配失败 → [[AI Integration]] 生成回复
5. [[Reply Generator]] 生成回复内容
6. 显示浮动窗口供用户确认
7. 用户确认后发送
8. 记录到 [[Reply Generator]] 历史
9. [[Style Learning]] 学习用户风格

### Data Flow
- [[Database Schema]] 存储本地数据
- [[Sync Protocol]] 与云端同步
- [[Repository Pattern]] 封装数据访问

## Related
- [[Clean Architecture]]
- [[MVVM Pattern]]
- [[OTA Update]]
- [[Cloud Sync]]