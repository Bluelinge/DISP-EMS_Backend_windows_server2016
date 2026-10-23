# DISP-EMS Windows Server 2016 x64 离线一键部署包

本方案以 Windows Server 2016 物理服务器作为 Hyper-V 宿主机，在包内 Ubuntu 22.04 虚拟机中运行 DISP-EMS。目标服务器部署和升级过程不需要连接互联网。

## 构建离线包

构建机需要联网、已安装 Docker Desktop、已生成 `deploy/offline-linux-ubuntu22-x86_64/`，并拥有客户同版本 Windows Server 2016 ISO。你提供的 ISO 可这样构建：

```powershell
.\deploy\windows-server-2016-x64\build-windows-server-2016-offline.ps1 `
  -WindowsIsoPath 'E:\WEB\Github\14393.0.161119-1705.RS1_REFRESH_SERVER_EVAL_X64FRE_ZH-CN.ISO' `
  -RefreshImages
```

构建器会提取 ISO 中的 `sources\sxs` 和 `sources\install.wim`，并生成：

```text
deploy/windows-server-2016-x64/offline/
deploy/DISP-EMS-Windows-Server-2016-x64-offline.zip
```

最终包包括 Ubuntu Hyper-V VHDX、Docker Engine、Docker Compose、所有项目镜像、Windows Hyper-V 离线组件源、数据库/缓存/对象存储配置和安装脚本。不要只复制脚本，必须复制完整 ZIP 或完整 `offline` 目录。

## 首次部署

在 Windows Server 2016 上将 ZIP 解压后，右键以管理员身份运行 `install.bat`。脚本会询问服务器内网 IPv4，也可以把地址作为参数传入：`install.bat 192.168.1.10`。如果不方便使用批处理，也可以在管理员 PowerShell 中执行：

```powershell
Expand-Archive .\DISP-EMS-Windows-Server-2016-x64-offline.zip -DestinationPath C:\DISP-EMS-Offline
Set-ExecutionPolicy Bypass -Scope Process -Force
Set-Location C:\DISP-EMS-Offline
.\install.ps1 -HostAddress 192.168.1.10
```

`HostAddress` 使用车间浏览器能够访问的服务器内网 IPv4。脚本会检查并离线安装 Hyper-V、创建内部网络和 Ubuntu 虚拟机、安装 Docker、导入项目镜像、初始化数据库和 MinIO，并转发 `8089`、`9000`、`9001` 端口。

如果 Hyper-V 已启用，脚本直接继续；如果未启用，则优先从包内 `windows-source\install.wim` 自动匹配系统版本、版本类型和安装类型后离线启用 Hyper-V，并保留 `sources\sxs`。Server 2016 介质的 `sources\sxs` 可能仅含 .NET 3.5 CAB，因此同时打包 WIM 功能源。系统要求重启时会自动注册开机续跑任务。

部署完成后访问：

```text
http://192.168.1.10:8089
```

初始管理员：`ADMIN-001`，初始密码：`dispadmin`。首次登录后应立即修改密码。

## 后续升级与数据保留

把新版离线包解压到服务器后，在新版包目录执行同一个安装命令：

```powershell
.\install.ps1 -HostAddress 192.168.1.10
```

检测到已有 `DISP-EMS` VM 时，脚本会：

- 创建 Hyper-V 升级检查点；
- 停止 VM，替换只读更新 seed，重新启动 VM；
- 在 Ubuntu 内加载新版项目镜像并执行 `docker compose up -d --no-build`；
- 保留 Ubuntu 虚拟磁盘、MySQL、Redis、MinIO、Docker volumes、业务文件和现场 `.env`。

升级不会执行 `docker compose down -v`，也不会删除 `C:\ProgramData\DISP-EMS`。如果发现数据目录存在但 VM 不存在，脚本会停止并报警，拒绝初始化新环境覆盖旧数据。

## 运维命令

```powershell
.\manage.ps1 -Action status
.\manage.ps1 -Action start
.\manage.ps1 -Action stop
.\manage.ps1 -Action restart
.\manage.ps1 -Action backup
.\manage.ps1 -Action upgrade -HostAddress 192.168.1.10
```

`backup` 创建 Hyper-V 检查点。`uninstall` 只移除 VM 注册、网络、端口转发和防火墙规则，保留 `C:\ProgramData\DISP-EMS` 下的虚拟磁盘与检查点；不要手动删除该目录。

## 目标机要求

- Windows Server 2016 Standard/Datacenter x64。
- 物理机 BIOS/UEFI 已开启 Intel VT-x 或 AMD-V。
- 至少 8 GB 内存，建议 16 GB。
- 至少 30 GB 可用空间，生产环境建议 80 GB 以上。
- 本地管理员权限。
- 包内 Windows 组件源必须来自与目标机匹配的 Windows Server 2016 安装介质。

## 校验与故障定位

验证包文件：

```powershell
Get-FileHash .\DISP-EMS-Windows-Server-2016-x64-offline.zip -Algorithm SHA256
Get-Content .\SHA256SUMS
```

查看 VM 状态：

```powershell
.\manage.ps1 -Action status
```

如果服务尚未就绪，等待几分钟后再次访问；应用日志和容器状态位于 Ubuntu VM 内的 `/opt/disp-ems/app` 与 Docker 服务中。
