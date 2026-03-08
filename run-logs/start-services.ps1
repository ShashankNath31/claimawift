$ErrorActionPreference='Stop'
$root='c:\Users\shashank.nath\Desktop\Claimswift'
$logDir=Join-Path $root 'run-logs\service-logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$services=@(
  @{Name='config-server'; Port=8888; Jar='config-server\target\config-server-1.0.0.jar'},
  @{Name='eureka-server'; Port=8761; Jar='eureka-server\target\eureka-server-1.0.0.jar'},
  @{Name='api-gateway'; Port=8080; Jar='api-gateway\target\api-gateway-1.0.0.jar'},
  @{Name='auth-service'; Port=8081; Jar='auth-service\target\auth-service-1.0.0.jar'},
  @{Name='claim-service'; Port=8082; Jar='claim-service\target\claim-service-1.0.0.jar'},
  @{Name='document-service'; Port=8083; Jar='document-service\target\document-service-1.0.0.jar'},
  @{Name='assessment-service'; Port=8084; Jar='assessment-service\target\assessment-service-1.0.0.jar'},
  @{Name='payment-service'; Port=8085; Jar='payment-service\target\payment-service-1.0.0.jar'},
  @{Name='notification-service'; Port=8086; Jar='notification-service\target\notification-service-1.0.0.jar'},
  @{Name='reporting-service'; Port=8087; Jar='reporting-service\target\reporting-service-1.0.0.jar'}
)
$started=@()
foreach($svc in $services){
  $jarPath=Join-Path $root $svc.Jar
  if(-not (Test-Path $jarPath)){ throw "Jar missing: $jarPath" }
  $outLog=Join-Path $logDir ("{0}.out.log" -f $svc.Name)
  $errLog=Join-Path $logDir ("{0}.err.log" -f $svc.Name)
  if(Test-Path $outLog){ Remove-Item $outLog -Force }
  if(Test-Path $errLog){ Remove-Item $errLog -Force }
  $proc=Start-Process -FilePath 'java' -ArgumentList @('-jar', $jarPath) -WorkingDirectory $root -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
  $started += [pscustomobject]@{ name=$svc.Name; pid=$proc.Id; port=$svc.Port; outLog=$outLog; errLog=$errLog }

  $healthUrl = "http://localhost:$($svc.Port)/actuator/health"
  $deadline = (Get-Date).AddMinutes(3)
  $healthy = $false
  do {
    Start-Sleep -Seconds 2
    try {
      $resp = Invoke-WebRequest -UseBasicParsing -Uri $healthUrl -TimeoutSec 5
      if([int]$resp.StatusCode -ge 200 -and [int]$resp.StatusCode -lt 300){ $healthy = $true; break }
    } catch {}
  } while((Get-Date) -lt $deadline)

  if(-not $healthy){
    Write-Output "FAILED_START $($svc.Name) port=$($svc.Port) pid=$($proc.Id)"
    if(Test-Path $errLog){ Get-Content -Path $errLog -Tail 120 }
    throw "Service failed health check: $($svc.Name)"
  }
  Write-Output "STARTED $($svc.Name) pid=$($proc.Id) port=$($svc.Port)"
}
$started | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $logDir 'service-pids.json')
Write-Output 'ALL_STARTED'
