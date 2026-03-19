# Memos Note

一款简洁优雅的 Android 笔记应用，使用 Jetpack Compose 构建，支持 Markdown 语法。

## 功能特性

### 核心功能
- **笔记管理** - 创建、编辑、删除笔记
- **Markdown 支持** - 完整的 Markdown 语法渲染
  - 标题 (H1-H6)
  - 粗体、斜体
  - 行内代码、代码块
  - 列表（有序/无序）
  - 引用块
  - 链接
- **标签系统** - 使用 `#标签名` 添加标签，支持点击筛选
- **文件存储** - 基于 Android SAF 的 Markdown 文件存储

### 交互体验
- **滑动手势** - 左滑删除，右滑编辑
- **搜索功能** - 快速搜索笔记内容
- **深色模式** - 莫兰迪配色，支持深浅主题切换
- **历史记录** - 记录最近打开的文件（最多 20 个）

### 视觉设计
- 莫兰迪色系卡片（10 种颜色自动轮换）
- 标签彩色渲染（6 种颜色）
- 流畅的动画效果

## 技术栈

| 技术 | 版本 |
|------|------|
| Kotlin | 1.9.20 |
| Jetpack Compose | BOM 2023.06.01 |
| Material 3 | - |
| Android Gradle Plugin | 8.2.0 |
| minSdk | 26 (Android 8.0) |
| targetSdk | 33 |

## 项目结构

```
app/src/main/java/com/memosnote/
├── MainActivity.kt           # 主界面与 Composable 组件
├── data/
│   ├── Memo.kt              # 笔记数据模型
│   ├── MemoParser.kt        # 笔记解析与序列化
│   ├── MemoRepository.kt    # 数据仓库（文件读写）
│   └── FileHistoryManager.kt # 历史文件管理
├── util/
│   └── MarkdownRenderer.kt  # Markdown 渲染引擎
└── ui/theme/
    ├── Color.kt             # 颜色定义
    └── Theme.kt             # 主题配置
```

## 数据存储

应用使用 Android Storage Access Framework (SAF) 存储数据：
- 数据以 Markdown 格式存储在用户选择的文件中
- 支持新建文件或打开现有文件
- 文件格式示例：

```markdown
---
2024-01-15 10:30
---

这是一条笔记内容，支持 **Markdown** 语法和 #标签

```

## 构建与运行

### 环境要求
- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 33

## Markdown 渲染示例

| 语法 | 渲染效果 |
|------|---------|
| `# 标题` | H1 标题 |
| `**粗体**` | **粗体** |
| `*斜体*` | *斜体* |
| `` `代码` `` | `代码` |
| `[链接](url)` | 可点击链接 |
| `- 列表项` | 无序列表 |
| `> 引用` | 引用块 |
| `#标签` | 彩色标签 |

## 贡献指南

欢迎提交 Issue 和 Pull Request！

## 许可证

MIT License
