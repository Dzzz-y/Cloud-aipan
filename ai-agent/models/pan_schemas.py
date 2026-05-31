from pydantic import BaseModel
from typing import Optional, Dict, Any, List
from datetime import datetime

class PanQueryRequest(BaseModel):
    """网盘查询请求模型""" 
    account_id: Optional[int] = None 
    query: str

class FileInfo(BaseModel):
    """文件信息模型"""
    id: int
    file_id: int
    file_name: str
    file_type: str
    file_suffix: str
    file_size: int
    gmt_create: datetime
    gmt_modified: datetime
   
class StorageInfo(BaseModel):
    """存储信息模型"""
    use_size: int
    total_size: int
    used_percentage: float

class FileStatistics(BaseModel):
    """文件统计信息模型"""
    total_files: int
    total_size: int
    file_types: Dict[str, int]  # 文件类型及其数量
    recent_files: List[FileInfo]  

class PanQueryResponse(BaseModel):
    """网盘查询响应模型"""
    type: str
    data:Dict[str,Any]
