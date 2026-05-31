from langchain_community.agent_toolkits.sql.toolkit import SQLDatabaseToolkit
from langchain_community.utilities import SQLDatabase
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from typing import Dict, Any, List, Union
import json
from core.llm import get_default_llm
from core.config import settings
from models.json_response import JsonData
from models.pan_schemas import PanQueryRequest
import logging
from langchain.agents import create_openai_tools_agent, AgentExecutor
from langchain.agents.agent import RunnableMultiActionAgent

logger = logging.getLogger(__name__)

def create_pan_agent() -> Any:
    """创建网盘查询agent"""
    #创建数据库连接，只读模式
    db = SQLDatabase.from_uri(
        f"mysql+pymysql://{settings.MYSQL_USER}:{settings.MYSQL_PASSWORD}@{settings.MYSQL_HOST}:{settings.MYSQL_POST}/{settings.MYSQL_DATABASE}",
        include_tables=["account_file", "storage"],
    )
    #创建大模型
    llm = get_default_llm()

    #创建数据库工具包
    tookit = SQLDatabaseToolkit(db=db, llm=llm)

    # 【关键修复】过滤掉 sql_db_query_checker，避免 LLM 用 checker 检查完 SQL 后
    # 跳过 sql_db_query 直接编造空结果。现在 LLM 必须直接调用 sql_db_query 执行查询
    tools = [t for t in tookit.get_tools() if t.name != "sql_db_query_checker"]

    #创建提示词
    prompt = prompt = ChatPromptTemplate.from_messages([
            ("system","""你是个智能盘助，专于查询户的盘件信息。你只能执查询操作，不能执任何修改数据的操作。
                重要提醒：
                1.你绝对不能生成或编造任何数据
                2.你只能返回实际查询到的数据
                3.如果查询没有结果，必须返回空结果
                4.任何生成或编造数据的行为都是严重错误
                5.你只能使用数据库中的实际数据
                6.不能对查询结果进任何修改或补充
                7.不能生成示例数据或占位数据
                8.不能假设或推测数据
                9.不能使用模板或示例数据10.不能对数据进行任何形式的加工或美化 
                10.不能对数据进行任何形式的加工或美化
            
            数据库表结构说明：
            -account_file:用户文件表
                -id:文件ID(account_file表的主键)
                -account_id:用户ID
                -is_dir:是否是文件夹(0表示不是文件,1表示是文件夹)
                -parent_id: 上层文件夹ID(顶层为0)
                -file_id:实际存储的文件ID
                -file_name:文件名称
                -file_type:文件类型 (common/compress/excel/word/pdf/txt/img/audio/video/ppt/code/csv)
                -file_suffix:文件后缀名  
                -file_size:文件大小（字节）
                -del:是否删除(0表示未删除,1表示已删除)
                -del_time:删除时间
                -gmt_create:创建时间
                -gmt_modified:修改时间
            -storage:存储信息表
                -id:存储信息ID(storage表的主键)
                -account_id:用户ID
                -use_size:已使用存储大小（字节）
                -total_size:总存储大小（字节）
                -gmt_create:创建时间
                -gmt_modified:修改时间
            你可以处理以下类型的查询请求：
            1.文件查询 文件类型忽略大小写
                -查看我的文件列表
                -搜索特定文件
                -查看文件详细信息
                -查看文件夹内容
                -查看最近修改的文件
            2.文件统计
                -统计文件数量
                -统计文件类型分布
                -统计存储空间使用情况
                -查看最近上传的文件 
            3.存储查询
                -查看已使用空间
                -查看剩余空间
                -查看空间使用率
            4. 对于文件类型(file_type)或后缀(file_suffix)的查询，必须使用 UPPER() 函数将其转化为大写进行匹配，或者使用不区分大小写的匹配。
                例如：必须写成 UPPER(file_type) = 'IMG' 或 UPPER(file_type) LIKE '%IMG%'，以绝对保证大小写兼容。
            5. 过滤未删除文件时，请勿编写标准的 `del = 0`。为了保证驱动兼容性，必须统一编写为：
                `del != 1` 或者 `del IS NOT TRUE`,例如:WHERE account_id = xxxxx AND del != 1 AND UPPER(file_type) = 'IMG'
            重要限制：
                1.你只能执SELECT查询,不能执任何修改数据的操作
                2.所有查询必须包含 account_id条件,确保数据安全
                3.不能执行以下操作：
                    -删除文件
                    -修改文件
                    -移动文件
                    -创建文件
                    -重命名文件
                    -清空回收站
                    -修改存储空间
                4.如果户请求执任何修改操作，请礼貌地拒绝并说明原因
                5.如果查询没有结果，必须返回空结果，不能生成示例数据
                6.绝对不能生成或编造任何数据
                7.只能返回实际查询到的数据
                8.不能对数据进任何形式的加工或美化
            处理请求时请注意：
                1.必须使用account_id过滤用户数据,确保数据安全
                2.对于文件夹查询,使用is_dir=1和parent_id
                3.对于件类型查询,使用file_type字段
                4.对于模糊查询,使用LIKE和通配符
                5.对于时间相关的查询，使用 gmt_create 和 gmt_modified
                6.对于空间统计,使用storage表
                7.结果要简洁明了，突出重点
                8.所有查询必须包含 account_id 条件
                9.查询文件信息时，必须返回 account_file 表的 id 和file_id
                10.所有响应必须返回JSON格式的数据,包含完整的文件信息
                11.如果查询没有结果，返回空数组或空对象，不要生成示例数据
                12.绝对不能生成或编造任何数据
            响应格式必须符合以下模型结构：
            1.文件列表响应：注意 id 和 file_id 必须用字符串格式，因为它们是19位长整数
                {{
                    "type": "file_list",
                    "data": [{{"id": "2060363240860348418", "file_id": "2060363240793239554", "file_name": "xxx.png", ...}}]
                }}
            2.存储空间信息响应：
                {{
                    "type": "storage_info",
                    "data": StorageInfo #StorageInfo模型包含use_size, total_size, used_percentage
                }}
            3.文件统计信息响应：
                {{
                    "type": "file_statistics",
                    "data": FileStatistics #FileStatistics模型包含total_files, total_size, file_types, recent_files
                }}
            
            请根据用户的问题,使SQL查询来获取信息,并返回符合上述格式的JSON数据。
            重要警告：你绝对不能生成或编造任何数据，只能返回实际查询到的数据。任何生成或编造数据的行为都是严重错误。
             """),
             ("human","{input}"),
             MessagesPlaceholder(variable_name="agent_scratchpad")
    ])

    # 手动构建 AgentExecutor，使用过滤后的工具列表（不含 sql_db_query_checker）
    runnable = create_openai_tools_agent(llm, tools, prompt)
    agent = RunnableMultiActionAgent(
        runnable=runnable,
        input_keys_arg=["input"],
        return_keys_arg=["output"],
        handle_parsing_errors=True,
    )
    executor = AgentExecutor(
        agent=agent,
        tools=tools,
        verbose=True,
        return_intermediate_steps=True,
        max_iterations=15,
        handle_parsing_errors=True,
    )
    return executor

