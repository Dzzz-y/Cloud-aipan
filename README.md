```markdown
# 🤖 DCloud AiPan — AI 智能云盘系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115-blue.svg)](https://fastapi.tiangolo.com/)
[![LangChain](https://img.shields.io/badge/LangChain-0.2-orange.svg)](https://www.langchain.com/)
[![Vue.js](https://img.shields.io/badge/JavaScript-ES6-yellow.svg)](https://developer.mozilla.org/en-US/docs/Web/JavaScript)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

> 一款融合 AI 大模型能力的全栈智能网盘应用。支持文件管理、AI 智能搜索、文档摘要、自然语言对话等功能。

## ✨ 核心功能

### 📁 文件管理
- **文件 CRUD** — 上传、下载、删除、重命名、移动、复制
- **大文件分块上传** — 支持断点续传，秒传检测
- **文件夹树管理** — 无限层级文件夹
- **回收站** — 30天有效期，支持恢复和彻底删除
- **多种视图** — 图片网格 + 文件列表双模式

### 🔗 文件分享
- **公开分享** — 生成分享链接，无需提取码
- **加密分享** — 设置提取码保护
- **有效期管理** — 永久/7天/30天可选
- **转存功能** — 将分享文件保存到自己的网盘

### 🤖 AI 智能功能
- **AI 对话助手** — 基于通义千问大模型，支持上下文记忆
- **智能网盘查询** — 自然语言搜索文件："帮我找上周上传的图片"
- **文档智能摘要** — 输入URL，自动抓取并生成摘要（支持HTML/PDF）
- **流式响应** — SSE 实时流式输出，体验流畅

### 👤 用户系统
- **注册/登录** — JWT 认证
- **个人中心** — 存储空间管理
- **权限控制** — 拦截器 + AOP 双重验证

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────┐
│                    前端 (Frontend)                │
│          原生 JavaScript SPA + Node.js            │
│          Express 反向代理 + SSE 流式通信           │
└────────────┬────────────────────┬────────────────┘
             │                    │
    ┌────────▼────────┐  ┌───────▼────────┐
    │  Java 后端 (8080)│  │ Python AI (8000)│
    │  Spring Boot 3  │  │    FastAPI      │
    │  MyBatis-Plus   │  │   LangChain     │
    │  JWT 认证       │  │   Agent 调度     │
    └───────┬─────────┘  └───────┬─────────┘
            │                    │
    ┌───────▼────────────────────▼─────────┐
    │           数据 & 基础设施层             │
    │  MySQL · Redis · MinIO · Milvus      │
    │       阿里云 DashScope (通义千问)      │
    └──────────────────────────────────────┘
```

### 技术栈详情

| 层级 | 技术 | 版本 |
|------|------|------|
| **前端** | 原生 JavaScript + HTML5 + CSS3 | — |
| **前端服务器** | Node.js + Express | 4.21 |
| **后端框架** | Spring Boot 3 | 3.2.4 |
| **ORM** | MyBatis-Plus | 3.5.6 |
| **数据库** | MySQL | 8.0 |
| **缓存** | Redis | 7.x |
| **对象存储** | MinIO (S3 协议) | — |
| **AI 后端** | FastAPI + LangChain | 0.115 |
| **LLM** | 阿里云通义千问 (Qwen-Turbo) | — |
| **向量数据库** | Milvus | 2.5 |
| **认证** | JWT (jjwt 0.12.3) | — |
| **API 文档** | Knife4j (OpenAPI 3.0) | 4.4 |

## 📂 项目结构

