@echo off
chcp 936 >nul
setlocal
cd /d "%~dp0"
title DISP-MES 界面原型 - 本地启动器

echo ==================================================
echo          DISP-MES 界面原型 - 本地启动器
echo ==================================================
echo.

if not exist "package.json" (
    echo [错误] 当前目录下没有找到 package.json。
    echo         请把本脚本放在工程根目录（与 package.json 同级）后再运行。
    goto :fail
)

where node >nul 2>nul
if errorlevel 1 (
    echo [错误] 未检测到 Node.js。请先安装 Node.js 20 或更高版本：https://nodejs.org/
    goto :fail
)

where npm >nul 2>nul
if errorlevel 1 (
    echo [错误] 未检测到 npm。建议重新安装 Node.js，并确保勾选 Add to PATH。
    goto :fail
)

echo [环境] Node.js 版本：
call node -v

if not exist "node_modules\.bin\vite.cmd" (
    echo.
    echo [1/2] 首次运行，正在安装依赖，可能需要 1-3 分钟，请勿关闭本窗口...
    echo.
    call npm ci --no-fund --no-audit
    if errorlevel 1 (
        echo.
        echo [提示] npm ci 未成功（通常是锁定文件与 package.json 不同步），改用 npm install 重试...
        echo.
        call npm install --no-fund --no-audit
        if errorlevel 1 (
            echo.
            echo [错误] 依赖安装失败，一般是网络问题。可先执行下面这条命令换成国内镜像：
            echo        npm config set registry https://registry.npmmirror.com
            echo        然后删除 node_modules 目录，再重新运行本脚本。
            goto :fail
        )
    )
) else (
    echo [1/2] 依赖已存在，跳过安装。
)

if not exist "node_modules\.bin\vite.cmd" (
    echo [错误] 安装后仍未找到 vite，请删除 node_modules 目录后重新运行本脚本。
    goto :fail
)

echo.
echo [2/2] 正在启动开发服务器...
echo       访问地址：http://127.0.0.1:5173/#/login
echo       停止服务：直接关闭本窗口，或按 Ctrl+C 后输入 Y 回车。
echo.
call npm run dev
goto :end

:fail
echo.
echo 启动失败。请把本窗口的完整错误信息反馈给技术支持。

:end
echo.
pause
endlocal
