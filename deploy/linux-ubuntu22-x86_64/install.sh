#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR/app"
RUNTIME_DEBS="$SCRIPT_DIR/runtime-debs"
ENGINE_ARCHIVE="$SCRIPT_DIR/docker-engine.tgz"
COMPOSE_PLUGIN="$SCRIPT_DIR/docker-compose"
HOST_ADDRESS=""

fail() { echo "ERROR: $*" >&2; exit 1; }
note() { echo "==> $*"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) HOST_ADDRESS="${2:-}"; shift 2 ;;
    --help|-h) echo "Usage: sudo bash install.sh [--host SERVER_IP_OR_DNS]"; exit 0 ;;
    *) fail "Unknown argument: $1" ;;
  esac
done

[[ "${EUID}" -eq 0 ]] || fail "Run as root, for example: sudo bash install.sh --host 192.168.1.10"
[[ -f /etc/os-release ]] || fail "Only Ubuntu 22.04 x86_64 is supported by this package"
. /etc/os-release
[[ "${ID:-}" == "ubuntu" && "${VERSION_ID:-}" == "22.04" ]] || fail "This package requires Ubuntu 22.04"
[[ "$(uname -m)" == "x86_64" ]] || fail "This package requires x86_64; detected $(uname -m)"
[[ -f "$ENGINE_ARCHIVE" && -f "$COMPOSE_PLUGIN" && -d "$APP_DIR" ]] || fail "The offline package is incomplete"

if [[ -z "$HOST_ADDRESS" ]]; then
  HOST_ADDRESS="$(hostname -I 2>/dev/null | awk '{print $1}')"
fi
[[ -n "$HOST_ADDRESS" ]] || fail "Cannot determine the server address. Re-run with --host <server-ip-or-dns>"

install_runtime_prerequisites() {
  command -v iptables >/dev/null 2>&1 && command -v ip >/dev/null 2>&1 && return 0
  [[ -d "$RUNTIME_DEBS" ]] || fail "Missing Ubuntu runtime prerequisite packages"
  note "Installing bundled Ubuntu networking prerequisites"
  dpkg -i "$RUNTIME_DEBS"/*.deb || true
  dpkg --configure -a || true
  command -v iptables >/dev/null 2>&1 || fail "iptables is unavailable after offline prerequisite installation"
  command -v ip >/dev/null 2>&1 || fail "iproute2 is unavailable after offline prerequisite installation"
}

install_docker_engine() {
  if command -v docker >/dev/null 2>&1 && docker version >/dev/null 2>&1; then
    note "A working Docker Engine is already installed"
    return 0
  fi
  install_runtime_prerequisites
  note "Installing bundled Docker Engine"
  local temp_dir
  temp_dir="$(mktemp -d)"
  trap 'rm -rf "$temp_dir"' EXIT
  tar -xzf "$ENGINE_ARCHIVE" -C "$temp_dir"
  install -m 0755 "$temp_dir/docker/"* /usr/local/bin/
  rm -rf "$temp_dir"
  trap - EXIT
  getent group docker >/dev/null 2>&1 || groupadd --system docker
  cat >/etc/systemd/system/docker.service <<'UNIT'
[Unit]
Description=Docker Application Container Engine (offline static installation)
Documentation=https://docs.docker.com/engine/install/binaries/
After=network-online.target
Wants=network-online.target

[Service]
Type=notify
ExecStart=/usr/local/bin/dockerd --host=unix:///var/run/docker.sock --userland-proxy-path=/usr/local/bin/docker-proxy
ExecReload=/bin/kill -s HUP $MAINPID
TimeoutStartSec=0
Restart=always
RestartSec=2
LimitNOFILE=infinity
TasksMax=infinity
Delegate=yes
KillMode=process

[Install]
WantedBy=multi-user.target
UNIT
  systemctl daemon-reload
  systemctl enable --now docker
}

install_compose_plugin() {
  note "Installing bundled Docker Compose plugin"
  install -d -m 0755 /usr/local/lib/docker/cli-plugins
  install -m 0755 "$COMPOSE_PLUGIN" /usr/local/lib/docker/cli-plugins/docker-compose
  docker compose version >/dev/null || fail "Docker Compose plugin installation failed"
}

wait_for_docker() {
  local attempt
  for attempt in $(seq 1 30); do
    if docker info >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  systemctl status docker --no-pager || true
  fail "Docker Engine did not become ready"
}

configure_application() {
  local env_file="$APP_DIR/.env"
  if [[ -f "$env_file" ]]; then
    note "Keeping existing application configuration: $env_file"
    return 0
  fi
  note "Creating application configuration for $HOST_ADDRESS"
  cp "$APP_DIR/.env.example" "$env_file"
  # 现场统一使用用户指定的固定口令；如需变更，请在首次运行前编辑 app/.env。
  local fixed_password='dispadmin'
  sed -i \
    -e "s|^MYSQL_PASSWORD=.*|MYSQL_PASSWORD=$fixed_password|" \
    -e "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=$fixed_password|" \
    -e "s|^REDIS_PASSWORD=.*|REDIS_PASSWORD=$fixed_password|" \
    -e "s|^MINIO_ROOT_PASSWORD=.*|MINIO_ROOT_PASSWORD=$fixed_password|" \
    -e "s|^BOOTSTRAP_ADMIN_PASSWORD=.*|BOOTSTRAP_ADMIN_PASSWORD=$fixed_password|" \
    -e "s|^MINIO_PUBLIC_ENDPOINT=.*|MINIO_PUBLIC_ENDPOINT=http://$HOST_ADDRESS:9000|" \
    -e "s|^PUBLIC_ORIGIN=.*|PUBLIC_ORIGIN=http://$HOST_ADDRESS:8089|" \
    "$env_file"
  chmod 600 "$env_file"
  echo
  echo "Initial administrator account: ADMIN-001"
  echo "Initial administrator password: $fixed_password"
  echo
}

install_docker_engine
wait_for_docker
install_compose_plugin
configure_application

note "Importing DISP-EMS images"
docker load -i "$APP_DIR/disp-ems-images.tar"
note "Starting DISP-EMS"
docker compose --project-name disp-ems --env-file "$APP_DIR/.env" -f "$APP_DIR/docker-compose.yml" up -d --no-build
docker compose --project-name disp-ems --env-file "$APP_DIR/.env" -f "$APP_DIR/docker-compose.yml" ps

echo
echo "DISP-EMS has been deployed. Open: http://$HOST_ADDRESS:8089"
echo "MinIO console: http://$HOST_ADDRESS:9001"
