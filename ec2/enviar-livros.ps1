param(
    [string]$Url = "",
    [string]$Pasta = "",
    [string]$Email = "",
    [string]$Senha = "",
    [string]$CursoPadrao = "Acervo Geral",
    [string]$OrientadorPadrao = "Coordenação do Acervo",
    [string]$AutorPadrao = "Acervo Institucional",
    [ValidateSet("TCC", "ARTIGO", "MONOGRAFIA", "DISSERTACAO", "TESE")]
    [string]$TipoPadrao = "ARTIGO"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir "..")).Path
$EnvFile = Join-Path $ScriptDir "deploy.env"

function Ler-EnvFile([string]$Caminho) {
    $mapa = @{}
    if (-not (Test-Path $Caminho)) { return $mapa }
    foreach ($linha in Get-Content $Caminho -Encoding UTF8) {
        $limpa = $linha.Trim()
        if (-not $limpa -or $limpa.StartsWith("#")) { continue }
        $idx = $limpa.IndexOf("=")
        if ($idx -gt 0) {
            $k = $limpa.Substring(0, $idx).Trim()
            $v = $limpa.Substring($idx + 1).Trim().Trim('"').Trim("'")
            $mapa[$k] = $v
        }
    }
    return $mapa
}

function Criar-EmailOrientador([string]$Nome) {
    $normalizado = $Nome.Normalize([System.Text.NormalizationForm]::FormD)
    $sb = New-Object System.Text.StringBuilder
    foreach ($ch in $normalizado.ToCharArray()) {
        if ([System.Globalization.CharUnicodeInfo]::GetUnicodeCategory($ch) -ne [System.Globalization.UnicodeCategory]::NonSpacingMark) {
            [void]$sb.Append($ch)
        }
    }
    $slug = ($sb.ToString().ToLowerInvariant() -replace '[^a-z0-9]+', '.').Trim('.')
    if (-not $slug) { $slug = "orientador" }
    return "$slug@repositorio.local"
}

function Invocar-ApiJson {
    param(
        [string]$Method,
        [string]$Endpoint,
        [object]$Body = $null,
        [string]$Token = ""
    )
    $req = [System.Net.HttpWebRequest]::Create($Endpoint)
    $req.Method = $Method
    $req.Timeout = 30000
    $req.Accept = "application/json"
    if ($Token) { $req.Headers.Add("Authorization", "Bearer $Token") }
    if ($null -ne $Body) {
        $req.ContentType = "application/json; charset=utf-8"
        $json = $Body | ConvertTo-Json -Depth 5 -Compress
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
        $req.ContentLength = $bytes.Length
        $stream = $req.GetRequestStream()
        try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    }
    $resp = $req.GetResponse()
    try {
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
        try {
            $texto = $reader.ReadToEnd()
            if (-not $texto) { return $null }
            return ($texto | ConvertFrom-Json)
        } finally {
            $reader.Dispose()
        }
    } finally {
        $resp.Dispose()
    }
}

$cfg = Ler-EnvFile $EnvFile

if (-not $Url) {
    if ($cfg["EC2_HOST"]) {
        $Url = "http://$($cfg['EC2_HOST'])"
    } else {
        throw "Informe a URL da EC2 em EC2_HOST (ec2\deploy.env) ou passe -Url http://IP_DA_EC2 (ou http://localhost:8080)."
    }
}
$Url = $Url.TrimEnd("/")

if (-not $Pasta) { $Pasta = Join-Path $RepoRoot "livros" }
if (-not (Test-Path $Pasta)) { throw "Pasta de livros não encontrada: $Pasta" }

if (-not $Email) { $Email = if ($cfg["APP_ADMIN_EMAIL"]) { $cfg["APP_ADMIN_EMAIL"] } else { "admin@repositorio.local" } }
if (-not $Senha) { $Senha = if ($cfg["APP_ADMIN_PASSWORD"]) { $cfg["APP_ADMIN_PASSWORD"] } else { "admin123" } }

# Detecta automaticamente se a API está em /api (Nginx na EC2) ou na raiz (Spring Boot local)
$ApiBase = ""
foreach ($candidato in @("$Url/api", $Url)) {
    try {
        $st = Invoke-RestMethod -Uri "$candidato/status" -TimeoutSec 5 -ErrorAction Stop
        if ($st.status) {
            $ApiBase = $candidato
            break
        }
    } catch {}
}
if (-not $ApiBase) {
    throw "Não foi possível conectar em $Url/api/status nem em $Url/status. Verifique se a aplicação está no ar."
}

