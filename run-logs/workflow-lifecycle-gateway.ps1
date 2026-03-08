$ErrorActionPreference = "Stop"

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$gatewayBase = "http://localhost:8080"
$reportPath = Join-Path $PSScriptRoot "workflow-lifecycle-gateway-$timestamp.json"
$uploadFilePath = Join-Path $PSScriptRoot "workflow-upload-$timestamp.pdf"
$results = New-Object System.Collections.Generic.List[object]

function Parse-Json {
    param([string]$Content)
    try {
        return $Content | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Read-ErrorBody {
    param($Exception)
    if (-not $Exception.Response) {
        return $Exception.Message
    }
    try {
        $reader = New-Object System.IO.StreamReader($Exception.Response.GetResponseStream())
        return $reader.ReadToEnd()
    } catch {
        return $Exception.Message
    }
}

function Invoke-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [string]$Token = $null,
        [object]$Body = $null,
        [string]$ContentType = "application/json"
    )

    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    try {
        if ($Method -in @("GET", "DELETE")) {
            $resp = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri $Url -Headers $headers -TimeoutSec 40
        } else {
            $payload = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 15 }
            $resp = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri $Url -Headers $headers -Body $payload -ContentType $ContentType -TimeoutSec 40
        }
        return [pscustomobject]@{
            ok = $true
            status = [int]$resp.StatusCode
            body = $resp.Content
            json = Parse-Json $resp.Content
        }
    } catch {
        $status = -1
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        $body = Read-ErrorBody $_.Exception
        return [pscustomobject]@{
            ok = $false
            status = $status
            body = $body
            json = Parse-Json $body
        }
    }
}

function Invoke-ApiWithRetry {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [string]$Token = $null,
        [object]$Body = $null,
        [string]$ContentType = "application/json",
        [int]$MaxAttempts = 5,
        [int]$DelaySeconds = 2
    )

    $attempt = 0
    do {
        $attempt++
        $response = Invoke-Api -Name $Name -Method $Method -Url $Url -Token $Token -Body $Body -ContentType $ContentType
        if ($response.status -notin @(502, 503, 504)) {
            return $response
        }
        if ($attempt -lt $MaxAttempts) {
            Start-Sleep -Seconds $DelaySeconds
        }
    } while ($attempt -lt $MaxAttempts)

    return $response
}

function Add-Result {
    param(
        [string]$Step,
        [object]$Status,
        [bool]$Pass,
        [string]$Details
    )
    $results.Add([pscustomobject]@{
        step = $Step
        status = $Status
        pass = $Pass
        details = $Details
    }) | Out-Null
}

