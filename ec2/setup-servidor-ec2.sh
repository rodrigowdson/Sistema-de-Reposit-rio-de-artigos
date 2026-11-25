#!/usr/bin/env bash
set -euo pipefail

DEPLOY_TMP="/tmp/deploy-repositorio"
APP_DIR="/opt/repositorio-tcc"
WEB_DIR="/var/www/repositorio-tcc"
JAR_NAME="sistema-repositorio-artigos-1.0.0.jar"

if [[ ! -d "$DEPLOY_TMP" ]]; then
  echo "Erro: pasta $DEPLOY_TMP não encontrada. Execute a partir do script deploy-ec2."
  exit 1
fi

# Carrega parâmetros enviados pelo deploy local
if [[ -f "$DEPLOY_TMP/deploy-params.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$DEPLOY_TMP/deploy-params.env"
  set +a
fi

STORAGE_LOCAL_DIR="${STORAGE_LOCAL_DIR:-/var/lib/repositorio-tcc/storage}"
DB_PASSWORD="${DB_PASSWORD:-repositorio_dev}"
APP_ADMIN_NOME="${APP_ADMIN_NOME:-Administrador}"
APP_ADMIN_EMAIL="${APP_ADMIN_EMAIL:-admin@repositorio.local}"
APP_ADMIN_PASSWORD="${APP_ADMIN_PASSWORD:-troque-esta-senha-admin}"
UPLOAD_MAX_FILE_SIZE="${UPLOAD_MAX_FILE_SIZE:-100MB}"
UPLOAD_MAX_REQUEST_SIZE="${UPLOAD_MAX_REQUEST_SIZE:-110MB}"
RUN_USER="$(whoami)"

echo "==> [1/6] Verificando dependências na EC2 (Java 21+, Nginx, Docker)..."

if command -v apt-get >/dev/null 2>&1; then
  PKG_MGR="apt"
elif command -v dnf >/dev/null 2>&1; then
  PKG_MGR="dnf"
elif command -v yum >/dev/null 2>&1; then
  PKG_MGR="yum"
else
  echo "Gerenciador de pacotes não suportado."
  exit 1
fi

instalar_java_se_preciso() {
  local versao=0
  if command -v java >/dev/null 2>&1; then
    versao="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print ($2 == "1" ? $3 : $2); exit}')"
  fi
  if [[ -z "$versao" || "$versao" -lt 21 ]]; then
    echo "    Instalando Java 21 LTS..."
    local instalado=false
    if [[ "$PKG_MGR" == "apt" ]]; then
      sudo apt-get update -qq
      if sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-21-jre-headless 2>/dev/null; then
        instalado=true
      fi
    else
      if sudo "$PKG_MGR" install -y -q java-21-amazon-corretto-headless 2>/dev/null \
         || sudo "$PKG_MGR" install -y -q java-21-openjdk-headless 2>/dev/null; then
        instalado=true
      fi
    fi

    if [[ "$instalado" != "true" ]]; then
      echo "    Pacote Java 21 não disponível no repositório padrão; instalando Eclipse Temurin 21 JRE..."
      local arch="x64"
      [[ "$(uname -m)" == "aarch64" ]] && arch="aarch64"
      sudo mkdir -p /opt/java-21
      curl -fsSL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/${arch}/jre/hotspot/normal/eclipse" \
        -o /tmp/temurin21.tar.gz
      sudo tar -xzf /tmp/temurin21.tar.gz -C /opt/java-21 --strip-components=1
      rm -f /tmp/temurin21.tar.gz
      sudo ln -sf /opt/java-21/bin/java /usr/local/bin/java
    fi
  fi
}

instalar_nginx_e_ferramentas() {
  if ! command -v nginx >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1 || ! command -v openssl >/dev/null 2>&1; then
    echo "    Instalando Nginx, curl e openssl..."
    if [[ "$PKG_MGR" == "apt" ]]; then
      sudo apt-get update -qq
      sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nginx curl openssl tar
    else
      sudo "$PKG_MGR" install -y -q nginx curl openssl tar
    fi
  fi
}

instalar_docker_se_preciso() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "    Instalando Docker..."
    if [[ "$PKG_MGR" == "apt" ]]; then
      sudo apt-get update -qq
      sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq docker.io
      sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq docker-compose-v2 || sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq docker-compose-plugin || true
    else
      sudo "$PKG_MGR" install -y -q docker
    fi
  fi

  sudo systemctl enable --now docker
  sudo usermod -aG docker "$RUN_USER" || true

  if ! sudo docker compose version >/dev/null 2>&1; then
    echo "    Instalando plugin Docker Compose..."
    local arch
    arch="$(uname -m)"
    sudo mkdir -p /usr/local/lib/docker/cli-plugins
    sudo curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${arch}" \
      -o /usr/local/lib/docker/cli-plugins/docker-compose
    sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
  fi
}

instalar_nginx_e_ferramentas
instalar_java_se_preciso
instalar_docker_se_preciso

echo "==> [2/6] Preparando diretórios de aplicação e storage de PDFs..."
sudo mkdir -p "$APP_DIR" "$STORAGE_LOCAL_DIR" "$WEB_DIR"
sudo chown -R "$RUN_USER:$RUN_USER" "$APP_DIR" "$(dirname "$STORAGE_LOCAL_DIR")" "$STORAGE_LOCAL_DIR"
chmod 755 "$STORAGE_LOCAL_DIR"

cp "$DEPLOY_TMP/$JAR_NAME" "$APP_DIR/$JAR_NAME"
cp "$DEPLOY_TMP/docker-compose.yml" "$APP_DIR/docker-compose.yml"

# Mantém JWT_SECRET existente se já houver .env; caso contrário gera um segredo forte
JWT_SECRET_ATUAL=""
if [[ -f "$APP_DIR/.env" ]]; then
  JWT_SECRET_ATUAL="$(grep -E '^JWT_SECRET=' "$APP_DIR/.env" | head -n1 | cut -d'=' -f2- || true)"
fi
if [[ -z "$JWT_SECRET_ATUAL" || "$JWT_SECRET_ATUAL" == troque-* ]]; then
  JWT_SECRET_ATUAL="$(openssl rand -hex 32)"
fi

cat > "$APP_DIR/.env" <<EOF
SERVER_PORT=8080
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=repositorio_tcc
DB_USERNAME=repositorio
DB_PASSWORD=${DB_PASSWORD}
DB_USE_SSL=false
STORAGE_TYPE=local
STORAGE_LOCAL_DIR=${STORAGE_LOCAL_DIR}
UPLOAD_MAX_FILE_SIZE=${UPLOAD_MAX_FILE_SIZE}
UPLOAD_MAX_REQUEST_SIZE=${UPLOAD_MAX_REQUEST_SIZE}
JWT_SECRET=${JWT_SECRET_ATUAL}
JWT_EXPIRATION_HOURS=24
APP_ADMIN_NOME=${APP_ADMIN_NOME}
APP_ADMIN_EMAIL=${APP_ADMIN_EMAIL}
APP_ADMIN_PASSWORD=${APP_ADMIN_PASSWORD}
EOF
chmod 600 "$APP_DIR/.env"

echo "==> [3/6] Subindo banco de dados MySQL 8 via Docker Compose..."
(
  cd "$APP_DIR"
  sudo docker compose --env-file "$APP_DIR/.env" up -d
)

echo "    Aguardando MySQL ficar pronto..."
for i in $(seq 1 30); do
  estado="$(sudo docker inspect -f '{{.State.Health.Status}}' repositorio-mysql 2>/dev/null || echo "starting")"
  if [[ "$estado" == "healthy" ]]; then
    echo "    MySQL pronto!"
    break
  fi
  if [[ "$i" -eq 30 ]]; then
    echo "    Aviso: MySQL demorou mais que o esperado (status: $estado). Continuando..."
  fi
  sleep 2
done

echo "==> [4/6] Publicando frontend Angular e configurando Nginx..."
sudo rm -rf "${WEB_DIR:?}"/*
sudo tar -xzf "$DEPLOY_TMP/frontend-dist.tar.gz" -C "$WEB_DIR"
sudo chown -R root:root "$WEB_DIR"
sudo find "$WEB_DIR" -type d -exec chmod 755 {} \;
sudo find "$WEB_DIR" -type f -exec chmod 644 {} \;

if [[ -d /etc/nginx/sites-available ]]; then
  sudo cp "$DEPLOY_TMP/repositorio-tcc.conf" /etc/nginx/sites-available/repositorio-tcc
  sudo ln -sf /etc/nginx/sites-available/repositorio-tcc /etc/nginx/sites-enabled/repositorio-tcc
  sudo rm -f /etc/nginx/sites-enabled/default
else
  sudo cp "$DEPLOY_TMP/repositorio-tcc.conf" /etc/nginx/conf.d/repositorio-tcc.conf
  if [[ -f /etc/nginx/nginx.conf ]]; then
    sudo sed -i 's/listen\s*80\s*default_server;/listen 8089;/g; s/listen\s*\[::\]:80\s*default_server;/listen [::]:8089;/g' /etc/nginx/nginx.conf || true
  fi
fi

sudo nginx -t
sudo systemctl enable --now nginx
sudo systemctl reload nginx

echo "==> [5/6] Configurando e iniciando serviço systemd do backend..."
sudo tee /etc/systemd/system/repositorio-tcc.service >/dev/null <<EOF
[Unit]
Description=Sistema de Repositorio de TCCs e Artigos
After=network.target docker.service
Wants=docker.service

[Service]
Type=simple
User=${RUN_USER}
WorkingDirectory=${APP_DIR}
EnvironmentFile=${APP_DIR}/.env
ExecStart=$(command -v java) -jar ${APP_DIR}/${JAR_NAME}
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable repositorio-tcc.service
sudo systemctl restart repositorio-tcc.service

echo "==> [6/6] Validando saúde da aplicação (/status)..."
OK=false
for i in $(seq 1 30); do
  RES="$(curl -fsS http://127.0.0.1:8080/status 2>/dev/null || true)"
  if echo "$RES" | grep -q '"status":"UP"'; then
    echo "    Aplicação online: $RES"
    OK=true
    break
  fi
  sleep 2
done

rm -rf "$DEPLOY_TMP"

if [[ "$OK" != "true" ]]; then
  echo "Erro: a aplicação não respondeu UP em http://127.0.0.1:8080/status."
  echo "Últimos logs do serviço:"
  sudo journalctl -u repositorio-tcc.service -n 40 --no-pager || true
  exit 1
fi

echo "==> Deploy concluído com sucesso! Storage pronto em: $STORAGE_LOCAL_DIR"
