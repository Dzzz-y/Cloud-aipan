"""
RAG 知识库服务 — 使用 LangChain LCEL 构建检索增强生成管道

核心 LCEL 管道:
    ┌──────────────────────────────────────────────────────┐
    │  retriever.invoke(query) → [Doc1, Doc2, ...]        │
    │         ↓                                            │
    │  format_docs(docs) → "相关上下文..."                   │
    │         ↓                                            │
    │  {"context": formatted_docs, "question": query}       │
    │         ↓                                            │
    │  ChatPromptTemplate → Messages                        │
    │         ↓                                            │
    │  ChatOpenAI → AIMessage                               │
    │         ↓                                            │
    │  StrOutputParser → str                                │
    └──────────────────────────────────────────────────────┘

设计原则:
    - LCEL 用于 RAG 检索管道（RunnableSequence / | 操作符）
    - 不影响现有的 AgentExecutor 聊天流程
    - 可作为独立服务被 Agent 工具或 API 端点调用
"""

import logging
from typing import List, Optional, AsyncIterator, Dict, Any

from langchain_core.documents import Document
from langchain_core.runnables import RunnablePassthrough, RunnableLambda
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_text_splitters import RecursiveCharacterTextSplitter

from core.config import settings
from core.llm import get_default_llm
from core.milvus_client import is_knowledge_base_available
from services.vector_service import VectorService, get_vector_service

logger = logging.getLogger(__name__)


# ─────────────────────────────────────────────────────────
# 文档格式化器（LCEL 管道中的一环）
# ─────────────────────────────────────────────────────────

def format_docs(docs: List[Document]) -> str:
    """
    将检索到的文档列表格式化为 LLM 可读的上下文字符串

    这是 LCEL 管道中的关键转换环节:
        retriever | format_docs → 将 [Document, ...] 转为 str
    """
    if not docs:
        return "（未找到相关知识）"

    formatted_parts = []
    for i, doc in enumerate(docs, 1):
        source = doc.metadata.get("source", "未知来源")
        title = doc.metadata.get("title", "")
        header = f"[知识片段 {i}]"
        if title:
            header += f" 标题: {title}"
        header += f" 来源: {source}"
        formatted_parts.append(f"{header}\n{doc.page_content}")

    return "\n\n---\n\n".join(formatted_parts)


# ─────────────────────────────────────────────────────────
# RAG 服务
# ─────────────────────────────────────────────────────────