function Invoke-MultipartUpload {
    param(
        [string]$Url,
        [string]$Token,
        [string]$FilePath,
        [long]$ClaimId
    )

    $marker = "HTTPSTATUS:"
    $output = (curl.exe -s -X POST `
        -H "Authorization: Bearer $Token" `
        -F "file=@$FilePath;type=application/pdf" `
        -F "claimId=$ClaimId" `
        -F "documentType=PHOTO_EVIDENCE" `
        -F "description=Gateway workflow upload" `
        -w "$marker%{http_code}" `
        "$Url") -join ""

    $idx = $output.LastIndexOf($marker)
    if ($idx -lt 0) {
        return [pscustomobject]@{ status = -1; body = $output; json = $null }
    }
    $body = $output.Substring(0, $idx)
    $status = [int]$output.Substring($idx + $marker.Length)
    return [pscustomobject]@{
        status = $status
        body = $body
        json = Parse-Json $body
    }
}

Set-Content -Path $uploadFilePath -Value "ClaimSwift workflow upload $timestamp"

$runId = "$(Get-Date -Format 'yyyyMMddHHmmss')$((Get-Random -Minimum 100 -Maximum 999))"
$password = "Password@123"
$policyUsername = "wf_policy_$runId"
$adjusterUsername = "wf_adjuster_$runId"
$managerUsername = "wf_manager_$runId"

$policyToken = $null
$adjusterToken = $null
$managerToken = $null
$policyUserId = 0
$adjusterUserId = 0
$managerUserId = 0
$claimId = 0
$claimNumber = ""
$assessmentId = 0
$documentId = 0
$paymentCount = 0
$paymentStatus = ""
$claimStatus = ""

$policyRegister = Invoke-ApiWithRetry -Name "register-policyholder" -Method "POST" -Url "$gatewayBase/api/auth/register" -Body @{
    username = $policyUsername
    email = "$policyUsername@example.com"
    password = $password
    firstName = "Workflow"
    lastName = "Policyholder"
    phoneNumber = "9999999911"
    role = "ROLE_POLICYHOLDER"
}
Add-Result -Step "register-policyholder" -Status $policyRegister.status -Pass ($policyRegister.status -in 200,201) -Details $policyUsername

$adjusterRegister = Invoke-ApiWithRetry -Name "register-adjuster" -Method "POST" -Url "$gatewayBase/api/auth/register" -Body @{
    username = $adjusterUsername
    email = "$adjusterUsername@example.com"
    password = $password
    firstName = "Workflow"
    lastName = "Adjuster"
    phoneNumber = "9999999912"
    role = "ROLE_ADJUSTER"
}
Add-Result -Step "register-adjuster" -Status $adjusterRegister.status -Pass ($adjusterRegister.status -in 200,201) -Details $adjusterUsername

$managerRegister = Invoke-ApiWithRetry -Name "register-manager" -Method "POST" -Url "$gatewayBase/api/auth/register" -Body @{
    username = $managerUsername
    email = "$managerUsername@example.com"
    password = $password
    firstName = "Workflow"
    lastName = "Manager"
    phoneNumber = "9999999913"
    role = "ROLE_MANAGER"
}
Add-Result -Step "register-manager" -Status $managerRegister.status -Pass ($managerRegister.status -in 200,201) -Details $managerUsername

$policyLogin = Invoke-ApiWithRetry -Name "login-policyholder" -Method "POST" -Url "$gatewayBase/api/auth/login" -Body @{
    usernameOrEmail = $policyUsername
    password = $password
}
if ($policyLogin.json -and $policyLogin.json.data) {
    $policyToken = $policyLogin.json.data.accessToken
    $policyUserId = [long]$policyLogin.json.data.user.id
}
Add-Result -Step "login-policyholder" -Status $policyLogin.status -Pass ($policyLogin.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($policyToken)) -Details "token_ok"

$adjusterLogin = Invoke-ApiWithRetry -Name "login-adjuster" -Method "POST" -Url "$gatewayBase/api/auth/login" -Body @{
    usernameOrEmail = $adjusterUsername
    password = $password
}
if ($adjusterLogin.json -and $adjusterLogin.json.data) {
    $adjusterToken = $adjusterLogin.json.data.accessToken
    $adjusterUserId = [long]$adjusterLogin.json.data.user.id
}
Add-Result -Step "login-adjuster" -Status $adjusterLogin.status -Pass ($adjusterLogin.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($adjusterToken)) -Details "token_ok"

$managerLogin = Invoke-ApiWithRetry -Name "login-manager" -Method "POST" -Url "$gatewayBase/api/auth/login" -Body @{
    usernameOrEmail = $managerUsername
    password = $password
}
if ($managerLogin.json -and $managerLogin.json.data) {
    $managerToken = $managerLogin.json.data.accessToken
    $managerUserId = [long]$managerLogin.json.data.user.id
}
Add-Result -Step "login-manager" -Status $managerLogin.status -Pass ($managerLogin.status -eq 200 -and -not [string]::IsNullOrWhiteSpace($managerToken)) -Details "token_ok"

$claimCreate = Invoke-ApiWithRetry -Name "create-claim" -Method "POST" -Url "$gatewayBase/api/claims" -Token $policyToken -Body @{
    policyNumber = "POL-WF-$timestamp"
    vehicleRegistration = "WF$($runId.Substring($runId.Length-8))"
    vehicleMake = "Toyota"
    vehicleModel = "Camry"
    vehicleYear = 2023
    incidentDate = "2026-03-01"
    incidentLocation = "Bangalore"
    incidentDescription = "Workflow lifecycle claim creation"
    claimAmount = 15400.50
}
if ($claimCreate.json -and $claimCreate.json.data) {
    $claimId = [long]$claimCreate.json.data.id
    $claimNumber = [string]$claimCreate.json.data.claimNumber
}
Add-Result -Step "create-claim" -Status $claimCreate.status -Pass ($claimCreate.status -in 200,201 -and $claimId -gt 0) -Details "claimId=$claimId claimNumber=$claimNumber"

if ($claimId -gt 0) {
    $upload = Invoke-MultipartUpload -Url "$gatewayBase/api/documents/upload" -Token $policyToken -FilePath $uploadFilePath -ClaimId $claimId
    if ($upload.json -and $upload.json.data -and $upload.json.data.id) {
        $documentId = [long]$upload.json.data.id
    }
    Add-Result -Step "upload-document" -Status $upload.status -Pass ($upload.status -in 200,201 -and $documentId -gt 0) -Details "documentId=$documentId"

    $assessmentCreate = Invoke-ApiWithRetry -Name "perform-assessment" -Method "POST" -Url "$gatewayBase/api/assessments" -Token $adjusterToken -Body @{
        claimId = $claimId
        assessedAmount = 15000.00
        justification = "Documents verified and damage validated"
        notes = "Ready for approval"
    }
    if ($assessmentCreate.json -and $assessmentCreate.json.data) {
        $assessmentId = [long]$assessmentCreate.json.data.id
    }
    Add-Result -Step "perform-assessment" -Status $assessmentCreate.status -Pass ($assessmentCreate.status -in 200,201 -and $assessmentId -gt 0) -Details "assessmentId=$assessmentId"

    if ($assessmentId -gt 0) {
        $assessmentDecision = Invoke-ApiWithRetry -Name "assessment-decision-approved" -Method "POST" -Url "$gatewayBase/api/assessments/decision" -Token $adjusterToken -Body @{
            assessmentId = $assessmentId
            decision = "APPROVED"
            finalAmount = 15000.00
            justification = "Approved after review"
        }
        Add-Result -Step "assessment-decision-approved" -Status $assessmentDecision.status -Pass ($assessmentDecision.status -eq 200) -Details (($assessmentDecision.body | Out-String).Trim())
    } else {
        Add-Result -Step "assessment-decision-approved" -Status "SKIPPED" -Pass $false -Details "Skipped because perform-assessment failed"
    }
} else {
    Add-Result -Step "upload-document" -Status "SKIPPED" -Pass $false -Details "Skipped because create-claim failed"
    Add-Result -Step "perform-assessment" -Status "SKIPPED" -Pass $false -Details "Skipped because create-claim failed"
    Add-Result -Step "assessment-decision-approved" -Status "SKIPPED" -Pass $false -Details "Skipped because create-claim failed"
}

if ($claimId -gt 0) {
    for ($i = 0; $i -lt 12; $i++) {
        $claimRead = Invoke-Api -Name "claim-status-check" -Method "GET" -Url "$gatewayBase/api/claims/$claimId" -Token $adjusterToken
        if ($claimRead.json -and $claimRead.json.data -and $claimRead.json.data.status) {
            $claimStatus = [string]$claimRead.json.data.status
        }
        $paymentsRead = Invoke-Api -Name "payment-check" -Method "GET" -Url "$gatewayBase/api/payments/claim/$claimId" -Token $adjusterToken
        if ($paymentsRead.json -and $paymentsRead.json.data) {
            $paymentCount = @($paymentsRead.json.data).Count
            if ($paymentCount -gt 0) {
                $paymentStatus = [string]$paymentsRead.json.data[0].status
            }
        }
        if ($claimStatus -eq "PAID" -and $paymentCount -gt 0) {
            break
        }
        Start-Sleep -Seconds 2
    }
}

Add-Result -Step "claim-status-after-decision" -Status $claimStatus -Pass ($claimStatus -in @("APPROVED","PAID")) -Details "claimStatus=$claimStatus"
Add-Result -Step "payment-processed" -Status $paymentCount -Pass ($paymentCount -gt 0 -and $paymentStatus -eq "APPROVED") -Details "count=$paymentCount firstStatus=$paymentStatus"

if ($claimId -gt 0 -and -not [string]::IsNullOrWhiteSpace($claimNumber)) {
    $documentsByClaim = Invoke-Api -Name "document-db-check" -Method "GET" -Url "$gatewayBase/api/documents/claim/$claimId" -Token $policyToken
    $assessmentByClaim = Invoke-Api -Name "assessment-db-check" -Method "GET" -Url "$gatewayBase/api/assessments/claim/$claimId" -Token $adjusterToken
    $claimByNumber = Invoke-Api -Name "claim-db-check" -Method "GET" -Url "$gatewayBase/api/claims/number/$claimNumber" -Token $managerToken
    $dbPass = ($documentsByClaim.status -eq 200 -and $assessmentByClaim.status -eq 200 -and $claimByNumber.status -eq 200)
    Add-Result -Step "db-data-check" -Status "$($documentsByClaim.status)/$($assessmentByClaim.status)/$($claimByNumber.status)" -Pass $dbPass -Details "claim/document/assessment records accessible"
} else {
    Add-Result -Step "db-data-check" -Status "SKIPPED" -Pass $false -Details "Skipped because claim creation failed"
}

$notifications = Invoke-Api -Name "notifications" -Method "GET" -Url "$gatewayBase/api/notifications" -Token $policyToken
$notificationCount = 0
$paymentNotificationCount = 0
if ($notifications.json -and $notifications.json.data) {
    $allNotifications = @($notifications.json.data)
    $notificationCount = $allNotifications.Count
    $paymentNotificationCount = @($allNotifications | Where-Object { $_.message -match "Payment" -or $_.title -match "Payment" }).Count
}
Add-Result -Step "notifications" -Status $notifications.status -Pass ($notifications.status -eq 200 -and $notificationCount -gt 0) -Details "total=$notificationCount paymentRelated=$paymentNotificationCount"

$claimSummary = Invoke-ApiWithRetry -Name "reporting-claims-summary" -Method "GET" -Url "$gatewayBase/api/reports/claims/summary?startDate=2026-01-01&endDate=2026-12-31" -Token $managerToken
$paymentsSummary = Invoke-ApiWithRetry -Name "reporting-payments-summary" -Method "GET" -Url "$gatewayBase/api/reports/payments?startDate=2026-01-01&endDate=2026-12-31" -Token $managerToken
$reportPass = ($claimSummary.status -eq 200 -and $paymentsSummary.status -eq 200)
Add-Result -Step "reporting-updated" -Status "$($claimSummary.status)/$($paymentsSummary.status)" -Pass $reportPass -Details "reports reachable via gateway"

$passed = ($results | Where-Object { $_.pass }).Count
$total = $results.Count

$report = [ordered]@{
    timestamp = (Get-Date).ToString("s")
    claimId = $claimId
    claimNumber = $claimNumber
    policyUserId = $policyUserId
    adjusterUserId = $adjusterUserId
    managerUserId = $managerUserId
    passed = $passed
    total = $total
    success = ($passed -eq $total)
    results = $results
}

$report | ConvertTo-Json -Depth 20 | Set-Content -Path $reportPath
Write-Output "REPORT=$reportPath"