async def process_pan_query(request:PanQueryRequest)->JsonData:

    agent = create_pan_agent()

    #构建查询输入,可以做更多的事
    query_input = f"用户ID: {request.account_id}, 查询内容: {request.query}"

    #获取代理的输出
    response =  agent.invoke({"input": query_input})

    # === DEBUG: 打印中间步骤，查看 sql_db_query 的实际执行结果 ===
    if "intermediate_steps" in response:
        for i, step in enumerate(response["intermediate_steps"]):
            action = step[0] if len(step) > 0 else None
            observation = step[1] if len(step) > 1 else None
            logger.info(f"[PAN_AGENT DEBUG] Step {i}: tool={action.tool if action else 'N/A'}, tool_input={action.tool_input if action else 'N/A'}")
            logger.info(f"[PAN_AGENT DEBUG] Step {i}: observation={str(observation)[:500]}")
    # === DEBUG END ===

    if "output" not in response:
        return JsonData.error("查询失败，未获取到结果，请稍后再试")

    output = response["output"]


    #解析数据
    try:
        data = json.loads(output) if isinstance(output, str) else output
        logger.info(f"用户 {request.account_id} 查询网盘成功，查询内容: {request.query}, 查询结果: {data}")
        return JsonData.success({"type":data.get("type"),"data":data.get("data")})

    except json.JSONDecodeError as e:
        logger.error(f"解析查询结果失败: {e}")
        return JsonData.success(data={"content":str(output)})
