"""直接测试 SQL 查询，排除 LangChain 层的干扰"""
import pymysql
from core.config import settings

conn = pymysql.connect(
    host=settings.MYSQL_HOST,
    port=settings.MYSQL_POST,
    user=settings.MYSQL_USER,
    password=settings.MYSQL_PASSWORD,
    database=settings.MYSQL_DATABASE,
    charset=settings.MYSQL_CHARSET,
)

account_id = 2060349668843331585
sql = """
SELECT id, file_id, file_name, file_type, file_suffix, file_size, gmt_create, gmt_modified
FROM account_file
WHERE account_id = %s
  AND del != 1
  AND UPPER(file_type) = 'IMG'
"""

with conn.cursor() as cursor:
    cursor.execute(sql, (account_id,))
    rows = cursor.fetchall()
    print(f"查询到 {len(rows)} 条记录:")
    for row in rows:
        print(row)

conn.close()
