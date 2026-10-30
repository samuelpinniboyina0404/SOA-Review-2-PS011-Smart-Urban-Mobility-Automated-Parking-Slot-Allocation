# ParkGrid Solutions - Stop All Microservices
$ErrorActionPreference = "Continue"

Write-Host "Stopping all ParkGrid microservices..." -ForegroundColor Cyan

$ports = @(8761, 8080, 8081, 8082, 8083, 8084, 8085)
$killedCount = 0

foreach ($port in $ports) {
    $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($conn) {
        $pidsToKill = $conn | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($p in $pidsToKill) {
            Write-Host "Stopping process on port $port (PID: $p)..." -ForegroundColor Yellow
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
            $killedCount++
        }
    }
}

Write-Host "All ParkGrid microservice processes stopped ($killedCount processes terminated)." -ForegroundColor Green
