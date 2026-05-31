from typing import List, Optional, Dict, Any, Generator
import requests
from bs4 import BeautifulSoup
from langchain.tools import Tool
import re
from urllib.parse import urlparse
import time
import io
from PyPDF2 import PdfReader
from tqdm import tqdm
import gc
import logging
from core.exceptions import ApiException

logger = logging.getLogger(__name__)

class DocumentTools:
    """文档处理工具集"""

    @staticmethod
    def fetch_document(url: str) -> Dict[str, Any]:
        """获取文档"""
        try:
            logger.info(f"开始获取文档: {url}")
            
            # 添加请求头，模拟浏览器访问
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
            }
            
            response = requests.get(url, stream=True, timeout=30, headers=headers, verify=False)
            response.raise_for_status()
            
            # 设置编码
            response.encoding = response.apparent_encoding or 'utf-8'

            # 获取文档类型
            content_type = response.headers.get("Content-Type", "")
            logger.info(f"Content-Type: {content_type}")
            
            if "text/html" in content_type:
                logger.info("解析HTML文档")
                return DocumentTools._parse_html(response)
            elif "application/pdf" in content_type:
                logger.info("解析PDF文档")
                return DocumentTools._parse_pdf_stream(response)
            else:
                logger.info(f"未知文档类型，作为文本处理")
                return {
                    "title": urlparse(url).path.split("/")[-1] or "文档",
                    "content": response.text,
                    "type": "text"
                }

        except requests.exceptions.Timeout:
            logger.error(f"请求超时: {url}")
            return {
                "title": "错误",
                "content": "请求超时，请稍后重试",
                "type": "error"
            }
        except requests.exceptions.ConnectionError:
            logger.error(f"连接错误: {url}")
            return {
                "title": "错误",
                "content": "无法连接到服务器，请检查网络或URL",
                "type": "error"
            }
        except requests.exceptions.HTTPError as e:
            logger.error(f"HTTP错误: {e}")
            return {
                "title": "错误",
                "content": f"服务器返回错误: {e.response.status_code}",
                "type": "error"
            }
        except Exception as e:
            logger.error(f"获取文档失败: {str(e)}", exc_info=True)
            return {
                "title": "错误",
                "content": f"无法获取文档: {str(e)}",
                "type": "error"
            }

    @staticmethod
    def _parse_html(response: requests.Response) -> Dict[str, Any]:
        """解析HTML文档"""
        try:
            logger.info("解析HTML")
            soup = BeautifulSoup(response.text, "html.parser")

            # 提取文档标题
            title = soup.title.string if soup.title else "无标题"
            if title:
                title = title.strip()

            # 移除脚本和样式标签
            for script in soup(["script", "style", "nav", "footer", "header"]):
                script.decompose()

            # 尝试多种方式提取正文内容
            content = ""
            
            # 方法1：查找常见的内容容器
            content_selectors = [
                'main', 'article', 
                '.content', '#content', 
                '.post-content', '.article-content',
                '.entry-content', '.main-content',
                'div[role="main"]'
            ]
            
            main_content = None
            for selector in content_selectors:
                main_content = soup.select_one(selector)
                if main_content:
                    logger.info(f"使用选择器提取内容: {selector}")
                    break
            
            if main_content:
                # 从主要内容区域提取文本
                content = main_content.get_text(separator='\n', strip=True)
            else:
                # 方法2：如果没有找到特定容器，提取body中的段落
                body = soup.body
                if body:
                    # 提取所有段落和标题
                    for tag in body.find_all(['p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'li', 'div']):
                        text = tag.get_text(strip=True)
                        if text and len(text) > 20:  # 过滤太短的文本
                            content += text + "\n"
                else:
                    # 方法3：最后备用方案
                    content = soup.get_text(separator='\n', strip=True)

            # 清理文本
            content = DocumentTools._clean_text(content)
            
            # 限制内容长度
            max_length = 50000
            if len(content) > max_length:
                content = content[:max_length] + "\n\n[文档过长，已截断...]"
                logger.warning(f"文档内容过长，已截断至 {max_length} 字符")

            # 如果没有提取到内容，返回错误
            if not content or len(content) < 50:
                logger.warning("提取的内容过少，尝试备用方法")
                # 备用方法：直接获取所有文本
                content = soup.get_text(separator='\n', strip=True)
                content = DocumentTools._clean_text(content)

            logger.info(f"成功解析HTML，标题: {title}, 内容长度: {len(content)}")
            
            return {
                "title": title,
                "content": content if content else "无法提取文档内容",
                "type": "html"
            }
            
        except Exception as e:
            logger.error(f"解析HTML失败: {str(e)}", exc_info=True)
            return {
                "title": "解析错误",
                "content": f"HTML解析失败: {str(e)}",
                "type": "error"
            }

    @staticmethod
    def _parse_pdf_stream(response: requests.Response) -> Dict[str, Any]:
        """解析pdf流,加载在线内容"""
        logger.info("解析PDF文档")

        # 创建内存缓冲区
        buffer = io.BytesIO()
        # 获取文件总大小
        total_size = int(response.headers.get("Content-Length", 0))
        
        try:
            # 使用tqdm显示下载进度
            with tqdm(total=total_size, unit="B", unit_scale=True, desc="下载PDF") as pbar:
                for chunk in response.iter_content(chunk_size=8192):
                    if chunk:
                        buffer.write(chunk)
                        pbar.update(len(chunk))
            
            # 重置缓冲区
            buffer.seek(0)

            # 分块读取PDF内容
            content = ""
            pdf_reader = PdfReader(buffer)

            # 获取文档信息
            info = pdf_reader.metadata
            title = info.title if info and info.title else "未命名文档"

            # 分块处理
            total_pages = len(pdf_reader.pages)
            with tqdm(total=total_pages, desc="解析PDF") as pbar:
                for page_num, page in enumerate(pdf_reader.pages):
                    page_text = page.extract_text()
                    if page_text:
                        content += page_text + "\n"

                    # 移除相关的空行或空格,清理内存
                    if (page_num + 1) % 10 == 0:
                        content = DocumentTools._clean_text(content)
                        gc.collect()
                    pbar.update(1)

            # 限制内容长度
            if len(content) > 50000:
                content = content[:50000] + "\n\n[PDF过长，已截断...]"
                
            return {
                "title": title,
                "content": content if content else "无法提取PDF内容",
                "type": "pdf"
            }
            
        except Exception as e:
            logger.error(f"解析PDF失败: {str(e)}", exc_info=True)
            return {
                "title": "解析错误",
                "content": f"PDF解析失败: {str(e)}",
                "type": "error"
            }
        finally:
            buffer.close()

    @staticmethod
    def _clean_text(text: str) -> str:
        """清理文本内容"""
        if not text:
            return ""
        # 移除多余的空行
        text = re.sub(r'\n\s*\n', '\n', text)
        # 移除多余的空格
        text = re.sub(r'[ \t]+', ' ', text)
        # 限制最多两个换行
        text = re.sub(r'\n{3,}', '\n\n', text)
        # 移除空白行开头的行
        lines = [line.strip() for line in text.split('\n') if line.strip()]
        return '\n'.join(lines)

    @staticmethod
    def create_tool() -> List[Tool]:
        """创建文档处理工具列表"""
        fetch_tool = Tool(
            name="fetch_document",
            func=DocumentTools.fetch_document,
            description="根据URL获取文档内容,返回文档标题、内容和类型。参数: url (字符串) - 文档的URL地址"
        )
        return [fetch_tool]