# DISP-EMS Ubuntu 22.04 x86_64 离线一键部署包

本包适用于 **Ubuntu 22.04 x86_64**，不需要目标服务器连接互联网。它包含 Docker Engine 静态发行版、Docker Compose 插件、Docker 运行所需的 Ubuntu 网络依赖，以及 DISP-EMS 全部镜像。

## 部署

1. 将 `disp-ems-ubuntu22-x86_64-offline.tar` 复制到服务器后解压，或直接复制整个目录：

```bash
mkdir -p /opt/disp-ems-offline
tar -xf disp-ems-ubuntu22-x86_64-offline.tar -C /opt/disp-ems-offline --strip-components=1
```

2. 以 root 执行（将 IP 替换为服务器供车间浏览器访问的内网地址）：

```bash
cd /opt/disp-ems-offline
sudo bash install.sh --host 192.168.1.10
```

首次安装会将 MySQL、Redis、MinIO 和初始超级管理员密码统一配置为 `dispadmin`。初始超级管理员工号为 `ADMIN-001`。已存在 `app/.env` 时，脚本不会覆盖现场配置或数据。

部署完成后访问 `http://服务器IP:8089`。查看状态：

```bash
docker compose --project-name disp-ems --env-file app/.env -f app/docker-compose.yml ps
```

## 停止与启动服务

日常停止（保留容器和所有业务数据）：

```bash
docker compose --project-name disp-ems --env-file app/.env -f app/docker-compose.yml stop
```

重新启动：

```bash
docker compose --project-name disp-ems --env-file app/.env -f app/docker-compose.yml start
```

需要停止并移除容器时可执行 `down`；不要追加 `-v`，否则会删除 MySQL、MinIO 等业务数据卷：

```bash
docker compose --project-name disp-ems --env-file app/.env -f app/docker-compose.yml down
```

## 升级

保留现场的 `app/.env` 以及 Docker volumes。将新版离线包解压到新目录后，把旧目录的 `app/.env` 复制到新目录，再运行同一安装命令即可。不要执行 `docker compose down -v`，该命令会删除业务数据。

## 前置条件

- Ubuntu 22.04 x86_64、systemd、64 位 Linux 内核。
- 使用 root 或具备 sudo 权限的账号。
- 服务器防火墙须允许车间访问 TCP 8089；需要直接访问 MinIO 时再允许 TCP 9000/9001。
