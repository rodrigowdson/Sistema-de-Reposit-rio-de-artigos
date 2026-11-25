#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$SCRIPT_DIR/deploy.env"
ENV_EXAMPLE="$SCRIPT_DIR/deploy.env.example"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  echo "Criei o arquivo ec2/deploy.env a partir do modelo."
  echo "Preencha EC2_HOST e coloque sua chave .pem em ec2/chaves/ (ou informe EC2_KEY)."
  echo "Depois execute novamente: ./ec2/deploy-ec2.sh"
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

EC2_USER="${EC2_USER:-ubuntu}"
EC2_HOST="${EC2_HOST:-}"
EC2_KEY="${EC2_KEY:-}"

if [[ -z "$EC2_KEY" ]]; then
  mapfile -t PEMS < <(find "$SCRIPT_DIR/chaves" -maxdepth 1 -type f -name '*.pem' 2>/dev/null || true)
  if [[ "${#PEMS[@]}" -eq 1 ]]; then
    EC2_KEY="${PEMS[0]}"
  fi
fi

EC2_KEY="${EC2_KEY/#\~/$HOME}"
if [[ -n "$EC2_KEY" && ! -f "$EC2_KEY" && -f "$REPO_ROOT/$EC2_KEY" ]]; then
  EC2_KEY="$REPO_ROOT/$EC2_KEY"
fi

if [[ -z "$EC2_HOST" || "$EC2_HOST" == "0.0.0.0" ]]; then
  echo "Erro: preencha EC2_HOST com o IP público ou DNS real da EC2 em ec2/deploy.env."
  exit 1
fi

if [[ -z "$EC2_KEY" || ! -f "$EC2_KEY" ]]; then
  echo "Erro: chave .pem não encontrada. Coloque-a em ec2/chaves/ ou configure EC2_KEY em ec2/deploy.env."
  exit 1
fi

chmod 400 "$EC2_KEY"

echo "==> Gerando JAR do backend com Gradle..."
(cd "$REPO_ROOT" && ./gradlew bootJar --no-daemon)

echo "==> Gerando build de produção do frontend Angular..."
(
  cd "$REPO_ROOT/frontend"
  [[ -d node_modules ]] || npm install
  npm run build
)

TMP_BUNDLE="$(mktemp -d)"
trap 'rm -rf "$TMP_BUNDLE"' EXIT

tar -czf "$TMP_BUNDLE/frontend-dist.tar.gz" -C "$REPO_ROOT/frontend/dist/repositorio-tcc/browser" .

cat > "$TMP_BUNDLE/deploy-params.env" <<EOF
DB_PASSWORD=${DB_PASSWORD:-repositorio_dev}
STORAGE_LOCAL_DIR=${STORAGE_LOCAL_DIR:-/var/lib/repositorio-tcc/storage}
APP_ADMIN_NOME=${APP_ADMIN_NOME:-Administrador}
APP_ADMIN_EMAIL=${APP_ADMIN_EMAIL:-admin@repositorio.local}
APP_ADMIN_PASSWORD=${APP_ADMIN_PASSWORD:-troque-esta-senha-admin}
UPLOAD_MAX_FILE_SIZE=${UPLOAD_MAX_FILE_SIZE:-100MB}
UPLOAD_MAX_REQUEST_SIZE=${UPLOAD_MAX_REQUEST_SIZE:-110MB}
EOF

REMOTE="$EC2_USER@$EC2_HOST"
SSH_OPTS=(-i "$EC2_KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15)

echo "==> Conectando na EC2 ($REMOTE)..."
ssh "${SSH_OPTS[@]}" "$REMOTE" "rm -rf /tmp/deploy-repositorio && mkdir -p /tmp/deploy-repositorio"

echo "==> Enviando artefatos para a EC2..."
scp "${SSH_OPTS[@]}" \
  "$REPO_ROOT/build/libs/sistema-repositorio-artigos-1.0.0.jar" \
  "$TMP_BUNDLE/frontend-dist.tar.gz" \
  "$TMP_BUNDLE/deploy-params.env" \
  "$REPO_ROOT/docker-compose.yml" \
  "$REPO_ROOT/frontend/nginx/repositorio-tcc.conf" \
  "$SCRIPT_DIR/setup-servidor-ec2.sh" \
  "$REMOTE:/tmp/deploy-repositorio/"

echo "==> Executando configuração na EC2..."
ssh "${SSH_OPTS[@]}" "$REMOTE" "sed -i 's/\r$//' /tmp/deploy-repositorio/setup-servidor-ec2.sh /tmp/deploy-repositorio/deploy-params.env && chmod +x /tmp/deploy-repositorio/setup-servidor-ec2.sh && bash /tmp/deploy-repositorio/setup-servidor-ec2.sh"

echo ""
echo "================================================================="
echo "  DEPLOY CONCLUÍDO NA AWS EC2!"
echo "  Acesse: http://$EC2_HOST"
echo "  Para enviar os livros da pasta livros/: ./ec2/enviar-livros.sh"
echo "================================================================="