class RAGService:
    """
    RAG 知识库服务

    提供三个层次的能力:
        1. 文档摄入:   URL/文本 → 分块 → 向量化 → Milvus 存储
        2. 知识检索:   查询 → 向量化 → Milvus 相似搜索 → 相关文档
        3. RAG 问答:   查询 → 检索 + LCEL 管道 → LLM 增强回答
    """

    def __init__(self):
        self.vector_service: VectorService = get_vector_service()
        self.llm = get_default_llm()

        # 文本分割器：用于文档摄入时分块
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000,
            chunk_overlap=200,
            separators=["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""],
            add_start_index=True,
        )

    @property
    def is_available(self) -> bool:
        return self.vector_service.is_available

    # ═══════════════════════════════════════════════════════
    # 1. 文档摄入
    # ═══════════════════════════════════════════════════════

    def ingest_text(
        self,
        content: str,
        source: str = "manual",
        title: str = "",
        metadata: Optional[Dict[str, Any]] = None,
    ) -> int:
        """
        将文本内容分块、向量化并存入知识库

        Args:
            content:  要摄入的文本内容
            source:   来源标识（manual / url / upload）
            title:    文档标题
            metadata: 额外的元数据

        Returns:
            int: 存入的文档块数量

        Raises:
            RuntimeError: 知识库不可用或摄入失败
        """
        if not self.is_available:
            raise RuntimeError("知识库不可用，无法摄入文档")

        # 1. 分块
        chunks = self.text_splitter.split_text(content)
        logger.info(f"文本分块完成: {len(chunks)} 个块 (source={source})")

        if not chunks:
            return 0

        # 2. 构建 Document 对象
        base_meta = {
            "source": source,
            "title": title,
            "chunk_count": len(chunks),
        }
        if metadata:
            base_meta.update(metadata)

        documents = []
        for i, chunk in enumerate(chunks):
            doc_meta = {
                **base_meta,
                "chunk_index": i,
            }
            documents.append(Document(page_content=chunk, metadata=doc_meta))

        # 3. 向量化并存入 Milvus
        ids = self.vector_service.add_documents(documents)
        logger.info(f"文档摄入完成: {len(ids)} 个向量已存储, title='{title}'")
        return len(ids)

    def ingest_url(self, url: str) -> int:
        """
        从 URL 获取文档内容并摄入知识库

        Args:
            url: 文档 URL

        Returns:
            int: 存入的文档块数量
        """
        from tools.document_tools import DocumentTools

        logger.info(f"开始从 URL 摄入文档: {url}")
        doc_result = DocumentTools.fetch_document(url)

        if doc_result.get("type") == "error":
            raise RuntimeError(f"获取文档失败: {doc_result.get('content')}")

        title = doc_result.get("title", "")
        content = doc_result.get("content", "")

        if not content or len(content) < 50:
            raise RuntimeError("文档内容过短或为空，无法摄入")

        return self.ingest_text(
            content=content,
            source=url,
            title=title,
            metadata={"type": doc_result.get("type", "unknown"), "url": url},
        )

    # ═══════════════════════════════════════════════════════
    # 2. 知识检索（纯检索，不含 LLM）
    # ═══════════════════════════════════════════════════════

    def search(
        self,
        query: str,
        k: int = 4,
    ) -> List[Document]:
        """
        语义搜索知识库，返回相关文档

        Args:
            query: 查询文本
            k:     返回文档数

        Returns:
            List[Document]: 相关文档列表
        """
        return self.vector_service.similarity_search(query, k=k)

    def search_formatted(self, query: str, k: int = 4) -> str:
        """
        搜索知识库并返回格式化后的文本——供 Agent Tool 直接使用

        Args:
            query: 查询文本
            k:     返回文档数

        Returns:
            str: 格式化后的知识文本，未找到时返回提示信息
        """
        docs = self.search(query, k=k)
        if not docs:
            return "知识库中未找到与您问题相关的信息。"
        return format_docs(docs)

    # ═══════════════════════════════════════════════════════
    # 3. LCEL RAG 问答管道 ★ 核心 ★
    # ═══════════════════════════════════════════════════════

    def _build_rag_chain(self):
        """
        使用 LCEL 构建 RAG 检索增强生成管道

        LCEL 管道结构（使用 | 操作符链接）:

            {
                "context":   retriever → format_docs,    # 检索 + 格式化
                "question":  RunnablePassthrough()        # 原样传递用户问题
            }
            | ChatPromptTemplate                           # 注入系统提示词
            | ChatOpenAI                                   # LLM 生成回答
            | StrOutputParser                              # 解析输出为纯文本

        这就是 LangChain Expression Language 的标志性用法：
        每一步都是一个 Runnable，通过 | 管道符串联成 RunnableSequence
        """
        # ① 检索器：查询 → 语义搜索 → [Document, ...]
        retriever = self.vector_service._store.as_retriever(
            search_kwargs={"k": 4}
        )

        # ② 系统提示词
        system_prompt = """你是一个 AI 知识库助手。请根据以下**检索到的知识**来回答用户的问题。

═══════════════════════════════════════
检索到的相关知识：
{context}
═══════════════════════════════════════

回答规则：
1. 优先基于检索到的知识回答，引用时可标注 [知识片段 N]
2. 如果检索到的知识足以回答问题，直接给出清晰、完整的回答
3. 如果知识只能部分回答问题，先给出基于知识的部分，再坦诚说明缺失
4. 如果检索到的知识与问题完全不相关，如实告知用户，不要编造
5. 回答时注意条理清晰，必要时使用分点或步骤说明"""

        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("human", "{question}"),
        ])

        # ③ LCEL 管道组装 ★
        # 这是整个 RAG 系统 LCEL 使用的核心——字典解构 + 管道链接
        rag_chain = (
            {
                # RunnableParallel（隐式）：字典的每个 value 都是 Runnable
                # "context" 分支：检索 → 格式化文档
                "context": retriever | format_docs,
                # "question" 分支：直接透传用户输入
                "question": RunnablePassthrough(),
            }
            | prompt           # RunnableSequence 第二步：填入提示词模板
            | self.llm         # 第三步：LLM 生成
            | StrOutputParser()  # 第四步：输出解析
        )

        return rag_chain

    async def ask(
        self,
        question: str,
        stream: bool = False,
    ) -> str:
        """
        使用 RAG 管道回答用户问题

        Args:
            question: 用户问题
            stream:   是否流式输出（异步迭代器）

        Returns:
            str: 增强后的回答
        """
        if not self.is_available:
            return "知识库当前不可用，无法提供知识增强回答。请检查 Milvus 服务状态。"

        rag_chain = self._build_rag_chain()

        if stream:
            # 流式模式：返回异步生成器
            return rag_chain.astream(question)
        else:
            # 非流式：直接返回完整回答
            response = await rag_chain.ainvoke(question)
            logger.info(f"RAG 问答完成, question='{question[:50]}...'")
            return response

    async def ask_stream(self, question: str) -> AsyncIterator[str]:
        """
        流式 RAG 问答——逐个 token 产出

        使用 LCEL 的 .astream() 方法，每次产出 LLM 生成的一个 token
        """
        if not self.is_available:
            yield "知识库当前不可用。"
            return

        rag_chain = self._build_rag_chain()

        async for chunk in rag_chain.astream(question):
            yield chunk

    # ═══════════════════════════════════════════════════════
    # 4. 便捷方法
    # ═══════════════════════════════════════════════════════

    def get_retriever(self):
        """
        获取独立的检索器（供外部 LCEL 管道组合使用）

        可以被其他 LCEL 管道嵌入，例如:
            retriever = rag_service.get_retriever()
            custom_chain = {"docs": retriever | format_docs} | some_prompt | some_llm
        """
        if not self.is_available:
            raise RuntimeError("知识库不可用")
        return self.vector_service._store.as_retriever(
            search_kwargs={"k": 4}
        )


# ─────────────────────────────────────────────────────────
# 模块级单例
# ─────────────────────────────────────────────────────────

_rag_service: Optional[RAGService] = None


def get_rag_service() -> RAGService:
    """获取 RAGService 单例"""
    global _rag_service
    if _rag_service is None:
        _rag_service = RAGService()
    return _rag_service