Write-Host "==> Conectado à API: $ApiBase" -ForegroundColor Cyan

# Login como ADMIN para publicar os livros diretamente no acervo
try {
    $authRes = Invocar-ApiJson -Method "POST" -Endpoint "$ApiBase/auth/login" -Body @{
        email = $Email
        senha = $Senha
    }
} catch {
    throw "Falha no login com '$Email'. Verifique APP_ADMIN_EMAIL e APP_ADMIN_PASSWORD em ec2\deploy.env (ou passe -Email / -Senha)."
}
$Token = $authRes.token
Write-Host "==> Autenticado como $($authRes.usuario.nome) ($($authRes.usuario.perfil))." -ForegroundColor Green

# Carrega catálogo opcional livros/catalogo.csv
$Catalogo = @{}
$CsvPath = Join-Path $Pasta "catalogo.csv"
if (Test-Path $CsvPath) {
    $linhas = Get-Content $CsvPath -Encoding UTF8 | Where-Object { $_.Trim() -and -not $_.Trim().StartsWith("#") }
    if ($linhas.Count -gt 1) {
        $registros = $linhas | ConvertFrom-Csv -Delimiter ";"
        foreach ($r in $registros) {
            if ($r.arquivo) {
                $Catalogo[$r.arquivo.Trim().ToLowerInvariant()] = $r
            }
        }
    }
}

# Cache de cursos e orientadores existentes
$CursosPorNome = @{}
foreach ($c in (Invocar-ApiJson -Method "GET" -Endpoint "$ApiBase/cursos" -Token $Token)) {
    $CursosPorNome[$c.nome.Trim().ToLowerInvariant()] = $c.id
}

$OrientadoresPorNome = @{}
$OrientadoresPorEmail = @{}
foreach ($o in (Invocar-ApiJson -Method "GET" -Endpoint "$ApiBase/orientadores?apenasAtivos=false" -Token $Token)) {
    $OrientadoresPorNome[$o.nome.Trim().ToLowerInvariant()] = $o.id
    if ($o.email) { $OrientadoresPorEmail[$o.email.Trim().ToLowerInvariant()] = $o.id }
}

function Garantir-Curso([string]$NomeCurso) {
    $chave = $NomeCurso.Trim().ToLowerInvariant()
    if ($CursosPorNome.ContainsKey($chave)) { return $CursosPorNome[$chave] }
    $criado = Invocar-ApiJson -Method "POST" -Endpoint "$ApiBase/cursos" -Token $Token -Body @{
        nome  = $NomeCurso.Trim()
        sigla = $null
    }
    $CursosPorNome[$chave] = $criado.id
    Write-Host "    + Curso criado: $($criado.nome) (ID $($criado.id))" -ForegroundColor DarkCyan
    return $criado.id
}

function Garantir-Orientador([string]$NomeOrientador) {
    $chave = $NomeOrientador.Trim().ToLowerInvariant()
    $email = (Criar-EmailOrientador $NomeOrientador).ToLowerInvariant()
    if ($OrientadoresPorNome.ContainsKey($chave)) { return $OrientadoresPorNome[$chave] }
    if ($OrientadoresPorEmail.ContainsKey($email)) { return $OrientadoresPorEmail[$email] }
    $criado = Invocar-ApiJson -Method "POST" -Endpoint "$ApiBase/orientadores" -Token $Token -Body @{
        nome      = $NomeOrientador.Trim()
        email     = $email
        titulacao = $null
    }
    $OrientadoresPorNome[$chave] = $criado.id
    $OrientadoresPorEmail[$email] = $criado.id
    Write-Host "    + Orientador criado: $($criado.nome) (ID $($criado.id))" -ForegroundColor DarkCyan
    return $criado.id
}

$ArquivosPdf = @(Get-ChildItem -Path $Pasta -Filter "*.pdf" -File -Recurse | Sort-Object FullName)
if ($ArquivosPdf.Count -eq 0) {
    Write-Host ""
    Write-Host "Nenhum arquivo .pdf encontrado em: $Pasta" -ForegroundColor Yellow
    Write-Host "Coloque seus PDFs dentro da pasta livros\ e execute novamente." -ForegroundColor White
    exit 0
}

Write-Host "==> Encontrados $($ArquivosPdf.Count) arquivo(s) PDF em $Pasta" -ForegroundColor Cyan

