param(
    [string]$Ec2Host = "",
    [string]$Ec2User = "",
    [string]$Ec2Key = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir "..")).Path
$EnvFile = Join-Path $ScriptDir "deploy.env"
$EnvExample = Join-Path $ScriptDir "deploy.env.example"

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

function Resolver-CaminhoChave([string]$RawKey) {
    if ($RawKey) {
        if ($RawKey.StartsWith("~/") -or $RawKey.StartsWith("~\")) {
            $RawKey = Join-Path $env:USERPROFILE $RawKey.Substring(2)
        }
        if (-not [System.IO.Path]::IsPathRooted($RawKey)) {
            $relRepo = Join-Path $RepoRoot $RawKey
            if (Test-Path $relRepo) { return (Resolve-Path $relRepo).Path }
        }
        if (Test-Path $RawKey) { return (Resolve-Path $RawKey).Path }
        return $RawKey
    }

    $chavesDir = Join-Path $ScriptDir "chaves"
    if (Test-Path $chavesDir) {
        $pems = @(Get-ChildItem -Path $chavesDir -Filter "*.pem" -File -ErrorAction SilentlyContinue)
        if ($pems.Count -eq 1) {
            return $pems[0].FullName
        }
    }
    return ""
}

function Ajustar-PermissaoChaveWindows([string]$KeyPath) {
    Write-Host "==> Ajustando permissão da chave SSH para o OpenSSH do Windows..." -ForegroundColor Cyan
    $usuario = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls $KeyPath /inheritance:r | Out-Null
    & icacls $KeyPath /grant:r "${usuario}:(R)" | Out-Null
}

if (-not (Test-Path $EnvFile) -and (-not $Ec2Host)) {
    Copy-Item $EnvExample $EnvFile
    Write-Host ""
    Write-Host "Criei o arquivo ec2\deploy.env a partir do modelo." -ForegroundColor Yellow
    Write-Host "Preencha pelo menos:" -ForegroundColor Yellow
    Write-Host "  1. EC2_HOST=<IP público da sua EC2>" -ForegroundColor White
    Write-Host "  2. Jogue sua chave .pem dentro de ec2\chaves\ (ou informe EC2_KEY)" -ForegroundColor White
    Write-Host "Depois execute novamente: .\ec2\subir-ec2.bat" -ForegroundColor Green
    exit 1
}

$cfg = Ler-EnvFile $EnvFile
if (-not $Ec2Host) { $Ec2Host = $cfg["EC2_HOST"] }
if (-not $Ec2User) { $Ec2User = if ($cfg["EC2_USER"]) { $cfg["EC2_USER"] } else { "ubuntu" } }
if (-not $Ec2Key)  { $Ec2Key  = $cfg["EC2_KEY"] }

$KeyPath = Resolver-CaminhoChave $Ec2Key

if (-not $Ec2Host -or $Ec2Host -eq "0.0.0.0") {
    throw "Informe o IP público ou DNS real da EC2 em EC2_HOST no arquivo ec2\deploy.env (ou via -Ec2Host)."
}
if (-not $KeyPath -or -not (Test-Path $KeyPath)) {
    throw "Chave .pem não encontrada. Coloque o arquivo .pem em ec2\chaves\ ou informe EC2_KEY em ec2\deploy.env."
}

$AdminNome = if ($cfg["APP_ADMIN_NOME"]) { $cfg["APP_ADMIN_NOME"] } else { "Administrador" }
$AdminEmail = if ($cfg["APP_ADMIN_EMAIL"]) { $cfg["APP_ADMIN_EMAIL"] } else { "admin@repositorio.local" }
$AdminPass = if ($cfg["APP_ADMIN_PASSWORD"]) { $cfg["APP_ADMIN_PASSWORD"] } else { "troque-esta-senha-admin" }
$DbPassword = if ($cfg["DB_PASSWORD"]) { $cfg["DB_PASSWORD"] } else { "repositorio_dev" }
$StorageDir = if ($cfg["STORAGE_LOCAL_DIR"]) { $cfg["STORAGE_LOCAL_DIR"] } else { "/var/lib/repositorio-tcc/storage" }
$MaxFile = if ($cfg["UPLOAD_MAX_FILE_SIZE"]) { $cfg["UPLOAD_MAX_FILE_SIZE"] } else { "100MB" }
$MaxReq = if ($cfg["UPLOAD_MAX_REQUEST_SIZE"]) { $cfg["UPLOAD_MAX_REQUEST_SIZE"] } else { "110MB" }

Ajustar-PermissaoChaveWindows $KeyPath

$JarPath = Join-Path $RepoRoot "build\libs\sistema-repositorio-artigos-1.0.0.jar"
$FrontendBrowserDir = Join-Path $RepoRoot "frontend\dist\repositorio-tcc\browser"

if (-not $SkipBuild) {
    Write-Host "==> Gerando JAR do backend com Gradle..." -ForegroundColor Cyan
    Push-Location $RepoRoot
    try {
        & .\gradlew.bat bootJar --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Falha ao compilar o backend com Gradle." }
    } finally {
        Pop-Location
    }

    Write-Host "==> Gerando build de produção do frontend Angular..." -ForegroundColor Cyan
    Push-Location (Join-Path $RepoRoot "frontend")
    try {
        if (-not (Test-Path "node_modules")) {
            & npm.cmd install
            if ($LASTEXITCODE -ne 0) { throw "Falha ao instalar dependências do frontend." }
        }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw "Falha ao compilar o frontend Angular." }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $JarPath)) { throw "JAR não encontrado em: $JarPath" }
