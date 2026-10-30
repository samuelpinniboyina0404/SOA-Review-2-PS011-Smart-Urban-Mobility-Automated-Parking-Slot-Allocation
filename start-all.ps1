# ParkGrid Solutions - Microservices Startup Script
$ErrorActionPreference = "Continue"

$projectDir = $PSScriptRoot
if (-not (Test-Path "$projectDir\eureka-server")) {
    $candidate = Join-Path $PSScriptRoot "SOA-Review-2-PS011-Smart-Urban-Mobility-Automated-Parking-Slot-Allocation"
    if (Test-Path "$candidate\eureka-server") {
        $projectDir = $candidate
    }
}

$logsDir = Join-Path $projectDir "logs"
if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Starting ParkGrid Solutions Microservices Architecture   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Stop any previous instances on these ports
$ports = @(8761, 8080, 8081, 8082, 8083, 8084, 8085)
foreach ($port in $ports) {
    $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($conn) {
        $pidsToKill = $conn | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($p in $pidsToKill) {
            Write-Host "Stopping existing process on port $port (PID: $p)..." -ForegroundColor Yellow
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        }
    }
}

Start-Sleep -Seconds 2

# Java executable
$javaExe = "C:\Program Files\Java\jdk-26.0.2\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    $javaExe = "java"
}

$startedPids = @()

# Function to launch jar detached using Win32_Process
function Start-ServiceJar {
    param(
        [string]$name,
        [string]$jarPath,
        [string]$workDir,
        [string]$extraArgs = "",
        [int]$port
    )
    Write-Host "[Starting] $name (Port $port)..." -ForegroundColor Green
    $stdout = Join-Path $logsDir "$name.log"
    $stderr = Join-Path $logsDir "$name.err.log"

    # Clear previous logs
    if (Test-Path $stdout) { Remove-Item $stdout -Force -ErrorAction SilentlyContinue }
    if (Test-Path $stderr) { Remove-Item $stderr -Force -ErrorAction SilentlyContinue }

    $cmd = "cmd.exe /c `"`"$javaExe`" -Xms128m -Xmx384m -jar `"$jarPath`" $extraArgs > `"$stdout`" 2> `"$stderr`"`""
    $res = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmd; CurrentDirectory = $workDir }
    if ($res.ProcessId) {
        $script:startedPids += $res.ProcessId
    }
    return $res.ProcessId
}

# 1. Start Eureka Server
$eurekaJar = Join-Path $projectDir "eureka-server\target\eureka-server-1.0.0.jar"
Start-ServiceJar -name "eureka-server" -jarPath $eurekaJar -workDir (Join-Path $projectDir "eureka-server") -port 8761 | Out-Null

Write-Host "Waiting for Eureka Server on port 8761 to initialize..." -ForegroundColor Yellow
$eurekaReady = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:8761" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($resp.StatusCode -eq 200) {
            $eurekaReady = $true
            break
        }
    } catch {
        # keep waiting
    }
}

if ($eurekaReady) {
    Write-Host "Eureka Server is ONLINE at http://localhost:8761" -ForegroundColor Green
} else {
    Write-Host "Eureka Server did not report 200 within 30s. Check logs/eureka-server.log" -ForegroundColor Red
}

# 2. Start Core Services
$authJar = Join-Path $projectDir "auth-service\target\auth-service-1.0.0.jar"
Start-ServiceJar -name "auth-service" -jarPath $authJar -workDir (Join-Path $projectDir "auth-service") -port 8081 | Out-Null

$parkingJar = Join-Path $projectDir "parking-service\target\parking-service-1.0.0.jar"
Start-ServiceJar -name "parking-service-1" -jarPath $parkingJar -workDir (Join-Path $projectDir "parking-service") -extraArgs "--server.port=8082" -port 8082 | Out-Null
Start-ServiceJar -name "parking-service-2" -jarPath $parkingJar -workDir (Join-Path $projectDir "parking-service") -extraArgs "--server.port=8085" -port 8085 | Out-Null

$bookingJar = Join-Path $projectDir "booking-service\target\booking-service-1.0.0.jar"
Start-ServiceJar -name "booking-service" -jarPath $bookingJar -workDir (Join-Path $projectDir "booking-service") -port 8083 | Out-Null

$billingJar = Join-Path $projectDir "billing-service\target\billing-service-1.0.0.jar"
Start-ServiceJar -name "billing-service" -jarPath $billingJar -workDir (Join-Path $projectDir "billing-service") -port 8084 | Out-Null

Write-Host "Waiting 12 seconds for core microservices to initialize and register with Eureka..." -ForegroundColor Yellow
Start-Sleep -Seconds 12

# 3. Start API Gateway
$gatewayJar = Join-Path $projectDir "api-gateway\target\api-gateway-1.0.0.jar"
Start-ServiceJar -name "api-gateway" -jarPath $gatewayJar -workDir (Join-Path $projectDir "api-gateway") -port 8080 | Out-Null

Write-Host "Waiting for API Gateway to initialize on port 8080..." -ForegroundColor Yellow
$gatewayReady = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/instance-info" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($resp.StatusCode -eq 200) {
            $gatewayReady = $true
            break
        }
    } catch {
        # keep waiting
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " Service Status Summary" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$servicesToCheck = @(
    @{ Name = "Eureka Registry"; Url = "http://localhost:8761" },
    @{ Name = "Auth Service (Direct)"; Url = "http://localhost:8081/api/auth/instance-info" },
    @{ Name = "Parking Service Inst 1 (Direct)"; Url = "http://localhost:8082/api/parking/instance-info" },
    @{ Name = "Parking Service Inst 2 (Direct)"; Url = "http://localhost:8085/api/parking/instance-info" },
    @{ Name = "Booking Service (Direct)"; Url = "http://localhost:8083/api/bookings/instance-info" },
    @{ Name = "Billing Service (Direct)"; Url = "http://localhost:8084/api/billing/instance-info" },
    @{ Name = "API Gateway (Edge Router)"; Url = "http://localhost:8080/api/auth/instance-info" }
)

foreach ($s in $servicesToCheck) {
    try {
        $res = Invoke-RestMethod -Uri $s.Url -Method Get -TimeoutSec 3 -ErrorAction Stop
        Write-Host " [ONLINE] $($s.Name) - $($s.Url)" -ForegroundColor Green
    } catch {
        Write-Host " [INITIALIZING] $($s.Name) - $($s.Url)" -ForegroundColor Yellow
    }
}

# Start Frontend HTTP server
$frontendWorkDir = $projectDir
$nodeCmd = "cmd.exe /c node server.js"
$nodeRes = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $nodeCmd; CurrentDirectory = $frontendWorkDir }
Write-Host "[Starting] Frontend Server (Port 3000)..." -ForegroundColor Green
Start-Sleep -Seconds 1

# Open Frontend
Write-Host "`nOpening Frontend UI in browser: http://localhost:3000" -ForegroundColor Cyan
Start-Process "http://localhost:3000"

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " ParkGrid Solutions is now RUNNING!" -ForegroundColor Green
Write-Host " Frontend: http://localhost:3000" -ForegroundColor Green
Write-Host " API Gateway: http://localhost:8080" -ForegroundColor Green
Write-Host " Eureka Dashboard: http://localhost:8761" -ForegroundColor Green
Write-Host " Pre-configured accounts:" -ForegroundColor Green
Write-Host "   - Admin:  admin@parkgrid.com  / Admin@123" -ForegroundColor Green
Write-Host "   - Driver: user@parkgrid.com   / User@123" -ForegroundColor Green
Write-Host " To stop all services: run .\stop-all.ps1" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
