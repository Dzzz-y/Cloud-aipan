"""
向量服务 — 封装 Embedding 向量化 + Milvus 向量存储/检索

功能:
    1. 文档向量化并存入 Milvus
    2. 语义相似度检索
    3. 文档删除
    4. 批量操作

设计原则:
    - 所有 Milvus 操作失败时抛出明确异常，由调用方决定降级策略
    - 不依赖 FastAPI 上下文，可在任何地方使用
"""

import logging
from typing import List, Dict, Optional, Any

from langchain_core.documents import Document

from core.milvus_client import get_vector_store, is_knowledge_base_available

logger = logging.getLogger(__name__)


class VectorService:
    """向量存储与检索服务"""

    def __init__(self):
        self._store = get_vector_store()

    @property
    def is_available(self) -> bool:
        """知识库是否可用"""
        return self._store is not None

    def _ensure_available(self):
        """确保知识库可用，否则抛出异常"""
        if not self.is_available:
            raise RuntimeError("知识库不可用，请检查 Milvus 连接")

    # ──────────────────────────────────────────────
    # 1. 文档存储
    # ──────────────────────────────────────────────

    def add_documents(
        self,
        documents: List[Document],
    ) -> List[str]:
        """
        将文档列表向量化并存入 Milvus

        Args:
            documents: LangChain Document 列表，每个 Document 包含 page_content 和 metadata

        Returns:
            List[str]: 插入的文档 ID 列表

        Raises:
            RuntimeError: 知识库不可用时抛出
        """
        self._ensure_available()

        try:
            ids = self._store.add_documents(documents)
            logger.info(f"成功存入 {len(documents)} 个文档到知识库，IDs: {ids}")
            return ids
        except Exception as e:
            logger.error(f"存入文档到知识库失败: {e}")
            raise RuntimeError(f"文档写入失败: {e}") from e

    def add_texts(
        self,
        texts: List[str],
        metadatas: Optional[List[Dict[str, Any]]] = None,
    ) -> List[str]:
        """
        将纯文本列表向量化并存入 Milvus

        Args:
            texts: 文本列表
            metadatas: 每条文本对应的元数据（可选）

        Returns:
            List[str]: 插入的文本 ID 列表
        """
        self._ensure_available()

        try:
            ids = self._store.add_texts(texts, metadatas=metadatas)
            logger.info(f"成功存入 {len(texts)} 条文本到知识库")
            return ids
        except Exception as e:
            logger.error(f"存入文本到知识库失败: {e}")
            raise RuntimeError(f"文本写入失败: {e}") from e

    # ──────────────────────────────────────────────
    # 2. 语义检索
    # ──────────────────────────────────────────────

    def similarity_search(
        self,
        query: str,
        k: int = 4,
        filter_expr: Optional[str] = None,
    ) -> List[Document]:
        """
        语义相似度检索 — 返回最相关的文档

        Args:
            query: 查询文本
            k: 返回的文档数量
            filter_expr: Milvus 标量过滤表达式（可选），如 'metadata["source"] == "upload"'

        Returns:
            List[Document]: 相关文档列表，按相似度降序

        Raises:
            RuntimeError: 知识库不可用时抛出
        """
        self._ensure_available()

        try:
            search_kwargs = {"k": k}
            if filter_expr:
                search_kwargs["expr"] = filter_expr

            retriever = self._store.as_retriever(
                search_kwargs=search_kwargs,
            )
            docs = retriever.invoke(query)
            logger.info(f"检索查询 '{query[:50]}...' 返回 {len(docs)} 个文档")
            return docs
        except Exception as e:
            logger.error(f"语义检索失败: {e}")
            raise RuntimeError(f"检索失败: {e}") from e

    def similarity_search_with_score(
        self,
        query: str,
        k: int = 4,
    ) -> List[tuple]:
        """
        带相似度分数的语义检索

        Args:
            query: 查询文本
            k: 返回的文档数量

        Returns:
            List[tuple]: [(Document, score), ...] 分数越低越相关（余弦距离）
        """
        self._ensure_available()

        try:
            results = self._store.similarity_search_with_score(query, k=k)
            logger.info(
                f"带分数检索 '{query[:50]}...' 返回 {len(results)} 个结果"
            )
            return results
        except Exception as e:
            logger.error(f"带分数语义检索失败: {e}")
            raise RuntimeError(f"检索失败: {e}") from e

    # ──────────────────────────────────────────────
    # 3. 删除操作
    # ──────────────────────────────────────────────

    def delete_by_ids(self, ids: List[str]) -> bool:
        """
        按 ID 删除文档

        Args:
            ids: 要删除的文档 ID 列表

        Returns:
            bool: 是否成功
        """
        self._ensure_available()

        try:
            result = self._store.delete(ids=ids)
            logger.info(f"从知识库删除 {len(ids)} 个文档")
            return result
        except Exception as e:
            logger.error(f"删除文档失败: {e}")
            raise RuntimeError(f"删除失败: {e}") from e

    def clear_collection(self) -> bool:
        """
        清空整个知识库集合（危险操作）

        Returns:
            bool: 是否成功
        """
        self._ensure_available()

        try:
            from pymilvus import Collection
            from core.config import settings

            collection = Collection(settings.MILVUS_COLLECTION_NAME)
            collection.load()

            # 获取所有实体的 ID 并删除
            expr = "id >= 0"
            collection.delete(expr)
            collection.compact()
            logger.warning("知识库集合已清空")
            return True
        except Exception as e:
            logger.error(f"清空知识库失败: {e}")
            raise RuntimeError(f"清空失败: {e}") from e

    # ──────────────────────────────────────────────
    # 4. 对话记忆（语义化）
    # ──────────────────────────────────────────────

    def add_conversation_memory(
        self,
        account_id: str,
        user_message: str,
        assistant_message: str,
    ) -> bool:
        """
        将对话记录向量化存储，用于长期语义记忆

        Args:
            account_id: 用户 ID
            user_message: 用户消息
            assistant_message: 助手回复

        Returns:
            bool: 是否成功
        """
        if not self.is_available:
            return False

        try:
            full_text = f"用户: {user_message}\n助手: {assistant_message}"
            metadata = {
                "account_id": account_id,
                "type": "conversation_memory",
                "role_user": user_message[:200],
                "role_assistant": assistant_message[:200],
            }
            self.add_texts([full_text], metadatas=[metadata])
            logger.debug(f"用户 {account_id} 对话记忆已存入知识库")
            return True
        except Exception as e:
            logger.warning(f"存储对话记忆失败（不影响主流程）: {e}")
            return False

    def search_conversation_memory(
        self,
        account_id: str,
        query: str,
        k: int = 3,
    ) -> List[str]:
        """
        搜索与当前查询语义相关的历史对话

        Args:
            account_id: 用户 ID
            query: 当前查询（用于语义匹配）
            k: 返回的记忆数量

        Returns:
            List[str]: 相关的历史对话文本
        """
        if not self.is_available:
            return []

        try:
            filter_expr = f'metadata["account_id"] == "{account_id}" && metadata["type"] == "conversation_memory"'
            docs = self.similarity_search(query, k=k, filter_expr=filter_expr)
            return [doc.page_content for doc in docs]
        except Exception as e:
            logger.warning(f"搜索对话记忆失败（不影响主流程）: {e}")
            return []


# 模块级单例
_vector_service: Optional[VectorService] = None


def get_vector_service() -> VectorService:
    """获取 VectorService 单例"""
    global _vector_service
    if _vector_service is None:
        _vector_service = VectorService()
    return _vector_service
