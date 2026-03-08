$ErrorActionPreference = "Stop"

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $PSScriptRoot "api-smoke-report-$timestamp.json"
$postmanEnvPath = Join-Path $PSScriptRoot "postman-env-$timestamp.json"
$uploadFilePath = Join-Path $PSScriptRoot "smoke-upload-$timestamp.pdf"

$secret = "ClaimSwiftSecretKeyForJwtSigningAndVerificationMustBeAtLeast256BitsLong"

$results = New-Object System.Collections.Generic.List[object]

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    return [Convert]::ToBase64String($Bytes).TrimEnd("=") -replace "\+", "-" -replace "/", "_"
}

function New-JwtToken {
    param(
        [long]$UserId,
        [string]$Username,
        [string[]]$Roles
    )

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $header = @{ alg = "HS256"; typ = "JWT" } | ConvertTo-Json -Compress
    $payload = @{
        sub = "$UserId"
        userId = $UserId
        username = $Username
        roles = $Roles
        role = $Roles[0]
        iat = $now
        exp = $now + 86400
    } | ConvertTo-Json -Compress

    $encodedHeader = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))
    $encodedPayload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payload))
    $unsignedToken = "$encodedHeader.$encodedPayload"

    $hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($secret))
    $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsignedToken)))
    return "$unsignedToken.$signature"
}

function Parse-Json {
    param([string]$Content)
    try {
        return $Content | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Invoke-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [string]$Token = $null,
        [object]$Body = $null,
        [string]$ContentType = "application/json",
        [switch]$Binary
    )

    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    $result = [ordered]@{
        name = $Name
        method = $Method
        url = $Url
        status = -1
        ok = $false
        content = ""
    }

    try {
        if ($Method -in @("GET", "DELETE")) {
            $response = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri $Url -Headers $headers -TimeoutSec 20
        } else {
            $payload = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 15 }
            $response = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri $Url -Headers $headers -Body $payload -ContentType $ContentType -TimeoutSec 20
        }

        $result.status = [int]$response.StatusCode
        $result.ok = $true
        if ($Binary) {
            $result.content = "<binary:$($response.RawContentLength)bytes>"
        } else {
            $result.content = $response.Content
        }
    } catch {
        if ($_.Exception.Response) {
            $webResponse = $_.Exception.Response
            $result.status = [int]$webResponse.StatusCode
            try {
                $streamReader = New-Object IO.StreamReader($webResponse.GetResponseStream())
                $result.content = $streamReader.ReadToEnd()
            } catch {
                $result.content = $_.Exception.Message
            }
        } else {
            $result.content = $_.Exception.Message
        }
    }

    $results.Add([pscustomobject]$result) | Out-Null
    return [pscustomobject]$result
}

