@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ==========================================================
REM One-click Docker build + push (Windows .bat)
REM
REM Usage:
REM   docker_build_push.bat                 (tag defaults to current git short SHA, fallback: timestamp)
REM   docker_build_push.bat v1.2.3          (use explicit tag)
REM
REM Optional env vars (override defaults):
REM   REGISTRY            e.g. crpi-xxxx.cn-beijing.personal.cr.aliyuncs.com
REM   NAMESPACE           e.g. qj_szr_docker
REM   BACKEND_IMAGE       e.g. ai_novel-backend
REM   FRONTEND_IMAGE      e.g. ai_novel-frontend
REM   PUSH_LATEST         1 (default) / 0
REM   NO_CACHE            1 to add --no-cache
REM   PULL_BASE           1 to add --pull
REM   DOCKER_USERNAME     username for non-interactive login
REM   DOCKER_PASSWORD     password/token for non-interactive login (read by PowerShell; not echoed)
REM
REM Notes:
REM - Non-interactive login requires PowerShell (preinstalled on Windows).
REM - This script builds from ./backend and ./frontend using their Dockerfiles.
REM ==========================================================

pushd "%~dp0"

REM ---- Defaults (match docker-compose.yml) ----
if not defined REGISTRY set "REGISTRY=crpi-8mw6zrxo8n10fjq0.cn-beijing.personal.cr.aliyuncs.com"
if not defined NAMESPACE set "NAMESPACE=qj_szr_docker"
if not defined BACKEND_IMAGE set "BACKEND_IMAGE=ai_novel-backend"
if not defined FRONTEND_IMAGE set "FRONTEND_IMAGE=ai_novel-frontend"
if not defined PUSH_LATEST set "PUSH_LATEST=1"
if not defined NO_CACHE set "NO_CACHE=0"
if not defined PULL_BASE set "PULL_BASE=0"

REM ---- Preconditions ----
docker version >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Docker is not available. Please install/start Docker Desktop and ensure 'docker' is in PATH.
  popd
  exit /b 1
)

REM ---- Resolve tag ----
set "TAG=%~1"
if not defined TAG (
  for /f "usebackq delims=" %%i in (`git rev-parse --short HEAD 2^>nul`) do set "TAG=%%i"
)
if not defined TAG (
  for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "(Get-Date).ToString('yyyyMMdd-HHmmss')"`) do set "TAG=%%i"
)

set "BACKEND_REPO=%REGISTRY%/%NAMESPACE%/%BACKEND_IMAGE%"
set "FRONTEND_REPO=%REGISTRY%/%NAMESPACE%/%FRONTEND_IMAGE%"

echo.
echo ==============================
echo Registry : %REGISTRY%
echo Namespace: %NAMESPACE%
echo Tag      : %TAG%
echo Backend  : %BACKEND_REPO%
echo Frontend : %FRONTEND_REPO%
echo ==============================
echo.

REM ---- Login (interactive by default; non-interactive if DOCKER_USERNAME/DOCKER_PASSWORD provided) ----
call :DockerLogin
if errorlevel 1 goto :error

REM ---- Build args ----
set "BUILD_ARGS="
if "%NO_CACHE%"=="1" set "BUILD_ARGS=!BUILD_ARGS! --no-cache"
if "%PULL_BASE%"=="1" set "BUILD_ARGS=!BUILD_ARGS! --pull"

REM ---- Build + push backend ----
call :BuildTagPush "%BACKEND_REPO%" "backend" "backend\Dockerfile"
if errorlevel 1 goto :error

REM ---- Build + push frontend ----
call :BuildTagPush "%FRONTEND_REPO%" "frontend" "frontend\Dockerfile"
if errorlevel 1 goto :error

echo.
echo [OK] Done.
echo - Pushed: %BACKEND_REPO%:%TAG%
echo - Pushed: %FRONTEND_REPO%:%TAG%
if "%PUSH_LATEST%"=="1" (
  echo - Pushed: %BACKEND_REPO%:latest
  echo - Pushed: %FRONTEND_REPO%:latest
)
echo.
echo Next:
echo - (Optional) docker-compose pull ^& docker-compose up -d
echo.

popd
exit /b 0

:BuildTagPush
REM %1 = repo (no tag), %2 = build context dir, %3 = Dockerfile path
set "REPO=%~1"
set "CTX=%~2"
set "DF=%~3"

echo.
echo [STEP] Build %REPO%:%TAG%
docker build %BUILD_ARGS% -f "%DF%" -t "%REPO%:%TAG%" "%CTX%"
if errorlevel 1 exit /b 1

if "%PUSH_LATEST%"=="1" (
  echo [STEP] Tag %REPO%:latest
  docker tag "%REPO%:%TAG%" "%REPO%:latest"
  if errorlevel 1 exit /b 1
)

echo [STEP] Push %REPO%:%TAG%
docker push "%REPO%:%TAG%"
if errorlevel 1 exit /b 1

if "%PUSH_LATEST%"=="1" (
  echo [STEP] Push %REPO%:latest
  docker push "%REPO%:latest"
  if errorlevel 1 exit /b 1
)

exit /b 0

:DockerLogin
if defined DOCKER_USERNAME (
  if defined DOCKER_PASSWORD (
    echo [STEP] Docker login (non-interactive) to %REGISTRY% as %DOCKER_USERNAME%
    powershell -NoProfile -Command "$p=$env:DOCKER_PASSWORD; if ([string]::IsNullOrEmpty($p)) { exit 2 } ; $p | docker login '%REGISTRY%' -u '%DOCKER_USERNAME%' --password-stdin"
    if errorlevel 1 exit /b 1
    exit /b 0
  )

  echo [STEP] Docker login (interactive) to %REGISTRY% as %DOCKER_USERNAME%
  docker login %REGISTRY% -u %DOCKER_USERNAME%
  if errorlevel 1 exit /b 1
  exit /b 0
)

echo [STEP] Docker login (interactive) to %REGISTRY%
docker login %REGISTRY%
if errorlevel 1 exit /b 1
exit /b 0

:error
echo.
echo [ERROR] Build/push failed. See logs above.
popd
exit /b 1
