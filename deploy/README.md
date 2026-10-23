# DISP-EMS 本地与离线部署

## 本机联调（可联网）

1. 安装并启动 Docker Desktop（Linux containers / WSL 2）。
2. 在项目根目录执行 `Copy-Item deploy/.env.example deploy/.env`，修改所有密码、`PUBLIC_ORIGIN` 和 `MINIO_PUBLIC_ENDPOINT`。`PUBLIC_ORIGIN` 必须包含浏览器实际访问的地址；本机模板已包含 `localhost` 与 `127.0.0.1`。现场填写浏览器可访问的服务器内网地址，例如 `http://192.168.1.10:9000`。
3. 执行 `docker compose up -d --build`。
4. 等待 `docker compose ps` 中所有服务为运行状态后，访问 `http://localhost:8089`；MinIO S3 接口为 `http://localhost:9000`，控制台为 `http://localhost:9001`。

首次启动会自动创建数据库结构、对象桶、默认工位、投影模板和由 `BOOTSTRAP_ADMIN_*` 指定的超级管理员。确认管理员可以登录后，应在管理端修改初始密码。MinIO 上游已停止公开维护容器镜像，因此本部署锁定使用兼容的社区维护镜像；其数据协议和 Java MinIO SDK 均保持 S3 兼容。

常用排障命令：

```powershell
docker compose ps
docker compose logs -f server
docker compose down
```

`docker compose down` 不会删除数据库或文件；只有明确执行 `docker compose down -v` 才会清空本机业务数据。

## 打包为现场离线安装包

联网的构建机完成联调后，在项目根目录执行：

```powershell
.\deploy\export-offline-images.ps1
```

将生成的 `deploy/offline/` 整个目录复制到现场服务器。该目录包含 MySQL、Redis、MinIO、后端和前端的全部镜像，不依赖现场联网。

## 现场离线安装

现场服务器需要预装 Docker Engine 与 Docker Compose 插件（无需登录 Docker Hub）。将离线包复制到任意目录后：

```powershell
Copy-Item .env.example .env
# 编辑 .env：必须替换所有密码、管理员初始密码、WEB_PORT、PUBLIC_ORIGIN，并将 MINIO_PUBLIC_ENDPOINT 改为现场服务器的内网地址
.\import-offline-images.ps1
```

脚本导入镜像并后台启动服务。Windows Server 可在 PowerShell 运行；Linux 服务器可运行等价命令：`docker load -i disp-ems-images.tar`，再执行 `docker compose up -d --no-build`。

生产环境应定期备份 Docker volumes `mysql_data` 与 `minio_data`，并将 `deploy/.env` 单独保管，绝不可提交到 Git。