function Invoke-MultipartUpload {
    param(
        [string]$Name,
        [string]$Url,
        [string]$Token,
        [string]$FilePath,
        [long]$ClaimId
    )

    $statusMarker = "HTTPSTATUS:"
    $curlOutput = (curl.exe -s -X POST `
        -H "Authorization: Bearer $Token" `
        -F "file=@$FilePath;type=application/pdf" `
        -F "claimId=$ClaimId" `
        -F "documentType=PHOTO_EVIDENCE" `
        -F "description=Smoke test upload" `
        -w "$statusMarker%{http_code}" `
        "$Url") -join ""

    $statusIndex = $curlOutput.LastIndexOf($statusMarker)
    if ($statusIndex -lt 0) {
        $status = -1
        $body = $curlOutput
    } else {
        $body = $curlOutput.Substring(0, $statusIndex)
        $status = [int]$curlOutput.Substring($statusIndex + $statusMarker.Length)
    }

    $result = [pscustomobject]@{
        name = $Name
        method = "POST"
        url = $Url
        status = $status
        ok = ($status -ge 200 -and $status -lt 300)
        content = $body
    }
    $results.Add($result) | Out-Null
    return $result
}

Set-Content -Path $uploadFilePath -Value "ClaimSwift smoke test file $timestamp"

$defaultPassword = "Password@123"
$policyUsername = "policy.$timestamp"
$adjusterUsername = "adjuster.$timestamp"
$managerUsername = "manager.$timestamp"
$policyEmail = "policy.$timestamp@example.com"
$adjusterEmail = "adjuster.$timestamp@example.com"
$managerEmail = "manager.$timestamp@example.com"

$policyUserId = 0
$adjusterUserId = 0
$managerUserId = 0
$policyToken = $null
$adjusterToken = $null
$managerToken = $null

# Health and registry checks
Invoke-Api -Name "Health config-server" -Method "GET" -Url "http://localhost:8888/actuator/health" | Out-Null
Invoke-Api -Name "Health eureka-server" -Method "GET" -Url "http://localhost:8761/actuator/health" | Out-Null
Invoke-Api -Name "Health api-gateway" -Method "GET" -Url "http://localhost:8080/actuator/health" | Out-Null
Invoke-Api -Name "Health auth-service" -Method "GET" -Url "http://localhost:8081/actuator/health" | Out-Null
Invoke-Api -Name "Health claim-service" -Method "GET" -Url "http://localhost:8082/actuator/health" | Out-Null
Invoke-Api -Name "Health document-service" -Method "GET" -Url "http://localhost:8083/actuator/health" | Out-Null
Invoke-Api -Name "Health assessment-service" -Method "GET" -Url "http://localhost:8084/actuator/health" | Out-Null
Invoke-Api -Name "Health payment-service" -Method "GET" -Url "http://localhost:8085/actuator/health" | Out-Null
Invoke-Api -Name "Health notification-service" -Method "GET" -Url "http://localhost:8086/actuator/health" | Out-Null
Invoke-Api -Name "Health reporting-service" -Method "GET" -Url "http://localhost:8087/actuator/health" | Out-Null

# Auth APIs
$policyRegister = Invoke-Api -Name "Auth register policyholder" -Method "POST" -Url "http://localhost:8081/api/auth/register" -Body @{
    username = $policyUsername
    email = $policyEmail
    password = $defaultPassword
    firstName = "Smoke"
    lastName = "Policyholder"
    phoneNumber = "9999999999"
    role = "ROLE_POLICYHOLDER"
}
$policyRegisterJson = Parse-Json $policyRegister.content
if ($policyRegisterJson -and $policyRegisterJson.data) {
    $policyToken = $policyRegisterJson.data.accessToken
    $policyUserId = [long]$policyRegisterJson.data.user.id
}

$adjusterRegister = Invoke-Api -Name "Auth register adjuster" -Method "POST" -Url "http://localhost:8081/api/auth/register" -Body @{
    username = $adjusterUsername
    email = $adjusterEmail
    password = $defaultPassword
    firstName = "Smoke"
    lastName = "Adjuster"
    phoneNumber = "9999999998"
    role = "ROLE_ADJUSTER"
}
$adjusterRegisterJson = Parse-Json $adjusterRegister.content
if ($adjusterRegisterJson -and $adjusterRegisterJson.data) {
    $adjusterToken = $adjusterRegisterJson.data.accessToken
    $adjusterUserId = [long]$adjusterRegisterJson.data.user.id
}

$managerRegister = Invoke-Api -Name "Auth register manager" -Method "POST" -Url "http://localhost:8081/api/auth/register" -Body @{
    username = $managerUsername
    email = $managerEmail
    password = $defaultPassword
    firstName = "Smoke"
    lastName = "Manager"
    phoneNumber = "9999999997"
    role = "ROLE_MANAGER"
}
$managerRegisterJson = Parse-Json $managerRegister.content
if ($managerRegisterJson -and $managerRegisterJson.data) {
    $managerToken = $managerRegisterJson.data.accessToken
    $managerUserId = [long]$managerRegisterJson.data.user.id
}

$loginResult = Invoke-Api -Name "Auth login manager" -Method "POST" -Url "http://localhost:8081/api/auth/login" -Body @{
    usernameOrEmail = $managerUsername
    password = $defaultPassword
}
$loginJson = Parse-Json $loginResult.content
if ($loginJson -and $loginJson.data -and $loginJson.data.accessToken) {
    $managerToken = $loginJson.data.accessToken
}

$refreshResult = Invoke-Api -Name "Auth refresh" -Method "POST" -Url "http://localhost:8081/api/auth/refresh" -Token $managerToken -Body "{}"
$refreshJson = Parse-Json $refreshResult.content
if ($refreshJson -and $refreshJson.data -and $refreshJson.data.accessToken) {
    $managerToken = $refreshJson.data.accessToken
}

if (-not $policyToken) {
    $policyUserId = 1101
    $policyToken = New-JwtToken -UserId $policyUserId -Username "policyholder.fallback" -Roles @("ROLE_POLICYHOLDER")
}
if (-not $adjusterToken) {
    $adjusterUserId = 1102
    $adjusterToken = New-JwtToken -UserId $adjusterUserId -Username "adjuster.fallback" -Roles @("ROLE_ADJUSTER")
}
if (-not $managerToken) {
    $managerUserId = 1103
    $managerToken = New-JwtToken -UserId $managerUserId -Username "manager.fallback" -Roles @("ROLE_MANAGER")
}

Invoke-Api -Name "Auth me" -Method "GET" -Url "http://localhost:8081/api/auth/me" -Token $managerToken | Out-Null
Invoke-Api -Name "Auth health" -Method "GET" -Url "http://localhost:8081/api/auth/health" | Out-Null

# Gateway route checks
Invoke-Api -Name "Gateway auth route" -Method "GET" -Url "http://localhost:8080/api/auth/health" | Out-Null
Invoke-Api -Name "Gateway claim route" -Method "GET" -Url "http://localhost:8080/api/claims/health" -Token $managerToken | Out-Null
Invoke-Api -Name "Gateway report route" -Method "GET" -Url "http://localhost:8080/api/reports/claims/summary" -Token $managerToken | Out-Null

# Claim APIs
$claimBody = @{
    policyNumber = "POL-$timestamp"
    vehicleRegistration = "DL01SMK$($timestamp.Substring($timestamp.Length - 4))"
    vehicleMake = "Toyota"
    vehicleModel = "Camry"
    vehicleYear = 2022
    incidentDate = "2026-03-01"
    incidentLocation = "Bangalore"
    incidentDescription = "Rear-end collision during smoke test"
    claimAmount = 12500.75
}

$submitClaim = Invoke-Api -Name "Claim submit primary" -Method "POST" -Url "http://localhost:8082/api/claims" -Token $policyToken -Body $claimBody
$submitClaimJson = Parse-Json $submitClaim.content
$claimId = if ($submitClaimJson -and $submitClaimJson.data -and $submitClaimJson.data.id) { [long]$submitClaimJson.data.id } else { 0 }
$claimNumber = if ($submitClaimJson -and $submitClaimJson.data -and $submitClaimJson.data.claimNumber) { [string]$submitClaimJson.data.claimNumber } else { "" }

$deleteClaim = Invoke-Api -Name "Claim submit delete-target" -Method "POST" -Url "http://localhost:8082/api/claims" -Token $policyToken -Body @{
    policyNumber = "POL-DEL-$timestamp"
    vehicleRegistration = "KA05DEL$($timestamp.Substring($timestamp.Length - 3))"
    vehicleMake = "Honda"
    vehicleModel = "City"
    vehicleYear = 2021
    incidentDate = "2026-02-28"
    incidentLocation = "Pune"
    incidentDescription = "Delete flow claim"
    claimAmount = 9000.25
}
$deleteClaimJson = Parse-Json $deleteClaim.content
$deleteClaimId = if ($deleteClaimJson -and $deleteClaimJson.data -and $deleteClaimJson.data.id) { [long]$deleteClaimJson.data.id } else { 0 }

if ($claimId -gt 0) {
    Invoke-Api -Name "Claim get by id" -Method "GET" -Url "http://localhost:8082/api/claims/$claimId" -Token $managerToken | Out-Null
    Invoke-Api -Name "Claim get by number" -Method "GET" -Url "http://localhost:8082/api/claims/number/$claimNumber" -Token $managerToken | Out-Null
    Invoke-Api -Name "Claim my-claims" -Method "GET" -Url "http://localhost:8082/api/claims/my-claims" -Token $policyToken | Out-Null
    Invoke-Api -Name "Claim history" -Method "GET" -Url "http://localhost:8082/api/claims/history" -Token $policyToken | Out-Null
    Invoke-Api -Name "Claim list page" -Method "GET" -Url "http://localhost:8082/api/claims?page=0&size=10&sortBy=createdAt&sortDir=desc" -Token $adjusterToken | Out-Null
    Invoke-Api -Name "Claim update" -Method "PUT" -Url "http://localhost:8082/api/claims/$claimId" -Token $managerToken -Body @{
        policyNumber = "POL-$timestamp-UPD"
        vehicleRegistration = "DL01UPD$($timestamp.Substring($timestamp.Length - 4))"
        vehicleMake = "Toyota"
        vehicleModel = "Camry"
        vehicleYear = 2022
        incidentDate = "2026-03-01"
        incidentLocation = "Bangalore"
        incidentDescription = "Updated after review"
        claimAmount = 13000.00
    } | Out-Null
    Invoke-Api -Name "Claim assign" -Method "PATCH" -Url "http://localhost:8082/api/claims/$claimId/assign" -Token $managerToken -Body @{ adjusterId = $adjusterUserId } | Out-Null
    Invoke-Api -Name "Claim assigned list" -Method "GET" -Url "http://localhost:8082/api/claims/assigned" -Token $adjusterToken | Out-Null
    Invoke-Api -Name "Claim pending list" -Method "GET" -Url "http://localhost:8082/api/claims/pending" -Token $managerToken | Out-Null
    Invoke-Api -Name "Claim put status ADJUSTED" -Method "PUT" -Url "http://localhost:8082/api/claims/$claimId/status" -Token $managerToken -Body @{
        status = "ADJUSTED"
        notes = "Adjusted after assessment"
        assignedAdjusterId = $adjusterUserId
    } | Out-Null
    Invoke-Api -Name "Claim patch status APPROVED" -Method "PATCH" -Url "http://localhost:8082/api/claims/$claimId/status" -Token $managerToken -Body @{
        status = "APPROVED"
        notes = "Approved after adjustment"
    } | Out-Null
    Invoke-Api -Name "Claim by status" -Method "GET" -Url "http://localhost:8082/api/claims/status/APPROVED" -Token $managerToken | Out-Null
    Invoke-Api -Name "Claim statistics" -Method "GET" -Url "http://localhost:8082/api/claims/statistics" -Token $managerToken | Out-Null
    Invoke-Api -Name "Claim search" -Method "GET" -Url "http://localhost:8082/api/claims/search?query=POL-$timestamp" -Token $managerToken | Out-Null
    Invoke-Api -Name "Claim by adjuster" -Method "GET" -Url "http://localhost:8082/api/claims/adjuster/$adjusterUserId" -Token $managerToken | Out-Null
    Invoke-Api -Name "Claim summary" -Method "GET" -Url "http://localhost:8082/api/claims/summary?startDate=2026-01-01&endDate=2026-12-31" -Token $managerToken | Out-Null
    Invoke-Api -Name "Claim internal all" -Method "GET" -Url "http://localhost:8082/api/claims/internal/all" -Token $managerToken | Out-Null
    Invoke-Api -Name "Claim internal by status" -Method "GET" -Url "http://localhost:8082/api/claims/internal/status/APPROVED" -Token $managerToken | Out-Null
    Invoke-Api -Name "Claim internal by adjuster" -Method "GET" -Url "http://localhost:8082/api/claims/internal/adjuster/$adjusterUserId" -Token $managerToken | Out-Null
    Invoke-Api -Name "Claim internal summary" -Method "GET" -Url "http://localhost:8082/api/claims/internal/summary?startDate=2026-01-01&endDate=2026-12-31" -Token $managerToken | Out-Null
}
Invoke-Api -Name "Claim health" -Method "GET" -Url "http://localhost:8082/api/claims/health" | Out-Null
if ($deleteClaimId -gt 0) {
    Invoke-Api -Name "Claim delete" -Method "DELETE" -Url "http://localhost:8082/api/claims/$deleteClaimId" -Token $managerToken | Out-Null
}

# Document APIs
$documentId = 0
if ($claimId -gt 0) {
    $uploadResult = Invoke-MultipartUpload -Name "Document upload" -Url "http://localhost:8083/api/documents/upload" -Token $policyToken -FilePath $uploadFilePath -ClaimId $claimId
    $uploadJson = Parse-Json $uploadResult.content
    if ($uploadJson -and $uploadJson.data -and $uploadJson.data.id) {
        $documentId = [long]$uploadJson.data.id
    }

    if ($documentId -gt 0) {
        Invoke-Api -Name "Document get by id" -Method "GET" -Url "http://localhost:8083/api/documents/$documentId" -Token $policyToken | Out-Null
        Invoke-Api -Name "Document by claim" -Method "GET" -Url "http://localhost:8083/api/documents/claim/$claimId" -Token $policyToken | Out-Null
        Invoke-Api -Name "Document by user" -Method "GET" -Url "http://localhost:8083/api/documents/user/$policyUserId" -Token $policyToken | Out-Null
        Invoke-Api -Name "Document by claim+type" -Method "GET" -Url "http://localhost:8083/api/documents/claim/$claimId/type/PHOTO_EVIDENCE" -Token $policyToken | Out-Null
        Invoke-Api -Name "Document download" -Method "GET" -Url "http://localhost:8083/api/documents/$documentId/download" -Token $policyToken -Binary | Out-Null
    }
}

# Assessment APIs
$assessmentId = 0
if ($claimId -gt 0) {
    $assessResult = Invoke-Api -Name "Assessment create" -Method "POST" -Url "http://localhost:8084/api/assessments" -Token $adjusterToken -Body @{
        claimId = $claimId
        assessedAmount = 12000.00
        justification = "Primary inspection complete"
        notes = "No red flags"
    }
    $assessJson = Parse-Json $assessResult.content
    if ($assessJson -and $assessJson.data -and $assessJson.data.id) {
        $assessmentId = [long]$assessJson.data.id
    }

    if ($assessmentId -gt 0) {
        Invoke-Api -Name "Assessment decision" -Method "POST" -Url "http://localhost:8084/api/assessments/decision" -Token $adjusterToken -Body @{
            assessmentId = $assessmentId
            decision = "ADJUSTED"
            finalAmount = 11800.50
            justification = "Depreciation applied"
        } | Out-Null
        Invoke-Api -Name "Assessment adjustment" -Method "POST" -Url "http://localhost:8084/api/assessments/adjustment" -Token $adjusterToken -Body @{
            assessmentId = $assessmentId
            claimId = $claimId
            adjustedAmount = 11800.50
            adjustmentType = "OTHER"
            reason = "Manual adjustment for wear and tear"
            detailedNotes = "Detailed notes for audit"
        } | Out-Null
        Invoke-Api -Name "Assessment get by id" -Method "GET" -Url "http://localhost:8084/api/assessments/$assessmentId" -Token $adjusterToken | Out-Null
        Invoke-Api -Name "Assessment adjustments by assessment" -Method "GET" -Url "http://localhost:8084/api/assessments/$assessmentId/adjustments" -Token $adjusterToken | Out-Null
    }

    Invoke-Api -Name "Assessment get by claim" -Method "GET" -Url "http://localhost:8084/api/assessments/claim/$claimId" -Token $adjusterToken | Out-Null
    Invoke-Api -Name "Assessment my-assessments" -Method "GET" -Url "http://localhost:8084/api/assessments/my-assessments" -Token $adjusterToken | Out-Null
    Invoke-Api -Name "Assessment request" -Method "POST" -Url "http://localhost:8084/api/assessments/request?claimId=$claimId" -Token $adjusterToken -Body "{}" | Out-Null
    Invoke-Api -Name "Assessment notify-complete" -Method "POST" -Url "http://localhost:8084/api/assessments/notify-complete" -Token $adjusterToken -Body @{
        claimId = $claimId
        claimNumber = $claimNumber
        approvedAmount = 11800.50
    } | Out-Null
}
Invoke-Api -Name "Assessment health" -Method "GET" -Url "http://localhost:8084/api/assessments/health" | Out-Null
if ($documentId -gt 0) {
    Invoke-Api -Name "Document delete" -Method "DELETE" -Url "http://localhost:8083/api/documents/$documentId" -Token $policyToken | Out-Null
}

# Payment APIs
$paymentId = 0
if ($claimId -gt 0) {
    $paymentResult = Invoke-Api -Name "Payment process" -Method "POST" -Url "http://localhost:8085/api/payments" -Token $managerToken -Body @{
        claimId = $claimId
        policyholderId = $policyUserId
        amount = 11800.50
        paymentMethod = "NEFT"
        beneficiaryName = "Smoke Policyholder"
        accountNumber = "123456789012"
        ifscCode = "HDFC0001234"
        bankName = "HDFC Bank"
        forceSimulateSuccess = $true
        forceSimulateFailure = $false
    }
    $paymentJson = Parse-Json $paymentResult.content
    if ($paymentJson -and $paymentJson.data -and $paymentJson.data.id) {
        $paymentId = [long]$paymentJson.data.id
    }

    if ($paymentId -gt 0) {
        Invoke-Api -Name "Payment get by id" -Method "GET" -Url "http://localhost:8085/api/payments/$paymentId" -Token $managerToken | Out-Null
    }
    Invoke-Api -Name "Payment by claim" -Method "GET" -Url "http://localhost:8085/api/payments/claim/$claimId" -Token $managerToken | Out-Null
    Invoke-Api -Name "Payment internal all" -Method "GET" -Url "http://localhost:8085/api/payments/internal/all" -Token $managerToken | Out-Null
    Invoke-Api -Name "Payment internal by status" -Method "GET" -Url "http://localhost:8085/api/payments/internal/status/APPROVED" -Token $managerToken | Out-Null
    Invoke-Api -Name "Payment internal summary" -Method "GET" -Url "http://localhost:8085/api/payments/internal/summary" -Token $managerToken | Out-Null
    Invoke-Api -Name "Payment internal by claim" -Method "GET" -Url "http://localhost:8085/api/payments/internal/claim/$claimId" -Token $managerToken | Out-Null
    Invoke-Api -Name "Claim read after payment" -Method "GET" -Url "http://localhost:8082/api/claims/$claimId" -Token $managerToken | Out-Null
}

# Notification APIs
$notificationId = 0
$sentNotificationId = 0
$testNotification = Invoke-Api -Name "Notification test" -Method "POST" -Url "http://localhost:8086/api/notifications/test" -Token $policyToken -Body "{}"
$testNotificationJson = Parse-Json $testNotification.content
if ($testNotificationJson -and $testNotificationJson.data -and $testNotificationJson.data.id) {
    $notificationId = [long]$testNotificationJson.data.id
}

Invoke-Api -Name "Notification my list" -Method "GET" -Url "http://localhost:8086/api/notifications" -Token $policyToken | Out-Null
Invoke-Api -Name "Notification unread list" -Method "GET" -Url "http://localhost:8086/api/notifications/unread" -Token $policyToken | Out-Null
Invoke-Api -Name "Notification unread count" -Method "GET" -Url "http://localhost:8086/api/notifications/unread/count" -Token $policyToken | Out-Null
if ($notificationId -gt 0) {
    Invoke-Api -Name "Notification mark read" -Method "PUT" -Url "http://localhost:8086/api/notifications/$notificationId/read" -Token $policyToken -Body "{}" | Out-Null
}
Invoke-Api -Name "Notification mark all read" -Method "PUT" -Url "http://localhost:8086/api/notifications/read-all" -Token $policyToken -Body "{}" | Out-Null

$sendNotification = Invoke-Api -Name "Notification send" -Method "POST" -Url "http://localhost:8086/api/notifications/send" -Token $managerToken -Body @{
    userId = $policyUserId
    claimId = if ($claimId -gt 0) { $claimId } else { $null }
    title = "Smoke Notification"
    message = "Notification endpoint smoke test"
    type = "SYSTEM_MESSAGE"
    actionUrl = "/claims/$claimId"
    senderId = $managerUserId
}
$sendNotificationJson = Parse-Json $sendNotification.content
if ($sendNotificationJson -and $sendNotificationJson.data -and $sendNotificationJson.data.id) {
    $sentNotificationId = [long]$sendNotificationJson.data.id
}

if ($sentNotificationId -gt 0) {
    Invoke-Api -Name "Notification delete" -Method "DELETE" -Url "http://localhost:8086/api/notifications/$sentNotificationId" -Token $policyToken | Out-Null
}

# Reporting APIs
Invoke-Api -Name "Report claim summary" -Method "GET" -Url "http://localhost:8087/api/reports/claims/summary?startDate=2026-01-01&endDate=2026-12-31" -Token $managerToken | Out-Null
Invoke-Api -Name "Report claim summary pdf" -Method "GET" -Url "http://localhost:8087/api/reports/claims/summary/pdf?startDate=2026-01-01&endDate=2026-12-31" -Token $managerToken -Binary | Out-Null
Invoke-Api -Name "Report payments" -Method "GET" -Url "http://localhost:8087/api/reports/payments?startDate=2026-01-01&endDate=2026-12-31" -Token $managerToken | Out-Null
Invoke-Api -Name "Report payments pdf" -Method "GET" -Url "http://localhost:8087/api/reports/payments/pdf?startDate=2026-01-01&endDate=2026-12-31" -Token $managerToken -Binary | Out-Null
Invoke-Api -Name "Report adjuster performance" -Method "GET" -Url "http://localhost:8087/api/reports/adjusters/performance?startDate=2026-01-01&endDate=2026-12-31" -Token $managerToken | Out-Null
Invoke-Api -Name "Report adjuster performance pdf" -Method "GET" -Url "http://localhost:8087/api/reports/adjusters/performance/pdf?startDate=2026-01-01&endDate=2026-12-31" -Token $managerToken -Binary | Out-Null
Invoke-Api -Name "Report claim-event" -Method "POST" -Url "http://localhost:8087/api/reports/claim-event" -Token $policyToken -Body @{
    claimId = $claimId
    claimNumber = $claimNumber
    eventType = "SMOKE_TEST"
    timestamp = (Get-Date).ToString("s")
} | Out-Null
Invoke-Api -Name "Report status-change" -Method "POST" -Url "http://localhost:8087/api/reports/status-change" -Token $managerToken -Body @{
    claimId = $claimId
    claimNumber = $claimNumber
    status = "APPROVED"
    oldStatus = "UNDER_REVIEW"
    newStatus = "APPROVED"
    timestamp = (Get-Date).ToString("s")
} | Out-Null

Invoke-Api -Name "Auth logout" -Method "POST" -Url "http://localhost:8081/api/auth/logout" -Token $managerToken -Body "{}" | Out-Null

$okCount = ($results | Where-Object { $_.ok }).Count
$failCount = ($results | Where-Object { -not $_.ok }).Count

$report = [ordered]@{
    generatedAt = (Get-Date).ToString("s")
    totals = @{
        total = $results.Count
        passed = $okCount
        failed = $failCount
    }
    context = @{
        claimId = $claimId
        claimNumber = $claimNumber
        deleteClaimId = $deleteClaimId
        assessmentId = $assessmentId
        paymentId = $paymentId
        documentId = $documentId
        notificationId = $notificationId
        sentNotificationId = $sentNotificationId
        policyUserId = $policyUserId
        adjusterUserId = $adjusterUserId
        managerUserId = $managerUserId
    }
    results = $results
}

$report | ConvertTo-Json -Depth 20 | Set-Content -Path $reportPath

$postmanEnv = [ordered]@{
    name = "ClaimSwift Local Smoke"
    values = @(
        @{ key = "gateway_base"; value = "http://localhost:8080"; enabled = $true },
        @{ key = "auth_base"; value = "http://localhost:8081"; enabled = $true },
        @{ key = "claim_base"; value = "http://localhost:8082"; enabled = $true },
        @{ key = "document_base"; value = "http://localhost:8083"; enabled = $true },
        @{ key = "assessment_base"; value = "http://localhost:8084"; enabled = $true },
        @{ key = "payment_base"; value = "http://localhost:8085"; enabled = $true },
        @{ key = "notification_base"; value = "http://localhost:8086"; enabled = $true },
        @{ key = "reporting_base"; value = "http://localhost:8087"; enabled = $true },
        @{ key = "policy_token"; value = $policyToken; enabled = $true },
        @{ key = "adjuster_token"; value = $adjusterToken; enabled = $true },
        @{ key = "manager_token"; value = $managerToken; enabled = $true },
        @{ key = "claim_id"; value = "$claimId"; enabled = $true },
        @{ key = "claim_number"; value = "$claimNumber"; enabled = $true },
        @{ key = "assessment_id"; value = "$assessmentId"; enabled = $true },
        @{ key = "payment_id"; value = "$paymentId"; enabled = $true },
        @{ key = "document_id"; value = "$documentId"; enabled = $true },
        @{ key = "notification_id"; value = "$notificationId"; enabled = $true },
        @{ key = "policy_user_id"; value = "$policyUserId"; enabled = $true },
        @{ key = "adjuster_user_id"; value = "$adjusterUserId"; enabled = $true },
        @{ key = "manager_user_id"; value = "$managerUserId"; enabled = $true }
    )
}

$postmanEnv | ConvertTo-Json -Depth 8 | Set-Content -Path $postmanEnvPath

Write-Host "Smoke test completed."
Write-Host "Report: $reportPath"
Write-Host "Postman environment: $postmanEnvPath"
Write-Host "Passed: $okCount, Failed: $failCount, Total: $($results.Count)"
