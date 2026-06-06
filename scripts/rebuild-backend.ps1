# Force-rebuild and restart the backend. Kills any lingering java process first.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $root 'backend'

Write-Host "Stopping all Java processes..."
Get-Process java, javaw -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 3

$target = Join-Path $backendDir 'target'
if (Test-Path $target) {
    Write-Host "Removing old target directory..."
    Remove-Item -Recurse -Force $target -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

Write-Host "Building backend..."
Push-Location $backendDir
try {
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "BUILD FAILED" -ForegroundColor Red
        exit 1
    }
} finally {
    Pop-Location
}

Write-Host "Starting backend..."
$jar = Join-Path $backendDir 'target\learning-resource-platform-0.0.1-SNAPSHOT.jar'
$runtimeDir = Join-Path $root 'runtime'
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
$backendOut = Join-Path $runtimeDir 'backend.out.log'
$backendErr = Join-Path $runtimeDir 'backend.err.log'

$backend = Start-Process -FilePath 'java' `
    -ArgumentList @('-jar', $jar) `
    -WorkingDirectory $backendDir `
    -RedirectStandardOutput $backendOut `
    -RedirectStandardError $backendErr `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Backend PID: $($backend.Id)"
Write-Host "Waiting for backend to become ready..."

$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    try {
        Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/categories' -Method Get -TimeoutSec 5 | Out-Null
        $ready = $true
        break
    } catch {
        if ($backend.HasExited) { break }
    }
}

if ($ready) {
    Write-Host "Backend is ready at http://localhost:8080" -ForegroundColor Green
} else {
    Write-Host "Backend failed to start. Check runtime/backend.err.log" -ForegroundColor Red
}