$enviados = 0
$pulados = 0
$erros = 0
$tiposValidos = @("TCC", "ARTIGO", "MONOGRAFIA", "DISSERTACAO", "TESE")
$metaTmp = Join-Path $env:TEMP ("repositorio-meta-" + [Guid]::NewGuid().ToString("N") + ".json")

try {
    foreach ($pdf in $ArquivosPdf) {
        $nomeChave = $pdf.Name.ToLowerInvariant()
        $metaCsv = if ($Catalogo.ContainsKey($nomeChave)) { $Catalogo[$nomeChave] } else { $null }

        $titulo = if ($metaCsv -and $metaCsv.titulo) {
            $metaCsv.titulo.Trim()
        } else {
            ([System.IO.Path]::GetFileNameWithoutExtension($pdf.Name) -replace '[_-]+', ' ').Trim()
        }

        $autoresLista = @()
        if ($metaCsv -and $metaCsv.autores) {
            $autoresLista = @($metaCsv.autores -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        }
        if ($autoresLista.Count -eq 0) { $autoresLista = @($AutorPadrao) }

        $tipo = if ($metaCsv -and $metaCsv.tipo) { $metaCsv.tipo.Trim().ToUpperInvariant() } else { $TipoPadrao }
        if ($tiposValidos -notcontains $tipo) { $tipo = $TipoPadrao }

        $ano = (Get-Date).Year
        if ($metaCsv -and $metaCsv.ano -and ($metaCsv.ano -match '^\d{4}$')) {
            $ano = [int]$metaCsv.ano
        } elseif ($pdf.BaseName -match '(19\d{2}|20\d{2})') {
            $ano = [int]$Matches[1]
        }

        $cursoNome = if ($metaCsv -and $metaCsv.curso) { $metaCsv.curso.Trim() } else { $CursoPadrao }
        $orientadorNome = if ($metaCsv -and $metaCsv.orientador) { $metaCsv.orientador.Trim() } else { $OrientadorPadrao }
        $resumo = if ($metaCsv -and $metaCsv.resumo) { $metaCsv.resumo.Trim() } else { "" }
        $palavras = if ($metaCsv -and $metaCsv.palavras_chave) { $metaCsv.palavras_chave.Trim() } else { "" }

        $cursoId = Garantir-Curso $cursoNome
        $orientadorId = Garantir-Orientador $orientadorNome

        $payloadObj = [ordered]@{
            titulo        = $titulo
            resumo        = $resumo
            palavrasChave = $palavras
            tipo          = $tipo
            ano           = $ano
            cursoId       = [long]$cursoId
            orientadorId  = [long]$orientadorId
            autores       = @($autoresLista)
        }
        $payloadJson = $payloadObj | ConvertTo-Json -Depth 5 -Compress
        [System.IO.File]::WriteAllText($metaTmp, $payloadJson, [System.Text.UTF8Encoding]::new($false))

        $respRaw = & curl.exe -sS -w "`n%{http_code}" `
            -X POST "$ApiBase/trabalhos" `
            -H "Authorization: Bearer $Token" `
            -F "dados=@$metaTmp;type=application/json" `
            -F "arquivo=@$($pdf.FullName);type=application/pdf"

        $linhasResp = @($respRaw -split "`r?`n")
        $httpCode = $linhasResp[-1].Trim()
        $corpoResp = ($linhasResp[0..($linhasResp.Count - 2)] -join "`n").Trim()

        if ($httpCode -eq "201") {
            $obj = $corpoResp | ConvertFrom-Json
            Write-Host "  [OK] $($pdf.Name) -> ID #$($obj.id) ($($obj.status))" -ForegroundColor Green
            $enviados++
        } elseif ($httpCode -eq "400" -and $corpoResp -match "enviado anteriormente") {
            Write-Host "  [PULADO] $($pdf.Name) -> já existe no acervo (mesmo SHA-256)" -ForegroundColor Yellow
            $pulados++
        } else {
            $msgErro = $corpoResp
            try {
                $errObj = $corpoResp | ConvertFrom-Json
                if ($errObj.mensagem) { $msgErro = $errObj.mensagem }
            } catch {}
            Write-Host "  [ERRO HTTP $httpCode] $($pdf.Name) -> $msgErro" -ForegroundColor Red
            $erros++
        }
    }
} finally {
    Remove-Item $metaTmp -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  Resumo: Enviados=$enviados | Já existentes=$pulados | Erros=$erros" -ForegroundColor White
Write-Host "  Acervo: $Url" -ForegroundColor White
Write-Host "=================================================================" -ForegroundColor Cyan

if ($erros -gt 0) { exit 1 }