```
dcloud-aipan/
├── backend/                # Java Spring Boot 后端
│   ├── src/main/java/      # Java 源码
│   │   └── net/aipan/dcloud_aipan/
│   │       ├── controller/ # REST API 控制器
│   │       ├── service/    # 业务逻辑层
│   │       ├── mapper/     # MyBatis 数据访问层
│   │       ├── model/      # 数据库实体
│   │       ├── dto/        # 数据传输对象
│   │       ├── config/     # 配置类
│   │       ├── interceptor/# 拦截器（认证）
│   │       ├── aspect/     # AOP 切面（分享码验证）
│   │       └── util/       # 工具类
│   ├── src/main/resources/ # 配置 & Mapper XML
│   └── pom.xml             # Maven 依赖
│
├── ai-agent/               # Python AI Agent 后端
│   ├── app/main.py         # FastAPI 入口
│   ├── routers/            # API 路由层
│   ├── services/           # 业务逻辑层
│   ├── agents/             # LangChain Agent 定义
│   ├── tools/              # Agent 工具集
│   ├── core/               # 核心配置/认证/LLM
│   └── models/             # Pydantic 数据模型
│
├── frontend/               # 前端
│   ├── js/                 # JavaScript 模块
│   │   ├── api.js          # API 服务层
│   │   ├── files.js        # 文件管理模块
│   │   ├── share.js        # 分享管理模块
│   │   ├── ai.js           # AI 交互模块
│   │   ├── auth.js         # 认证模块
│   │   └── app.js          # 应用入口
│   ├── css/style.css       # 样式表 (~1000行)
│   ├── index.html          # 单页入口
│   └── server.js           # Node.js 开发服务器
│
├── database/               # 数据库脚本
│   └── init.sql            # 建表语句
│
└── docs/                   # 文档
    ├── architecture.md     # 架构设计
    ├── api-guide.md        # API 接口文档
    └── screenshots/        # 截图
```

## 🚀 快速开始

### 环境要求

- **JDK 21+**
- **Python 3.10+**
- **Node.js 18+**
- **MySQL 8.0+**
- **Redis 7+**
- **MinIO** (或兼容 S3 的对象存储)
- **Maven 3.8+**

### 1. 克隆项目

```bash
git clone https://github.com/Dzzz-y/Dcloud-aipan.git
cd dcloud-aipan
```

### 2. 初始化数据库

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS app_db DEFAULT CHARACTER SET utf8mb4;"

# 导入表结构
mysql -u root -p app_db < database/init.sql
```

### 3. 配置环境变量

**Java 后端：**
```bash
cd backend
# 复制配置模板
cp src/main/resources/application-example.yaml src/main/resources/application.yaml
# 编辑 application.yaml，填入你的数据库/Redis/MinIO 连接信息
```

**Python AI 后端：**
```bash
cd ai-agent
# 复制环境变量模板
cp .env.example .env
# 编辑 .env，填入你的 API Key 和连接信息
```

### 4. 启动服务

**启动 Java 后端（端口 8080）：**
```bash
cd backend
./mvnw spring-boot:run
```

**启动 Python AI 后端（端口 8000）：**
```bash
cd ai-agent
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

**启动前端（端口 3000）：**
```bash
cd frontend
npm install
npm start
```

### 5. 访问系统

- 🌐 **前端界面：** http://localhost:3000
- 📘 **API 文档：** http://localhost:8080/doc.html
- 🤖 **AI Agent 文档：** http://localhost:8000/docs


1. **全栈能力** — 从前端到后端，从 Java 到 Python，覆盖完整技术栈
2. **AI 集成** — 不只是 CRUD，真正将大模型融入业务场景
3. **架构设计** — 策略模式（多存储引擎）、AOP 切面（分享码验证）、SSE 流式通信
4. **工程化** — MyBatis-Plus 代码生成、Maven Wrapper、配置文件模板化
5. **大文件处理** — 分块上传、断点续传、秒传检测
6. **安全设计** — JWT 双令牌（用户+分享）、AOP 权限验证、CORS 配置

## 📝 开发日志

详细的开发过程和设计决策请参阅 [docs/architecture.md](docs/architecture.md)。

## 📄 开源协议

本项目采用 [MIT License](LICENSE)。

---
