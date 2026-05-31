import requests
import json

try:
    response = requests.post(
        'http://127.0.0.1:8000/api/document/stream',
        json={"query": "测试"},
        timeout=5
    )
    print(f"状态码: {response.status_code}")
    print(f"响应头: {response.headers}")
    print(f"响应内容: {response.text}")
    
    # 如果状态码是500，尝试获取更多信息
    if response.status_code == 500:
        print("\n尝试获取详细错误...")
        # 发送同样的请求，但不处理异常
        response2 = requests.post(
            'http://127.0.0.1:8000/api/document/stream',
            json={"query": "测试"},
            timeout=5
        )
except Exception as e:
    print(f"请求失败: {e}")
    import traceback
    traceback.print_exc()