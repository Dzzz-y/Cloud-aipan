from langchain.agents import AgentExecutor, create_openai_functions_agent
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain.tools import Tool
from core.exceptions import ApiException
from core.llm import get_default_llm
from typing import List, AsyncIterator, Dict, AsyncGenerator
import asyncio
from services.chat_service import ChatService
from tools.chat_tools import get_chat_tools
from tools.rag_tools import get_rag_tools
from models.json_response import JsonData
import logging
logger = logging.getLogger(__name__)


def create_chat_agent(tools:List[Tool]):
    """创建聊天智能体"""
    system_prompt="""你是一个智能聊天助手。你可以：
    1.进行日常对话和问答
    2.使用搜索工具获取最新信息
    3.使用知识库工具（knowledge_search）搜索本地已存储的文档和知识
    4.使用知识库工具（knowledge_add_url）将在线文档加入本地知识库
    5.记住与用户的对话历史
    请保持回答专业、友好且准确。
    - 如果用户的问题需要最新信息，请使用搜索工具
    - 如果用户询问关于已存储文档的内容，请先搜索知识库
    - 如果用户要求你"记住"或"学习"某个网页/文档，请使用 knowledge_add_url
    """

    prompt=ChatPromptTemplate.from_messages(
        [
            ("system",system_prompt),
            ("system","以下是对话上下文：{context}"),
            ("human","{input}"),
            MessagesPlaceholder(variable_name="agent_scratchpad")
        ]
    )

    llm=get_default_llm()
    agent=create_openai_functions_agent(llm,tools,prompt)

    agent_excutor = AgentExecutor(
        agent=agent,
        tools=tools,
        verbose=True,
        max_iterations=5,
        handle_parsing_errors=True
    )
    return agent_excutor


async def chat_with_agent(agent_excutor:AgentExecutor,chat_service:ChatService,account_id:str,input_text:str)->AsyncIterator:
    """和智能体对话"""
    try:
        #获取增强上下文：摘要 + 语义记忆
        context=await chat_service.get_enhanced_context(account_id, input_text)

        #执行对话
        async for chunk in agent_excutor.astream({
            "input":input_text,
            "context":context,
        }):
            if "output" in chunk:
                response =chunk["output"]

                #保持对话信息
                chat_service.save_chat_message(account_id,input_text,response)

                #流式响应
                for token in response:
                    yield token
                    await asyncio.sleep(0.01)


    except Exception as e:
        logger.error(f"用户{account_id}对话失败：{e}")
        raise ApiException(msg="对话失败,请重试")
    
async def generate_stream_response(chat_service:ChatService,account_id:str,message:str)->AsyncIterator:
    """生成流式响应"""

    #合并所有工具：网络搜索 + RAG 知识库
    all_tools = get_chat_tools() + get_rag_tools()
    agent=create_chat_agent(all_tools)
    current_chunk=""

    async for token in chat_with_agent(agent,chat_service,account_id,message):
        current_chunk+=token
        #当遇到标点符号或者达到一定长度时，发送一次chunk
        if token in["。","!","!",";",","] or len(current_chunk)>=10:
            response=JsonData.stream_data(current_chunk)
            yield f"data: {response.model_dump_json()}\n\n"
            current_chunk =""
            await asyncio.sleep(0.01)

    #发送剩余的chunk
    if current_chunk:
        response =JsonData.stream_data(current_chunk)
        yield f"data: {response.model_dump_json()}\n\n"

    #发送结束标识
    yield "data: [DONE]\n\n"