const express = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

const BACKEND_TARGET = process.env.BACKEND_URL || 'http://localhost:8080';
const AI_AGENT_TARGET = process.env.AI_AGENT_URL || 'http://127.0.0.1:8000';

console.log('========================================');
console.log('   DCloud AiPan 前端服务 (终极修复版)');
console.log('========================================');
console.log(`   前端端口  : ${PORT}`);
console.log(`   Java 后端 : ${BACKEND_TARGET}  (/api, /ai)`);
console.log(`   AI  智能体: ${AI_AGENT_TARGET}  (/ai-agent)`);
console.log('========================================\n');

// ==================== API 代理 ====================

// 1. AI 智能体代理 (Python FastAPI) 
app.use(createProxyMiddleware({
  target: AI_AGENT_TARGET,
  changeOrigin: true,
  pathFilter: '/ai-agent',
  on: {
    proxyReq: (proxyReq, req, res) => {
      // 备份最原始的请求 URL，供打印观察
      const originalUrl = req.url;
      

      proxyReq.path = proxyReq.path.replace(/^\/ai-agent/g, '');
      
      // 控制台增强打印日志，方便你用肉眼在黑窗口里100%确认转发路径
      console.log(`[AI-Agent Proxy] 拦截成功！`);
      console.log(` └─ 前端浏览器请求 : ${req.method} ${originalUrl}`);
      console.log(` └─ 真正送给 Python: ${proxyReq.path}`);
      console.log(` └─ 转发目标服务器 : ${AI_AGENT_TARGET}\n`);
    },
    error: (err, req, res) => {
      console.error(`[AI-Agent Error] ${req.url}:`, err.message);
    }
  }
}));

// 2. AI 聊天流 (Java Spring Boot) — 保留 /ai 前缀
app.use(createProxyMiddleware({
  target: BACKEND_TARGET,
  changeOrigin: true,
  pathFilter: '/ai',
  on: {
    proxyReq: (proxyReq, req) => {
      console.log(`[AI-Chat] ${req.method} ${req.url} -> ${BACKEND_TARGET}`);
    },
    error: (err, req, res) => {
      console.error(`[AI-Chat Error] ${req.url}:`, err.message);
    }
  }
}));

// 3. Java 后端 API 代理 — 保留 /api 前缀
app.use(createProxyMiddleware({
  target: BACKEND_TARGET,
  changeOrigin: true,
  pathFilter: '/api',
  on: {
    proxyReq: (proxyReq, req) => {
      console.log(`[API] ${req.method} ${req.url} -> ${BACKEND_TARGET}`);
    },
    error: (err, req, res) => {
      console.error(`[API Error] ${req.url}:`, err.message);
    }
  }
}));

// ==================== 静态文件 ====================
app.use(express.static(__dirname, {
  setHeaders: (res, filePath) => {
    if (filePath.endsWith('.html')) {
      res.setHeader('Cache-Control', 'no-cache');
    }
  }
}));

// SPA fallback - 非代理请求返回 index.html
app.get('*', (req, res) => {
  if (req.url.startsWith('/api') || req.url.startsWith('/ai') || req.url.startsWith('/ai-agent')) {
    return res.status(404).json({ error: 'Not found' });
  }
  res.sendFile(path.join(__dirname, 'index.html'));
});

// ==================== 启动 ====================
app.listen(PORT, () => {
  console.log(`\n 前端服务已启动: http://localhost:${PORT}\n`);
});