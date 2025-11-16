@echo off
chcp 65001 >nul
echo ========================================
echo   构建并上传前端Docker镜像
echo ========================================
echo.

REM 阿里云镜像地址
set IMAGE=crpi-8mw6zrxo8n10fjq0.cn-beijing.personal.cr.aliyuncs.com/qj_szr_docker/ai_novel-frontend:latest

REM 检查Docker是否运行
docker info >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker未运行！请先启动Docker Desktop
    pause
    exit /b 1
)

REM 登录阿里云（首次运行需要输入密码）
echo 登录阿里云镜像服务...
docker login crpi-8mw6zrxo8n10fjq0.cn-beijing.personal.cr.aliyuncs.com
if errorlevel 1 (
    echo ❌ 登录失败
    pause
    exit /b 1
)

REM 构建镜像
echo.
echo 🔨 开始构建前端镜像...
cd frontend
docker build -t %IMAGE% .
if errorlevel 1 (
    echo ❌ 构建失败
    cd ..
    pause
    exit /b 1
)
cd ..

REM 推送镜像
echo.
echo 📤 开始推送镜像...
docker push %IMAGE%
if errorlevel 1 (
    echo ❌ 推送失败
    pause
    exit /b 1
)

echo.
echo ========================================
echo   ✅ 前端镜像构建上传完成！
echo ========================================
echo.
echo 镜像地址: %IMAGE%
echo.
echo 拉取命令: docker pull %IMAGE%
echo.
pause

