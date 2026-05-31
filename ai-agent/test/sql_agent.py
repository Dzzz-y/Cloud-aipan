from langchain_community.agent_toolkits.sql.base import create_sql_agent
from langchain_community.agent_toolkits.sql.toolkit import SQLDatabaseToolkit
from langchain_community.utilities import SQLDatabase
from langchain_openai import ChatOpenAI
from langchain.agents.agent_types import AgentType

#设置OpenAI API密钥
llm= ChatOpenAI(
    model_name="qwen-turbo",
    base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
    api_key="sk-e2347e65380c4d7b907e963391715f5d",
    temperature=0
)

#1. 连接数据库
db= SQLDatabase.from_uri(
    f"mysql+pymysql://root:123456@192.168.10.40:3306/app_db",  #用户名:密码@地址
    custom_table_info={"account":"查询账号相关的表","account_file": "查询文件夹和文件内容相关的表","storage": "查询存储相关就查询此表"},
    )

#初始化工具包
toolkit = SQLDatabaseToolkit(db=db,llm=llm)
print(f"包含的工具个数:{len(toolkit.get_tools())}")
print("工具列表",[ tool.name for tool in toolkit.get_tools()])
#创建SQLAgent
agent=create_sql_agent(
    llm=llm,
    toolkit=toolkit,
    verbose=False,
    agent_type=AgentType.ZERO_SHOT_REACT_DESCRIPTION,
    handle_parsing_errors=True,
    max_iterations=10
)

def ask_question(question:str)->str:
    print(f"问题：{question}")
    result = agent.invoke({"input":question})
    return result["output"]

questions = [
    "数据库有多少表",
    "有多少账号"
]

for question in questions:
    answer=ask_question(question)
    print(f"答案:{answer}")
    print("-" * 50) 