if (-not (Test-Path $FrontendBrowserDir)) { throw "Build do frontend não encontrado em: $FrontendBrowserDir" }

$TempBundle = Join-Path $env:TEMP "repositorio-ec2-bundle"
if (Test-Path $TempBundle) { Remove-Item $TempBundle -Recurse -Force }
New-Item -ItemType Directory -Path $TempBundle | Out-Null

$FrontendTar = Join-Path $TempBundle "frontend-dist.tar.gz"
& tar -czf $FrontendTar -C $FrontendBrowserDir .
if ($LASTEXITCODE -ne 0) { throw "Falha ao empacotar build do frontend." }

$DeployParams = Join-Path $TempBundle "deploy-params.env"
$paramsConteudo = @(
    "DB_PASSWORD=$DbPassword"
    "STORAGE_LOCAL_DIR=$StorageDir"
    "APP_ADMIN_NOME=$AdminNome"
    "APP_ADMIN_EMAIL=$AdminEmail"
    "APP_ADMIN_PASSWORD=$AdminPass"
    "UPLOAD_MAX_FILE_SIZE=$MaxFile"
    "UPLOAD_MAX_REQUEST_SIZE=$MaxReq"
) -join "`n"
[System.IO.File]::WriteAllText($DeployParams, $paramsConteudo + "`n", [System.Text.UTF8Encoding]::new($false))

$Remote = "$Ec2User@$Ec2Host"
$SshOpts = @("-i", $KeyPath, "-o", "StrictHostKeyChecking=accept-new", "-o", "ConnectTimeout=15")

Write-Host "==> Conectando na EC2 ($Remote) e preparando pasta temporária..." -ForegroundColor Cyan
& ssh @SshOpts $Remote "rm -rf /tmp/deploy-repositorio && mkdir -p /tmp/deploy-repositorio"
if ($LASTEXITCODE -ne 0) { throw "Não foi possível conectar via SSH em $Remote. Verifique IP, usuário ($Ec2User), porta 22 e chave .pem." }

Write-Host "==> Enviando backend, frontend e scripts para a EC2..." -ForegroundColor Cyan
& scp @SshOpts `
    $JarPath `
    $FrontendTar `
    $DeployParams `
    (Join-Path $RepoRoot "docker-compose.yml") `
    (Join-Path $RepoRoot "frontend\nginx\repositorio-tcc.conf") `
    (Join-Path $ScriptDir "setup-servidor-ec2.sh") `
    "${Remote}:/tmp/deploy-repositorio/"
if ($LASTEXITCODE -ne 0) { throw "Falha ao enviar arquivos para a EC2." }

Write-Host "==> Executando instalação e inicialização na EC2..." -ForegroundColor Cyan
& ssh @SshOpts $Remote "sed -i 's/\r$//' /tmp/deploy-repositorio/setup-servidor-ec2.sh /tmp/deploy-repositorio/deploy-params.env && chmod +x /tmp/deploy-repositorio/setup-servidor-ec2.sh && bash /tmp/deploy-repositorio/setup-servidor-ec2.sh"
if ($LASTEXITCODE -ne 0) { throw "Falha durante a configuração do servidor na EC2." }

Remove-Item $TempBundle -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Green
Write-Host "  DEPLOY CONCLUÍDO NA AWS EC2!" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
Write-Host "  Acesse o site:  http://$Ec2Host" -ForegroundColor White
Write-Host "  Status da API:  http://$Ec2Host/api/status" -ForegroundColor White
Write-Host "  Login Admin:    $AdminEmail" -ForegroundColor White
Write-Host ""
Write-Host "  Para enviar os PDFs/livros da pasta livros\ para a EC2:" -ForegroundColor Yellow
Write-Host "    .\ec2\enviar-livros.bat" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Green
