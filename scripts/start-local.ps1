param(
    [switch]$SkipBuild,
    [ValidateSet('mysql','h2')]
    [string]$Profile = 'mysql'
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $root 'backend'
$frontendDir = Join-Path $root 'frontend'
$runtimeDir = Join-Path $root 'runtime'

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

if (-not $SkipBuild) {
    Push-Location $backendDir
    try {
        mvn -q -DskipTests package
    } finally {
        Pop-Location
    }

    Push-Location $frontendDir
    try {
        npm.cmd run build
    } finally {
        Pop-Location
    }
}

$backendOut = Join-Path $runtimeDir 'backend.out.log'
$backendErr = Join-Path $runtimeDir 'backend.err.log'
$frontendOut = Join-Path $runtimeDir 'frontend.out.log'
$frontendErr = Join-Path $runtimeDir 'frontend.err.log'
$backendPid = Join-Path $runtimeDir 'backend.pid'
$frontendPid = Join-Path $runtimeDir 'frontend.pid'

Remove-Item $backendOut,$backendErr,$frontendOut,$frontendErr -ErrorAction SilentlyContinue

$backendArgs = @('-jar','target\learning-resource-platform-0.0.1-SNAPSHOT.jar')
if ($Profile -eq 'h2') {
    $backendArgs += '--spring.profiles.active=h2'
}

$backend = Start-Process -FilePath 'java' `
    -ArgumentList $backendArgs `
    -WorkingDirectory $backendDir `
    -RedirectStandardOutput $backendOut `
    -RedirectStandardError $backendErr `
    -WindowStyle Hidden `
    -PassThru
Set-Content -Path $backendPid -Value $backend.Id -Encoding ASCII

$backendReady = $false
for ($i = 0; $i -lt 35; $i++) {
    Start-Sleep -Seconds 2
    try {
        Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/categories' -Method Get -TimeoutSec 5 | Out-Null
        $backendReady = $true
        break
    } catch {
        if ($backend.HasExited) {
            break
        }
    }
}

if (-not $backendReady) {
    Write-Host 'Backend failed to become ready. See runtime/backend.out.log and runtime/backend.err.log.'
    exit 1
}

$frontend = Start-Process -FilePath 'npm.cmd' `
    -ArgumentList @('run','dev','--','--host','0.0.0.0') `
    -WorkingDirectory $frontendDir `
    -RedirectStandardOutput $frontendOut `
    -RedirectStandardError $frontendErr `
    -WindowStyle Hidden `
    -PassThru
Set-Content -Path $frontendPid -Value $frontend.Id -Encoding ASCII

$frontendReady = $false
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 1
    try {
        Invoke-WebRequest -Uri 'http://127.0.0.1:5173' -UseBasicParsing -TimeoutSec 5 | Out-Null
        $frontendReady = $true
        break
    } catch {
        if ($frontend.HasExited) {
            break
        }
    }
}

if (-not $frontendReady) {
    Write-Host 'Frontend failed to become ready. See runtime/frontend.out.log and runtime/frontend.err.log.'
    exit 1
}

Write-Host "Backend PID: $($backend.Id)"
Write-Host "Frontend PID: $($frontend.Id)"
Write-Host "Backend profile: $Profile"
Write-Host 'Backend:  http://localhost:8080'
Write-Host 'Frontend: http://localhost:5173'
