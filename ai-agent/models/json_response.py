from pydantic import BaseModel
from typing import Any,Optional,Literal


class JsonData(BaseModel):
    code: int=0
    data: Optional[Any] = None
    msg: Optional[str] =None
    type:Literal["stream","text"]="stream"
    
    @classmethod
    def success(cls,data:Any)->"JsonData":
        return cls(code=0,data=data,type="text")
    
    @classmethod
    def error(cls,msg:str="error",code:int=-1)->"JsonData":
        return cls(code=code,msg=msg,type="text")
    
    @classmethod
    def stream_data(cls,data:Any,msg:str="")->"JsonData":
        return cls(code=0,data=data,msg=msg,type="stream")