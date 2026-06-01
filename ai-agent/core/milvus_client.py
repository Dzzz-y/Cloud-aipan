"""
Milvus 向量数据库连接管理器

功能:
    1. 管理 Milvus 连接生命周期
    2. 集合创建与 schema 管理
    3. 提供 langchain-milvus 向量存储实例
    4. 连接失败时优雅降级，不影响其他功能
"""

import logging
from typing import Optional

from pymilvus import (
    connections,
    Collection,
    CollectionSchema,
    FieldSchema,
    DataType,
    utility,
)
from langchain_milvus import Milvus as LangChainMilvus
from langchain_openai import OpenAIEmbeddings

from core.config import settings

logger = logging.getLogger(__name__)

# 全局连接状态
_milvus_connected: bool = False
_vector_store: Optional[LangChainMilvus] = None
_embeddings: Optional[OpenAIEmbeddings] = None


def get_embeddings() -> OpenAIEmbeddings:
    """获取 Embedding 模型实例（单例）"""
    global _embeddings
    if _embeddings is None:
        _embeddings = OpenAIEmbeddings(
            model=settings.EMBEDDING_MODEL_NAME,
            base_url=settings.LLM_BASE_URL,
            api_key=settings.LLM_API_KEY,
        )
    return _embeddings


def connect_milvus() -> bool:
    """
    连接 Milvus 服务器

    Returns:
        bool: 连接是否成功
    """
    global _milvus_connected
    if _milvus_connected:
        return True

    try:
        connections.connect(
            alias="default",
            host=settings.MILVUS_HOST,
            port=settings.MILVUS_PORT,
            timeout=10,
        )
        _milvus_connected = True
        logger.info(f"Milvus 连接成功: {settings.MILVUS_HOST}:{settings.MILVUS_PORT}")
        return True
    except Exception as e:
        logger.warning(f"Milvus 连接失败 ({settings.MILVUS_HOST}:{settings.MILVUS_PORT}): {e}")
        logger.warning("知识库功能将不可用，但其他功能不受影响")
        _milvus_connected = False
        return False


def disconnect_milvus():
    """断开 Milvus 连接"""
    global _milvus_connected
    try:
        connections.disconnect("default")
        _milvus_connected = False
        logger.info("Milvus 连接已断开")
    except Exception as e:
        logger.warning(f"断开 Milvus 连接时出错: {e}")


def _create_collection_if_not_exists() -> bool:
    """
    创建 Milvus 集合（如果不存在）

    Returns:
        bool: 创建/确认是否成功
    """
    collection_name = settings.MILVUS_COLLECTION_NAME

    try:
        if utility.has_collection(collection_name):
            logger.info(f"Milvus 集合 '{collection_name}' 已存在")
            return True

        # 定义 schema
        id_field = FieldSchema(
            name="id",
            dtype=DataType.INT64,
            is_primary=True,
            auto_id=True,
        )
        content_field = FieldSchema(
            name="content",
            dtype=DataType.VARCHAR,
            max_length=65535,
        )
        embedding_field = FieldSchema(
            name="vector",
            dtype=DataType.FLOAT_VECTOR,
            dim=settings.MILVUS_DIMENSION,
        )
        # metadata 存储为 JSON 字符串
        metadata_field = FieldSchema(
            name="metadata",
            dtype=DataType.VARCHAR,
            max_length=16384,
        )

        schema = CollectionSchema(
            fields=[id_field, content_field, embedding_field, metadata_field],
            description="RAG 知识库集合",
            enable_dynamic_field=False,
        )

        collection = Collection(name=collection_name, schema=schema)

        # 创建 IVF_FLAT 索引
        index_params = {
            "metric_type": "COSINE",
            "index_type": "IVF_FLAT",
            "params": {"nlist": 128},
        }
        collection.create_index(field_name="vector", index_params=index_params)
        collection.load()

        logger.info(f"Milvus 集合 '{collection_name}' 创建成功，维度: {settings.MILVUS_DIMENSION}")
        return True

    except Exception as e:
        logger.error(f"创建 Milvus 集合失败: {e}")
        return False


def get_vector_store() -> Optional[LangChainMilvus]:
    """
    获取 langchain-milvus 向量存储实例（单例，懒加载）

    Returns:
        Optional[LangChainMilvus]: 向量存储实例，连接失败时返回 None
    """
    global _vector_store

    if _vector_store is not None:
        return _vector_store

    # 连接 Milvus
    if not connect_milvus():
        return None

    # 确保集合存在
    if not _create_collection_if_not_exists():
        return None

    try:
        embeddings = get_embeddings()
        _vector_store = LangChainMilvus(
            embedding_function=embeddings,
            collection_name=settings.MILVUS_COLLECTION_NAME,
            connection_args={
                "host": settings.MILVUS_HOST,
                "port": settings.MILVUS_PORT,
            },
            # 关闭自动创建索引，我们已手动创建
            index_params=None,
            vector_field="vector",
            text_field="content",
            primary_field="id",
        )
        logger.info("LangChain Milvus 向量存储初始化成功")
        return _vector_store

    except Exception as e:
        logger.error(f"初始化 LangChain Milvus 向量存储失败: {e}")
        return None


def is_knowledge_base_available() -> bool:
    """检查知识库是否可用"""
    return _milvus_connected and _vector_store is not None


def get_kb_stats() -> dict:
    """获取知识库统计信息"""
    if not is_knowledge_base_available():
        return {
            "available": False,
            "total_documents": 0,
            "message": "知识库不可用，请检查 Milvus 连接",
        }

    try:
        collection = Collection(settings.MILVUS_COLLECTION_NAME)
        collection.load()
        num_entities = collection.num_entities
        return {
            "available": True,
            "total_documents": num_entities,
            "collection_name": settings.MILVUS_COLLECTION_NAME,
            "embedding_model": settings.EMBEDDING_MODEL_NAME,
            "dimension": settings.MILVUS_DIMENSION,
        }
    except Exception as e:
        logger.error(f"获取知识库统计失败: {e}")
        return {
            "available": False,
            "total_documents": 0,
            "message": str(e),
        }
