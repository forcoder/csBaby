# 设计系统 — 客服小秘

## 产品背景
- **产品**: 客服小秘 — Android 智能客服自动回复 APP
- **用户**: 民宿/短租房东，需要高效管理客户沟通
- **品类**: 工具型 APP，竞品多为冷色/蓝色调客服工具
- **类型**: Android Jetpack Compose 应用

## 设计方向
- **方向**: 温暖极简 (Warm Minimal) — 亲和且专业
- **装饰**: 克制 — 圆角卡片 + 细微阴影，无多余装饰
- **用户记忆点**: "感觉很专业" — 民宿房东用起来靠谱、顺手
- **色彩策略**: 暖茶色主调，取代传统冷色客服工具

## 色彩系统

### 浅色模式

| Token | 色值 | 用途 |
|-------|------|------|
| `primary` | `#0D9488` Teal-600 | 主色 — 顶部栏、主要按钮、选中态 |
| `onPrimary` | `#FFFFFF` | 主色上的文字/图标 |
| `primaryContainer` | `#CCFBF1` | 标签、次要容器背景 |
| `secondary` | `#F59E0B` Amber-500 | 辅色 — 高亮标签、次要操作 |
| `onSecondary` | `#FFFFFF` | 辅色上的文字 |
| `background` | `#FFFAF5` Warm Paper | 页面背景 — 暖白柔和 |
| `surface` | `#FFFFFF` | 卡片/表面背景 |
| `onSurface` | `#1C1917` | 正文文字 |
| `onSurfaceVariant` | `#78716C` | 辅助文字 |
| `error` | `#E11D48` Rose-600 | 错误状态 |
| `surfaceVariant` | `#F5F5F4` | 搜索栏、输入框背景 |
| `outline` | `#D6D3D1` | 边框 |
| `outlineVariant` | `#E7E5E4` | 弱边框 |

### 深色模式
深色模式下饱和度降低 15-20%，暖感保留。主色变亮为 `#2DD4BF`，辅色为 `#FBBF24`。背景使用 `#0C0A09` 暖黑，而非纯黑。

## 字体
- **正文/UI**: 系统默认 (Roboto) — Android 平台原生字体
- **数字/数据**: Roboto tabular-nums
- Compose Material3 `Typography` 使用默认 scale，未覆盖

## 间距
- 基础单位: 4dp
- 密度: 舒适 (Comfortable)
- 卡片内边距: 16dp
- 列表项间距: 8dp
- 圆角: small(6dp), medium(10dp), large(14dp)

## 布局
- 底部导航 + Scaffold 结构
- 卡片式内容列表
- 顶部栏使用 primary 色背景，白色图标/文字
- 搜索栏使用 surfaceVariant 背景

## 动效
- Material3 默认过渡动画
- 无自定义动效

## 变更记录
| 日期 | 决策 | 理由 |
|------|------|------|
| 2026-06-18 | 从紫色(#6D28D9)改为暖茶(#0D9488) | 民宿行业暖色调更自然，与传统客服冷色工具区分 |
| 2026-06-18 | 辅色从青色改为琥珀色(#F59E0B) | 暖色系一致性，标签高亮更醒目 |
| 2026-06-18 | 背景改为暖白(#FFFAF5) | 替代纯白冷感，纸张质感更柔和 |
| 2026-06-18 | 状态栏白色(surface) | 现代风格，避免彩色状态栏视觉过载 |
