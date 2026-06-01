"""
知识库管理 API

端点:
    POST /api/knowledge/search   — 语义搜索知识库
    POST /api/knowledge/add/url  — 从 URL 添加文档
    POST /api/knowledge/add/text — 从文本添加文档
    POST /api/knowledge/ask      — RAG 问答（流式）
    GET  /api/knowledge/stats    — 知识库统计
"""

import logging
import asyncio

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from typing import Dict, Any

from core.auth import get_current_user
from core.exceptions import ApiException
from models.json_response import JsonData
from models.knowledge_schemas import (
    KnowledgeSearchRequest,
    KnowledgeAddUrlRequest,
    KnowledgeAddTextRequest,
    KnowledgeAskRequest,
)
from services.rag_service import get_rag_service

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/api/knowledge",
    tags=["RAG知识库"],
)


# ─────────────────────────────────────────────────────────
# 1. 知识库搜索
# ─────────────────────────────────────────────────────────

@router.post("/search")
async def search_knowledge(
    request: KnowledgeSearchRequest,
    current_user: Dict[str, Any] = Depends(get_current_user),
):
    """
    语义搜索知识库

    根据查询文本在向量知识库中进行语义相似度搜索，
    返回最相关的 k 个文档片段及其元数据。
    """
    account_id = current_user["account_id"]
    logger.info(f"用户 {account_id} 搜索知识库: '{request.query[:80]}...'")

    try:
        rag_service = get_rag_service()
        if not rag_service.is_available:
            return JsonData.error("知识库不可用，请检查 Milvus 连接状态", code=-1)

        docs = rag_service.search(request.query, k=request.k)

        results = []
        for doc in docs:
            results.append({
                "content": doc.page_content,
                "metadata": doc.metadata,
            })

        return JsonData.success({
            "query": request.query,
            "total": len(results),
            "results": results,
        })

    except Exception as e:
        logger.error(f"搜索知识库失败: {e}")
        raise ApiException(msg=f"搜索失败: {str(e)}")


# ─────────────────────────────────────────────────────────
# 2. 从 URL 添加文档
# ─────────────────────────────────────────────────────────

@router.post("/add/url")
async def add_knowledge_from_url(
    request: KnowledgeAddUrlRequest,
    current_user: Dict[str, Any] = Depends(get_current_user),
):
    """
    从 URL 获取文档内容并存入知识库

    支持 HTML 网页和 PDF 文件，文档会自动分块并向量化存储。
    """
    account_id = current_user["account_id"]
    logger.info(f"用户 {account_id} 从 URL 添加知识: {request.url}")

    try:
        rag_service = get_rag_service()
        if not rag_service.is_available:
            return JsonData.error("知识库不可用，请检查 Milvus 连接状态", code=-1)

        chunk_count = rag_service.ingest_url(request.url)

        return JsonData.success({
            "url": request.url,
            "chunks_stored": chunk_count,
            "message": f"成功摄入 {chunk_count} 个知识片段",
        })

    except Exception as e:
        logger.error(f"从 URL 添加知识失败: {e}")
        raise ApiException(msg=f"添加失败: {str(e)}")


# ─────────────────────────────────────────────────────────
# 3. 从文本添加文档
# ─────────────────────────────────────────────────────────

@router.post("/add/text")
async def add_knowledge_from_text(
    request: KnowledgeAddTextRequest,
    current_user: Dict[str, Any] = Depends(get_current_user),
):
    """
    将文本内容直接存入知识库

    适用于用户手动输入的知识或从其他来源获取的文本。
    """
    account_id = current_user["account_id"]
    title_preview = request.title or request.content[:50]
    logger.info(f"用户 {account_id} 从文本添加知识: '{title_preview}...'")

    try:
        rag_service = get_rag_service()
        if not rag_service.is_available:
            return JsonData.error("知识库不可用，请检查 Milvus 连接状态", code=-1)

        chunk_count = rag_service.ingest_text(
            content=request.content,
            source=request.source,
            title=request.title,
            metadata={"account_id": account_id},
        )

        return JsonData.success({
            "title": request.title or "未命名",
            "chunks_stored": chunk_count,
            "message": f"成功摄入 {chunk_count} 个知识片段",
        })

    except Exception as e:
        logger.error(f"从文本添加知识失败: {e}")
        raise ApiException(msg=f"添加失败: {str(e)}")


# ─────────────────────────────────────────────────────────
# 4. RAG 问答（流式）
# ─────────────────────────────────────────────────────────

async def _rag_ask_stream_generator(question: str):
    """RAG 问答流式生成器"""
    try:
        rag_service = get_rag_service()
        async for token in rag_service.ask_stream(question):
            response = JsonData.stream_data(token)
            yield f"data: {response.model_dump_json()}\n\n"
            await asyncio.sleep(0.01)

        yield "data: [DONE]\n\n"

    except Exception as e:
        logger.error(f"RAG 问答流式输出失败: {e}")
        error_response = JsonData.error(str(e))
        yield f"data: {error_response.model_dump_json()}\n\n"
        yield "data: [DONE]\n\n"


@router.post("/ask")
async def rag_ask(
    request: KnowledgeAskRequest,
    current_user: Dict[str, Any] = Depends(get_current_user),
):
    """
    RAG 问答 — 检索知识库后使用 LLM 增强回答

    流式模式下返回 SSE 事件流，非流式返回完整 JSON 响应。
    """
    account_id = current_user["account_id"]
    logger.info(f"用户 {account_id} RAG 问答: '{request.question[:80]}...'")

    rag_service = get_rag_service()
    if not rag_service.is_available:
        return JsonData.error("知识库不可用，请检查 Milvus 连接状态", code=-1)

    if request.stream:
        return StreamingResponse(
            _rag_ask_stream_generator(request.question),
            media_type="text/event-stream",
        )
    else:
        try:
            answer = await rag_service.ask(request.question)
            return JsonData.success({
                "question": request.question,
                "answer": answer,
            })
        except Exception as e:
            logger.error(f"RAG 问答失败: {e}")
            raise ApiException(msg=f"问答失败: {str(e)}")


# ─────────────────────────────────────────────────────────
# 5. 知识库统计
# ─────────────────────────────────────────────────────────

@router.get("/stats")
async def knowledge_stats(
    current_user: Dict[str, Any] = Depends(get_current_user),
):
    """
    获取知识库统计信息

    返回集合名称、文档总数、Embedding 模型等。
    """
    account_id = current_user["account_id"]
    logger.info(f"用户 {account_id} 查看知识库统计")

    from core.milvus_client import get_kb_stats

    stats = get_kb_stats()
    return JsonData.success(stats)
