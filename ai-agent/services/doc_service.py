from typing import Any, AsyncIterator
from agents.doc_agent import create_document_agent, process_document
from models.doc_schemas import DocumentRequest
from tools.document_tools import DocumentTools
import gc
import logging

logger = logging.getLogger(__name__)
class DocumentService:
    def __init__(self):
         #初始化工具
        self.tools = DocumentTools.create_tool()
        #创建智能体
        self.agent_excutor = create_document_agent(self.tools)

    async def process_document_stream(self,request:DocumentRequest)->AsyncIterator[str]:
        """处理文档"""
        try:
            logger.info(f"开始处理文档: {request.url}")
            #获取文档内容
            doc_content=DocumentTools.fetch_document(request.url)

            #根据文档长度进行分隔
            content = doc_content["content"]
            chunks = []

            if len(content) > 10000:
                logger.info("文档过长，进行分段处理")
                current_chunk = ""
                for paragraph in content.split("\n\n"):
                    if len(current_chunk) + len(paragraph) >10000 and current_chunk:
                        chunks.append(current_chunk)
                        current_chunk = paragraph
                    else:
                        current_chunk = f"{current_chunk}\n\n{paragraph}" if current_chunk else paragraph
                if current_chunk:
                    chunks.append(current_chunk)

            else:
                chunks = [content]

            #处理每个文档块
            for i,chunk in enumerate(chunks):
                input_text = self._build_input_text(
                    doc_content["title"],
                    chunk,
                    request.summary_type,
                    request.language,
                    request.length or "无限制",
                    request.additional_instructions or "无")
                #异步处理文档块
                async for chunk in process_document(self.agent_excutor,input_text):
                    yield chunk

                #如果不是最后一个片段，插入分隔符
                if i < len(chunks) - 1:
                    yield "\n--下一部分--\n"

                #清理内存
                gc.collect()

        except Exception as e:
            logger.error(f"获取文档失败: {e}")
            yield "无法获取文档内容,请检查URL是否正确或文档是否可访问。"
    
    def _build_input_text(self,title:str,content:str,summary_type:str,language:str,length:str,additional_instructions)->str:
        """构建输入文本"""
        return f"""
        文档标题: {title}
        文档内容: {content}
        总结类型: {summary_type}
        语言: {language}
        长度要求: {length}
        额外要求: {additional_instructions}
        """        
            
       
