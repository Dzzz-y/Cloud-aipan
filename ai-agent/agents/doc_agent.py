from langchain.agents import AgentExecutor, create_openai_functions_agent
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain.tools import Tool
from core.exceptions import ApiException
from core.llm import get_default_llm
from typing import List, AsyncIterator, Dict, AsyncGenerator
import asyncio
from datetime import datetime
from services.chat_service import ChatService
from tools.chat_tools import get_chat_tools
from models.json_response import JsonData
import logging
logger = logging.getLogger(__name__)


def create_document_agent(tools:List[Tool])->AgentExecutor:
    """创建文档智能体"""
    system_message="""你是个专业的档处理助。你的任务是分析用户提供的档，成质量的总结。
    你需要：
    1.仔细阅读并理解文档内容
    2.根据用户要求的总结类型（简要/详细/要点）生成相应的总结
    3.提取文档的关键要点
    4.确保总结准确、全面、易读
    5.如果文档内容不清晰或不完整，指出这些问题并提供建议
    6.根据用户的反馈不断优化总结结果
    如果用户提供了额外的要求，请尽量满这些要求。
    如果用户的需求需要最新信息,请使用搜索工具。
    """

    #创建提示词模板
    prompt=ChatPromptTemplate.from_messages([
        ("system",system_message),
        ("human","{input}"),
        MessagesPlaceholder(variable_name="agent_scratchpad"),
    ])  

    #获取大模型实例
    llm=get_default_llm()
    
    #创建智能体
    agent=create_openai_functions_agent(llm,tools,prompt)  

    agent_executor=AgentExecutor(
        agent=agent,
        tools=tools,
        verbose=True,
        max_retries=3,
        handle_parsing_errors=True
        )
    return agent_executor 

async def process_document(agent_excutor:AgentExecutor,input_text:str)->AsyncIterator[str]:
    logging.info(f"开始处理文档：{input_text}")
    async for chunk in agent_excutor.astream({"input":input_text}):
        if "output" in chunk:
            response = chunk["output"]
            logging.info(f"文档处理结果；{response}")
            yield response
            


 