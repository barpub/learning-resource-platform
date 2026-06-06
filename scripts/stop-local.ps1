$ErrorActionPreference = 'SilentlyContinue'

$root = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $root 'runtime'

foreach ($name in @('frontend','backend')) {
    $pidFile = Join-Path $runtimeDir "$name.pid"
    if (Test-Path $pidFile) {
        $processId = (Get-Content $pidFile -Raw).Trim()
        if ($processId) {
            taskkill.exe /PID $processId /T /F | Out-Null
            Write-Host "Stopped $name process $processId"
        }
        Remove-Item $pidFile -Force
    }
}
