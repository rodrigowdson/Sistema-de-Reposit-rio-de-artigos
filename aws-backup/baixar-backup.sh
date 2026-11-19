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
DESTINO="${1:-$SCRIPT_DIR/restaurado}"
SSH_OPTS=(-i "$EC2_KEY" -o StrictHostKeyChecking=accept-new)
REMOTE="$EC2_USER@$EC2_HOST"

if [[ ! -f "$EC2_KEY" ]]; then
  echo "Chave SSH não encontrada em: $EC2_KEY"
  exit 1
fi

mkdir -p "$DESTINO/arquivos" "$DESTINO/banco"

echo "==> Baixando backup de $REMOTE:$EC2_BACKUP_DIR para $DESTINO..."
if command -v rsync >/dev/null 2>&1; then
  rsync -avz -e "ssh ${SSH_OPTS[*]}" "$REMOTE:$EC2_BACKUP_DIR/arquivos/" "$DESTINO/arquivos/"
  rsync -avz -e "ssh ${SSH_OPTS[*]}" "$REMOTE:$EC2_BACKUP_DIR/banco/" "$DESTINO/banco/" 2>/dev/null || true
else
  scp -r "${SSH_OPTS[@]}" "$REMOTE:$EC2_BACKUP_DIR/arquivos/." "$DESTINO/arquivos/"
  scp -r "${SSH_OPTS[@]}" "$REMOTE:$EC2_BACKUP_DIR/banco/." "$DESTINO/banco/" 2>/dev/null || true
fi

echo "==> Download do backup concluído em: $DESTINO"
echo "    Para restaurar os PDFs na aplicação local, copie '$DESTINO/arquivos/' para '../storage/'."
