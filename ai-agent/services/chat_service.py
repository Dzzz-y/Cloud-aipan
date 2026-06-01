import json
from typing import List, Dict, Optional
import redis
from datetime import datetime
from core.config import settings
from core.llm import get_default_llm
from models.json_response import JsonData
import logging
logger = logging.getLogger(__name__)

class ChatService:
    def __init__(self):
        self.redis_client = redis.Redis(
            host=settings.REDIS_HOST,
            port=settings.REDIS_POST,
            db=settings.REDIS_DB,
            password=settings.REDIS_PASSWORD,
            decode_responses=True,
            max_connections=settings.REDIS_MAX_CONNECTIONS
        )
        self.llm = get_default_llm()

    def _get_chat_key(self,account_id:str)->str:
        """获取用户对话历史key"""
        return f"chat_history:{account_id}"
    
    def save_chat_history(self,account_id:str,messages:List[Dict]):
        """保存用户对话历史"""
        key=self._get_chat_key(account_id)
        self.redis_client.set(key,json.dumps(messages))

    def get_chat_history(self,account_id:str)->List[Dict]:
        """获取用户对话历史"""
        key=self._get_chat_key(account_id)
        messages=self.redis_client.get(key)
        if messages :
            return json.loads(messages)
        return []
    
    def add_message(self,account_id:str,role:str,content:str):
        """添加一条消息到聊天记录"""
        messages=self.get_chat_history(account_id)
        messages.append({"role":role,"content":content,"timestamp":datetime.now().isoformat()})
        #增加更多逻辑 TODO
        self.save_chat_history(account_id,messages)


    def save_chat_message(self,account_id:str,user_message:str,assistant_message:str):
        """保存用户对话信息（Redis 历史 + Milvus 语义记忆）"""
        self.add_message(account_id,"user",user_message)
        self.add_message(account_id,"assistant",assistant_message)
        # 同时存储语义记忆（失败不影响主流程）
        self.save_semantic_memory(account_id, user_message, assistant_message)



    def clear_chat_history(self,account_id:str):
        """清空用户对话历史"""
        key=self._get_chat_key(account_id)
        self.redis_client.delete(key)

    def _get_summary_key(self,account_id:str)->str:
        """获取摘要key"""
        return f"chat_summary:{account_id}"
    
    async def generate_summary(self,account_id:str)->str:
        """生成摘要"""
        try:
            #获取最新聊天记录
            messages=self.get_chat_history(account_id)
            if not messages:
                return ""
            
            #构建提示词
            prompt=f"""请根据以下对话历史成个简洁的核摘要，突出主要话题和关键信息:
            {json.dumps(messages,ensure_ascii=False,indent=2 )}
            摘要要求：
            1.突出对话的主要话题和关键信息
            2.使用第三人称描述，提取重要数据/时间节点/待办事项
            3.保留原始对话中的重要细节
            4.确保包含最新的对话内容
            """

            #生成摘要
            response= await self.llm.ainvoke(prompt)
            new_summary=response.content

            #获取历史摘要
            summary_key = self._get_summary_key(account_id)
            old_summary =  self.redis_client.get(summary_key)
            final_summary=""
            if old_summary:
                #如果存在旧摘要，生成一个合并的提示词
                merge_prompt=f"""请将以下两个摘要合并为一个摘要
                旧摘要：
                {old_summary}

                新摘要：
                {new_summary}
                合并要求：
                1.保留两个摘要中的重要信息
                2.突出对话的主要话题和关键信息
                3.使用第三人称描述，提取重要数据/时间节点/待办事项
                4.保留原始对话中的重要细节
                5.确保包含最新的对话内容
                """
                merge_response = await self.llm.ainvoke(merge_prompt)
                final_summary = merge_response.content
            else:
                final_summary = new_summary
            
            #更新缓存
            self.redis_client.set(summary_key,final_summary)
            return final_summary
        
        except Exception as e:
            logger.error(f"生成摘要失败:{e}")
            return ""

    # ═══════════════════════════════════════════════════════
    # 语义记忆（向量化对话历史 — 增强对话上下文）
    # ═══════════════════════════════════════════════════════

    def save_semantic_memory(self, account_id: str, user_message: str, assistant_message: str) -> bool:
        """
        将对话记录以向量形式存入 Milvus，构建长期语义记忆

        与 Redis 原始存储不同，语义记忆允许通过语义相似度搜索
        历史对话——即使关键词不完全匹配也能找到相关记忆。

        Args:
            account_id:         用户 ID
            user_message:       用户消息
            assistant_message:  助手回复

        Returns:
            bool: 是否存储成功。失败不影响主流程。
        """
        try:
            from services.vector_service import get_vector_service
            vector_service = get_vector_service()
            return vector_service.add_conversation_memory(
                account_id=account_id,
                user_message=user_message,
                assistant_message=assistant_message,
            )
        except Exception as e:
            logger.warning(f"保存语义记忆失败（不影响主流程）: {e}")
            return False

    def search_semantic_memory(self, account_id: str, query: str, k: int = 3) -> List[str]:
        """
        搜索与当前查询语义相关的历史对话记忆

        在每次新对话前调用，用于补充 LLM 摘要中可能遗漏的细节。

        Args:
            account_id: 用户 ID
            query:      当前用户查询（用于语义匹配）
            k:          返回的记忆数量

        Returns:
            List[str]: 相关的历史对话文本。知识库不可用时返回空列表。
        """
        try:
            from services.vector_service import get_vector_service
            vector_service = get_vector_service()
            return vector_service.search_conversation_memory(
                account_id=account_id,
                query=query,
                k=k,
            )
        except Exception as e:
            logger.warning(f"搜索语义记忆失败（不影响主流程）: {e}")
            return []

    async def get_enhanced_context(self, account_id: str, current_query: str) -> str:
        """
        获取增强后的对话上下文——摘要 + 语义记忆

        组合两层上下文：
            1. LLM 摘要（粗粒度，时间序列压缩）
            2. 语义记忆（细粒度，语义相关性召回）

        Args:
            account_id:    用户 ID
            current_query: 当前用户查询

        Returns:
            str: 增强后的上下文字符串，直接注入 Agent 系统提示词
        """
        parts = []

        # ① LLM 摘要（原有逻辑，保持不变）
        summary = await self.generate_summary(account_id)
        if summary:
            parts.append(f"## 历史对话摘要\n{summary}")

        # ② 语义记忆（新增——从 Milvus 中召回相关历史对话）
        semantic_memories = self.search_semantic_memory(
            account_id=account_id,
            query=current_query,
            k=3,
        )
        if semantic_memories:
            memories_text = "\n".join(
                f"- {mem}" for mem in semantic_memories
            )
            parts.append(f"## 相关历史对话（语义匹配）\n{memories_text}")

        return "\n\n".join(parts) if parts else ""