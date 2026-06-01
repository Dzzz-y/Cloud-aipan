"""
RAG 知识库工具 — 供 LangChain Agent 使用的 Tool

工具列表:
    1. knowledge_search  — 搜索本地知识库
    2. knowledge_add_url — 从 URL 添加文档到知识库

设计原则:
    - 工具调用失败时返回错误信息字符串，不抛出异常（Agent 可以优雅处理）
    - 所有工具函数不需要 Agent 上下文，可以独立测试
"""

import logging
from typing import List

from langchain_core.tools import tool

from services.rag_service import get_rag_service
from core.milvus_client import is_knowledge_base_available

logger = logging.getLogger(__name__)


@tool("knowledge_search", return_direct=False)
def knowledge_search(query: str) -> str:
    """
    搜索本地知识库，获取与查询相关的文档内容。

    使用场景：
    - 用户询问关于已存储文档的信息
    - 需要从知识库中检索特定知识
    - 需要查找之前摄入的文档内容

    参数:
        query (str): 搜索查询，越具体越好。例如："微服务架构最佳实践"

    返回:
        str: 格式化后的相关知识，未找到时返回提示信息
    """
    try:
        if not is_knowledge_base_available():
            return "知识库当前不可用（Milvus 未连接）。请先检查向量数据库服务状态。"

        rag_service = get_rag_service()
        result = rag_service.search_formatted(query, k=4)
        logger.info(f"knowledge_search 工具调用成功: query='{query[:80]}...'")
        return result

    except Exception as e:
        logger.error(f"knowledge_search 工具调用失败: {e}")
        return f"搜索知识库时出错: {str(e)}"


@tool("knowledge_add_url", return_direct=False)
def knowledge_add_url(url: str) -> str:
    """
    从 URL 获取文档内容并存入知识库。

    使用场景：
    - 用户要求将某个网页/PDF 文档加入知识库
    - 用户要求"记住"或"学习"某个在线文档

    参数:
        url (str): 文档的完整 URL 地址（支持 HTML 网页和 PDF 文件）

    返回:
        str: 摄入结果，包含存入的文档块数量
    """
    try:
        if not is_knowledge_base_available():
            return "知识库当前不可用（Milvus 未连接），无法添加文档。"

        rag_service = get_rag_service()
        chunk_count = rag_service.ingest_url(url)
        logger.info(f"knowledge_add_url 工具调用成功: url='{url}', chunks={chunk_count}")
        return f"✅ 成功将文档加入知识库！共处理为 {chunk_count} 个知识片段。来源: {url}"

    except Exception as e:
        logger.error(f"knowledge_add_url 工具调用失败: {e}")
        return f"添加文档到知识库时出错: {str(e)}"


@tool("knowledge_stats", return_direct=False)
def knowledge_stats(query: str = "") -> str:
    """
    查看知识库的统计信息，包括文档总数、集合名称等。

    参数:
        query (str): 占位参数，可传空字符串

    返回:
        str: 知识库统计信息
    """
    try:
        from core.milvus_client import get_kb_stats

        stats = get_kb_stats()
        if not stats.get("available"):
            return f"知识库不可用: {stats.get('message', '未知错误')}"

        return (
            f"📊 知识库统计:\n"
            f"  - 集合名称: {stats['collection_name']}\n"
            f"  - 文档片段总数: {stats['total_documents']}\n"
            f"  - Embedding 模型: {stats['embedding_model']}\n"
            f"  - 向量维度: {stats['dimension']}"
        )
    except Exception as e:
        logger.error(f"knowledge_stats 工具调用失败: {e}")
        return f"获取知识库统计时出错: {str(e)}"


def get_rag_tools() -> List:
    """
    获取所有 RAG 工具列表

    Returns:
        List[Tool]: RAG 工具列表，可直接传入 create_chat_agent()
    """
    tools = [
        knowledge_search,
        knowledge_add_url,
        knowledge_stats,
    ]
    return tools
