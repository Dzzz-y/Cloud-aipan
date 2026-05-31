```markdown
# 🏗️ DCloud AiPan 架构设计文档

## 1. 整体架构

本项目采用 **前后端分离 + 微服务萌芽** 的架构设计：

- **前端层**：原生 JavaScript SPA，通过 Node.js Express 做反向代理
- **业务后端**：Java Spring Boot，负责核心 CRUD、文件管理、分享、用户认证
- **AI 后端**：Python FastAPI + LangChain，负责 AI Agent 调度和 LLM 交互
- **数据层**：MySQL（业务数据）、Redis（缓存/会话）、MinIO（文件存储）、Milvus（向量检索）

## 2. 设计模式应用

| 模式 | 应用场景 | 位置 |
|------|----------|------|
| **策略模式** | 多存储引擎切换（MinIO / OSS / 本地） | `backend/.../component/StoreEngine.java` |
| **AOP 切面** | 分享码验证、统一日志 | `backend/.../aspect/ShareCodeAspect.java` |
| **拦截器链** | JWT 认证拦截 | `backend/.../interceptor/LoginInterceptor.java` |
| **Builder 模式** | LangChain Agent 构建 | `ai-agent/agents/*.py` |
| **模板方法** | MyBatis-Plus BaseMapper | `backend/.../mapper/*.java` |

## 3. 数据库设计（ER 图核心表）

- `account` — 用户账户
- `account_file` — 用户文件/文件夹树
- `file` — 物理文件元数据（MD5去重）
- `file_chunk` — 大文件分块上传追踪
- `share` / `share_file` — 分享管理
- `storage` — 用户存储配额
- `file_type` / `file_suffix` — 文件类型分类

## 4. AI Agent 架构

\`\`\`
用户输入 → Router 路由
              ├── /api/chat → ChatAgent（对话 + 网络搜索）
              ├── /api/document → DocAgent（文档抓取 + 摘要）
              └── /api/pan → PanAgent（SQL查询 + 自然语言）
                                   ↓
                          LangChain Agent Executor
                                   ↓
                     LLM (通义千问 Qwen-Turbo)
\`\`\`

## 5. 文件上传流程

\`\`\`
1. 客户端计算文件哈希 → 调用秒传检测接口
2. 如已存在 → 直接秒传成功
3. 如不存在：
   a. 小文件 (≤5MB) → 直接 POST 上传
   b. 大文件 (>5MB) → 分块上传
      - 初始化分块任务
      - 获取每块的预签名上传 URL
      - 并行 PUT 上传各分块
      - 全部完成后调用合并接口
\`\`\`
```