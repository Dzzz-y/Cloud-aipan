"""知识库 API 请求/响应模型"""

from pydantic import BaseModel
from typing import Optional


class KnowledgeSearchRequest(BaseModel):
    """知识库搜索请求"""
    query: str
    k: int = 4  # 返回结果数


class KnowledgeAddUrlRequest(BaseModel):
    """从 URL 添加文档到知识库"""
    url: str


class KnowledgeAddTextRequest(BaseModel):
    """从文本添加文档到知识库"""
    content: str
    title: str = ""
    source: str = "api"


class KnowledgeAskRequest(BaseModel):
    """RAG 问答请求"""
    question: str
    stream: bool = False
