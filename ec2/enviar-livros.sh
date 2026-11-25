#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$SCRIPT_DIR/deploy.env"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  set +a
fi

URL="${1:-}"
PASTA="${2:-$REPO_ROOT/livros}"
EMAIL="${APP_ADMIN_EMAIL:-admin@repositorio.local}"
SENHA="${APP_ADMIN_PASSWORD:-admin123}"
CURSO_PADRAO="${CURSO_PADRAO:-Acervo Geral}"
ORIENTADOR_PADRAO="${ORIENTADOR_PADRAO:-Coordenação do Acervo}"
AUTOR_PADRAO="${AUTOR_PADRAO:-Acervo Institucional}"
TIPO_PADRAO="${TIPO_PADRAO:-ARTIGO}"

if [[ -z "$URL" ]]; then
  if [[ -n "${EC2_HOST:-}" ]]; then
    URL="http://${EC2_HOST}"
  else
    echo "Uso: ./ec2/enviar-livros.sh [http://IP_DA_EC2] [pasta_dos_pdfs]"
    echo "Ou configure EC2_HOST em ec2/deploy.env."
    exit 1
  fi
fi
URL="${URL%/}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "Erro: python3 é necessário para montar o JSON de metadados no Linux/Mac."
  exit 1
fi

API_BASE=""
for cand in "$URL/api" "$URL"; do
  if curl -fsS --max-time 5 "$cand/status" | grep -q '"status"'; then
    API_BASE="$cand"
    break
  fi
done
if [[ -z "$API_BASE" ]]; then
  echo "Erro: não foi possível conectar em $URL/api/status nem em $URL/status."
  exit 1
fi

echo "==> Conectado à API: $API_BASE"

LOGIN_PAYLOAD="$(python3 -c 'import json,sys; print(json.dumps({"email":sys.argv[1],"senha":sys.argv[2]}))' "$EMAIL" "$SENHA")"
LOGIN_RESP="$(curl -fsS -X POST "$API_BASE/auth/login" -H "Content-Type: application/json" -d "$LOGIN_PAYLOAD")"
TOKEN="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["token"])' "$LOGIN_RESP")"

export API_BASE TOKEN PASTA CURSO_PADRAO ORIENTADOR_PADRAO AUTOR_PADRAO TIPO_PADRAO URL
python3 - <<'PY'
import csv, json, os, re, subprocess, tempfile, unicodedata, urllib.request, urllib.error
from datetime import datetime
from pathlib import Path

api = os.environ["API_BASE"]
token = os.environ["TOKEN"]
pasta = Path(os.environ["PASTA"])
curso_padrao = os.environ["CURSO_PADRAO"]
orientador_padrao = os.environ["ORIENTADOR_PADRAO"]
autor_padrao = os.environ["AUTOR_PADRAO"]
tipo_padrao = os.environ["TIPO_PADRAO"]
site_url = os.environ["URL"]

