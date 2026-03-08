$ErrorActionPreference='Stop'
$root='c:\Users\shashank.nath\Desktop\Claimswift'
$logDir=Join-Path $root 'run-logs\service-logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$targetPorts=8082,8085
$pids=Get-NetTCPConnection -State Listen | Where-Object { $targetPorts -contains $_.LocalPort } | Select-Object -ExpandProperty OwningProcess -Unique
if($pids){ Stop-Process -Id $pids -Force; Start-Sleep -Seconds 2 }

$services=@(
  @{Name='claim-service'; Port=8082; Jar='claim-service\target\claim-service-1.0.0.jar'},
  @{Name='payment-service'; Port=8085; Jar='payment-service\target\payment-service-1.0.0.jar'}
)
foreach($svc in $services){
  $jarPath=Join-Path $root $svc.Jar
  $outLog=Join-Path $logDir ("{0}.out.log" -f $svc.Name)
  $errLog=Join-Path $logDir ("{0}.err.log" -f $svc.Name)
  if(Test-Path $outLog){ Remove-Item $outLog -Force }
  if(Test-Path $errLog){ Remove-Item $errLog -Force }

  $proc=Start-Process -FilePath 'java' -ArgumentList @('-jar', $jarPath) -WorkingDirectory $root -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru

  $healthUrl = "http://localhost:$($svc.Port)/actuator/health"
  $deadline = (Get-Date).AddMinutes(3)
  $healthy = $false
  do {
    Start-Sleep -Seconds 2
    try {
      $resp = Invoke-WebRequest -UseBasicParsing -Uri $healthUrl -TimeoutSec 5
      if([int]$resp.StatusCode -ge 200 -and [int]$resp.StatusCode -lt 300){
        $healthy = $true
        break
      }
    } catch {}
  } while((Get-Date) -lt $deadline)

  if(-not $healthy){
    throw "Failed to start $($svc.Name)"
  }

  Write-Output "STARTED $($svc.Name) pid=$($proc.Id)"
}

Get-NetTCPConnection -State Listen | Where-Object { $targetPorts -contains $_.LocalPort } | Select-Object LocalPort,OwningProcess | Sort-Object LocalPort | Format-Table -AutoSize
