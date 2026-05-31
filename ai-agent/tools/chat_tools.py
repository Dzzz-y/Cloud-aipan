from langchain_core.tools import tool
from langchain_community.utilities import SearchApiAPIWrapper
import os
os.environ["SEARCHAPI_API_KEY"]="1iqZzEfC1hyBABu1Te9ncz4L"


@tool("web_search", return_direct=False)
def web_search(query: str) -> str:
    """"
    使用此工具搜索最新的互联网信息。当你需要获取实时信息或不确定某个事实时使用
    """
    try:
        search = SearchApiAPIWrapper()
        results = search.results(query)
        return "\n\n".join([
            f"来源：{res['title']}\n内容：{res['snippet']}"
                  for res in results['organic_results']
        ])
    except Exception as e:
        return f"搜索失败:{str(e)}"
    
def get_chat_tools():
    """
    获取可用的工具列表
    """
    tools=[
        web_search
    ]
    return tools