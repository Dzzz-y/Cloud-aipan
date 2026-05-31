from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    #Redis配置
    REDIS_HOST: str="192.168.10.40"
    REDIS_POST: int = 6379
    REDIS_DB: int = 0
    REDIS_PASSWORD: str ="123456"
    REDIS_MAX_CONNECTIONS: int = 10

    #MySQL配置
    MYSQL_HOST: str ="192.168.10.40"
    MYSQL_POST: int = 3306
    MYSQL_USER: str="root"
    MYSQL_PASSWORD: str="123456"
    MYSQL_DATABASE: str ="app_db"
    MYSQL_CHARSET: str ="utf8mb4"

    #应用配置
    APP_NAME: str="AI智能体中API服务"
    DEBUG: bool = False

    #JWT配置
    JWT_SECRET_KEY: str="xdclass.net168xdclass.net168xdclass.net168xdclass.net168"
    JWT_ALGORITHM: str="HS256"
    JWT_ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    JWT_LOGIN_SUBJECT: str = "ADCLASS"

    #LLM配置
    LLM_MODEL_NAME: str = "qwen-turbo"
    LLM_BASE_URL: str="https://dashscope.aliyuncs.com/compatible-mode/v1"
    LLM_API_KEY: str="sk-e2347e65380c4d7b907e963391715f5d"
    LLM_TEMPERATURE: float = 0.7
    LLM_STREAMING: bool = True

    class Config:
        env_file=".env"
        case_sensitive = True
settings = Settings()