def api_json(method, path, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(
        f"{api}{path}",
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json; charset=utf-8",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))

def slug_email(nome):
    norm = unicodedata.normalize("NFKD", nome).encode("ascii", "ignore").decode("ascii").lower()
    slug = re.sub(r"[^a-z0-9]+", ".", norm).strip(".") or "orientador"
    return f"{slug}@repositorio.local"

cursos = {c["nome"].strip().lower(): c["id"] for c in api_json("GET", "/cursos")}
orientadores_lista = api_json("GET", "/orientadores?apenasAtivos=false")
orientadores = {o["nome"].strip().lower(): o["id"] for o in orientadores_lista}
orientadores_email = {o["email"].strip().lower(): o["id"] for o in orientadores_lista if o.get("email")}

def garantir_curso(nome):
    k = nome.strip().lower()
    if k in cursos:
        return cursos[k]
    c = api_json("POST", "/cursos", {"nome": nome.strip(), "sigla": None})
    cursos[k] = c["id"]
    print(f"    + Curso criado: {c['nome']} (ID {c['id']})")
    return c["id"]

def garantir_orientador(nome):
    k = nome.strip().lower()
    email = slug_email(nome)
    if k in orientadores:
        return orientadores[k]
    if email in orientadores_email:
        return orientadores_email[email]
    o = api_json("POST", "/orientadores", {"nome": nome.strip(), "email": email, "titulacao": None})
    orientadores[k] = o["id"]
    orientadores_email[email] = o["id"]
    print(f"    + Orientador criado: {o['nome']} (ID {o['id']})")
    return o["id"]

catalogo = {}
csv_path = pasta / "catalogo.csv"
if csv_path.is_file():
    linhas = [l for l in csv_path.read_text(encoding="utf-8").splitlines() if l.strip() and not l.strip().startswith("#")]
    if len(linhas) > 1:
        for row in csv.DictReader(linhas, delimiter=";"):
            if row.get("arquivo"):
                catalogo[row["arquivo"].strip().lower()] = row

pdfs = sorted(pasta.rglob("*.pdf"))
if not pdfs:
    print(f"Nenhum arquivo .pdf encontrado em: {pasta}")
    raise SystemExit(0)

print(f"==> Encontrados {len(pdfs)} arquivo(s) PDF em {pasta}")
enviados = pulados = erros = 0
tipos_validos = {"TCC", "ARTIGO", "MONOGRAFIA", "DISSERTACAO", "TESE"}

for pdf in pdfs:
    meta = catalogo.get(pdf.name.lower(), {})
    titulo = (meta.get("titulo") or re.sub(r"[_-]+", " ", pdf.stem)).strip()
    autores = [a.strip() for a in (meta.get("autores") or "").split(",") if a.strip()] or [autor_padrao]
    tipo = (meta.get("tipo") or tipo_padrao).strip().upper()
    if tipo not in tipos_validos:
        tipo = tipo_padrao
    ano_str = (meta.get("ano") or "").strip()
    if re.fullmatch(r"\d{4}", ano_str):
        ano = int(ano_str)
    else:
        m = re.search(r"(19\d{2}|20\d{2})", pdf.stem)
        ano = int(m.group(1)) if m else datetime.now().year

    curso_id = garantir_curso((meta.get("curso") or curso_padrao).strip())
    orientador_id = garantir_orientador((meta.get("orientador") or orientador_padrao).strip())

    payload = {
        "titulo": titulo,
        "resumo": (meta.get("resumo") or "").strip(),
        "palavrasChave": (meta.get("palavras_chave") or "").strip(),
        "tipo": tipo,
        "ano": ano,
        "cursoId": curso_id,
        "orientadorId": orientador_id,
        "autores": autores,
    }

    with tempfile.NamedTemporaryFile("w", suffix=".json", encoding="utf-8", delete=False) as tf:
        json.dump(payload, tf, ensure_ascii=False)
        meta_path = tf.name

    try:
        proc = subprocess.run(
            [
                "curl", "-sS", "-w", "\n%{http_code}",
                "-X", "POST", f"{api}/trabalhos",
                "-H", f"Authorization: Bearer {token}",
                "-F", f"dados=@{meta_path};type=application/json",
                "-F", f"arquivo=@{pdf};type=application/pdf",
            ],
            capture_output=True, text=True, check=False
        )
        lines = proc.stdout.splitlines()
        code = lines[-1].strip() if lines else "000"
        body = "\n".join(lines[:-1]).strip() if len(lines) > 1 else ""
        if code == "201":
            obj = json.loads(body)
            print(f"  [OK] {pdf.name} -> ID #{obj['id']} ({obj['status']})")
            enviados += 1
        elif code == "400" and "enviado anteriormente" in body:
            print(f"  [PULADO] {pdf.name} -> já existe no acervo (SHA-256 duplicado)")
            pulados += 1
        else:
            try:
                msg = json.loads(body).get("mensagem", body)
            except Exception:
                msg = body or proc.stderr.strip()
            print(f"  [ERRO HTTP {code}] {pdf.name} -> {msg}")
            erros += 1
    finally:
        os.unlink(meta_path)

print(f"\nResumo: Enviados={enviados} | Já existentes={pulados} | Erros={erros}")
print(f"Acervo: {site_url}")
raise SystemExit(1 if erros else 0)
PY
