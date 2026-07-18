[CmdletBinding()]
param(
    [switch]$Demo
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $projectRoot 'backend'

function Import-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { continue }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim().Trim('"').Trim("'")
        if ($name -match '^[A-Za-z_][A-Za-z0-9_]*$' -and -not (Test-Path "Env:$name")) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

Import-DotEnv (Join-Path $projectRoot '.env')
Import-DotEnv (Join-Path $backendRoot '.env')

if ($Demo) {
    $env:SPRING_PROFILES_ACTIVE = 'demo'
} else {
    if (-not $env:DB_URL) { $env:DB_URL = 'jdbc:postgresql://localhost:5432/click' }
    if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'postgres' }
    if (-not $env:DB_PASSWORD) {
        $securePassword = Read-Host '请输入 PostgreSQL 密码（输入内容不会显示）' -AsSecureString
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        try { $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    }
}

if (-not $env:JWT_SECRET) {
    $bytes = [byte[]]::new(48)
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) }
    finally { $random.Dispose() }
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
    Write-Host '本次启动已生成临时 JWT_SECRET；生产环境请在 .env 中设置固定随机密钥。' -ForegroundColor Yellow
}

$javaVersion = (& java --version | Select-Object -First 1)
Write-Host "Java: $javaVersion"
if ($javaVersion -notmatch '(?:version\s+)?"?17(?:[\.\s"])') {
    Write-Host '提示：项目按 Java 17 字节码编译；当前运行时不是 JDK 17。建议在 IDE/Maven 中选择 JDK 17。' -ForegroundColor Yellow
}

Push-Location $backendRoot
try {
    if ($Demo) { & mvn spring-boot:run '-Dspring-boot.run.profiles=demo' }
    else { & mvn spring-boot:run }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
