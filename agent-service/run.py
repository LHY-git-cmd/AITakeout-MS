"""
服务启动脚本
使用 uvicorn 启动 FastAPI 应用
"""
import uvicorn
from app.core.config import settings

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",               # 指定 FastAPI 应用位置（模块名:变量名）
        host=settings.HOST,           # 监听地址
        port=settings.PORT,           # 监听端口
        reload=settings.DEBUG,        # 调试模式：代码修改后自动重载
        workers=1                     # 工作进程数（生产环境可设为 CPU 核心数）
    )