#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/backup.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Arquivo $ENV_FILE não encontrado."
  echo "Crie a partir do modelo: cp aws-backup/backup.env.example aws-backup/backup.env"
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

EC2_KEY="${EC2_KEY/#\~/$HOME}"
LOCAL_STORAGE_PATH="$(cd "$SCRIPT_DIR" && realpath -m "$LOCAL_STORAGE_DIR")"
SSH_OPTS=(-i "$EC2_KEY" -o StrictHostKeyChecking=accept-new)
REMOTE="$EC2_USER@$EC2_HOST"

if [[ ! -f "$EC2_KEY" ]]; then
  echo "Chave SSH não encontrada em: $EC2_KEY"
  exit 1
fi

if [[ ! -d "$LOCAL_STORAGE_PATH" ]]; then
  echo "Pasta local de PDFs não encontrada em: $LOCAL_STORAGE_PATH"
  echo "Crie a pasta ou envie pelo menos um PDF antes de rodar o backup."
  exit 1
fi

echo "==> Criando pasta de backup na EC2 ($REMOTE:$EC2_BACKUP_DIR)..."
ssh "${SSH_OPTS[@]}" "$REMOTE" "mkdir -p '$EC2_BACKUP_DIR/arquivos' '$EC2_BACKUP_DIR/banco'"

echo "==> Enviando arquivos PDF de $LOCAL_STORAGE_PATH para a EC2..."
if command -v rsync >/dev/null 2>&1; then
  rsync -avz -e "ssh ${SSH_OPTS[*]}" "$LOCAL_STORAGE_PATH/" "$REMOTE:$EC2_BACKUP_DIR/arquivos/"
else
  scp -r "${SSH_OPTS[@]}" "$LOCAL_STORAGE_PATH/." "$REMOTE:$EC2_BACKUP_DIR/arquivos/"
fi

if [[ "${BACKUP_DB:-false}" == "true" ]]; then
  DATA="$(date +%Y%m%d_%H%M%S)"
  DUMP_TMP="/tmp/repositorio_tcc_${DATA}.sql.gz"
  DUMP_CMD="$(command -v mariadb-dump || command -v mysqldump || true)"

  if [[ -z "$DUMP_CMD" ]]; then
    echo "Aviso: mysqldump/mariadb-dump não instalado localmente. Pulando backup do banco."
  else
    echo "==> Gerando dump do banco $DB_NAME..."
    "$DUMP_CMD" -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" | gzip > "$DUMP_TMP"
    echo "==> Enviando dump do banco para a EC2..."
    scp "${SSH_OPTS[@]}" "$DUMP_TMP" "$REMOTE:$EC2_BACKUP_DIR/banco/"
    rm -f "$DUMP_TMP"
  fi
fi

echo "==> Backup concluído com sucesso em $REMOTE:$EC2_BACKUP_DIR"
