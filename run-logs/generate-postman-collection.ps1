$ErrorActionPreference = 'Stop'

$root = 'c:\Users\shashank.nath\Desktop\Claimswift'
$latestReport = Get-ChildItem -Path (Join-Path $root 'run-logs') -Filter 'api-smoke-report-*.json' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $latestReport) {
    throw 'No api-smoke-report-*.json found under run-logs.'
}

$report = Get-Content -Path $latestReport.FullName | ConvertFrom-Json
$ctx = $report.context

function Get-FolderName {
    param([string]$Url)
    $uri = [uri]$Url
    switch ($uri.Port) {
        8888 { 'Config Server' }
        8761 { 'Eureka Server' }
        8080 { 'API Gateway' }
        8081 { 'Auth Service' }
        8082 { 'Claim Service' }
        8083 { 'Document Service' }
        8084 { 'Assessment Service' }
        8085 { 'Payment Service' }
        8086 { 'Notification Service' }
        8087 { 'Reporting Service' }
        default { 'Misc' }
    }
}

function Get-BaseVar {
    param([int]$Port)
    switch ($Port) {
        8888 { 'config_base' }
        8761 { 'eureka_base' }
        8080 { 'gateway_base' }
        8081 { 'auth_base' }
        8082 { 'claim_base' }
        8083 { 'document_base' }
        8084 { 'assessment_base' }
        8085 { 'payment_base' }
        8086 { 'notification_base' }
        8087 { 'reporting_base' }
        default { 'gateway_base' }
    }
}

function Convert-UrlToVariables {
    param([string]$Url)

    $uri = [uri]$Url
    $baseVar = Get-BaseVar -Port $uri.Port
    $pathAndQuery = $uri.PathAndQuery

    $pathAndQuery = $pathAndQuery -replace '/api/claims/number/CLM-[A-Z0-9]+', '/api/claims/number/{{claim_number}}'
    $pathAndQuery = $pathAndQuery -replace '/api/claims/\d+/assign', '/api/claims/{{claim_id}}/assign'
    $pathAndQuery = $pathAndQuery -replace '/api/claims/\d+/status', '/api/claims/{{claim_id}}/status'
    $pathAndQuery = $pathAndQuery -replace '/api/claims/adjuster/\d+', '/api/claims/adjuster/{{adjuster_user_id}}'
    $pathAndQuery = $pathAndQuery -replace '/api/claims/internal/adjuster/\d+', '/api/claims/internal/adjuster/{{adjuster_user_id}}'
    $pathAndQuery = $pathAndQuery -replace '/api/claims/\d+', '/api/claims/{{claim_id}}'

    $pathAndQuery = $pathAndQuery -replace '/api/documents/claim/\d+', '/api/documents/claim/{{claim_id}}'
    $pathAndQuery = $pathAndQuery -replace '/api/documents/user/\d+', '/api/documents/user/{{policy_user_id}}'
    $pathAndQuery = $pathAndQuery -replace '/api/documents/\d+/download', '/api/documents/{{document_id}}/download'
    $pathAndQuery = $pathAndQuery -replace '/api/documents/\d+', '/api/documents/{{document_id}}'

    $pathAndQuery = $pathAndQuery -replace '/api/assessments/claim/\d+', '/api/assessments/claim/{{claim_id}}'
    $pathAndQuery = $pathAndQuery -replace '/api/assessments/\d+/adjustments', '/api/assessments/{{assessment_id}}/adjustments'
    $pathAndQuery = $pathAndQuery -replace '/api/assessments/\d+', '/api/assessments/{{assessment_id}}'

    $pathAndQuery = $pathAndQuery -replace '/api/payments/internal/claim/\d+', '/api/payments/internal/claim/{{claim_id}}'
    $pathAndQuery = $pathAndQuery -replace '/api/payments/claim/\d+', '/api/payments/claim/{{claim_id}}'
    $pathAndQuery = $pathAndQuery -replace '/api/payments/\d+', '/api/payments/{{payment_id}}'

    $pathAndQuery = $pathAndQuery -replace '/api/notifications/\d+/read', '/api/notifications/{{notification_id}}/read'
    $pathAndQuery = $pathAndQuery -replace '/api/notifications/\d+', '/api/notifications/{{sent_notification_id}}'

    $pathAndQuery = $pathAndQuery -replace '(?<=claimId=)\d+', '{{claim_id}}'

    return "{{${baseVar}}}$pathAndQuery"
}

function Get-TokenVar {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url
    )

    if ($Name -like 'Health *' -or $Name -eq 'Gateway auth route' -or $Name -eq 'Gateway claim route' -or $Name -eq 'Gateway report route' -or $Name -eq 'Auth health') {
        return $null
    }

    if ($Name -in @('Auth register policyholder', 'Auth register adjuster', 'Auth register manager', 'Auth login manager')) {
        return $null
    }

    if ($Name -in @('Auth me', 'Auth refresh', 'Auth logout')) {
        return 'manager_token'
    }

    if ($Name -like 'Document *') {
        return 'policy_token'
    }

    if ($Name -like 'Assessment *' -and $Name -ne 'Assessment health') {
        return 'adjuster_token'
    }

    if ($Name -like 'Payment *') {
        return 'manager_token'
    }

    if ($Name -eq 'Notification send') {
        return 'manager_token'
    }

    if ($Name -like 'Notification *') {
        return 'policy_token'
    }

    if ($Name -in @('Report claim-event')) {
        return 'policy_token'
    }

    if ($Name -like 'Report *' -or $Name -eq 'Report status-change') {
        return 'manager_token'
    }

    if ($Name -in @('Claim submit primary', 'Claim submit delete-target', 'Claim my-claims', 'Claim history')) {
        return 'policy_token'
    }

    if ($Name -in @('Claim list page', 'Claim assigned list')) {
        return 'adjuster_token'
    }

    if ($Name -eq 'Claim health') {
        return $null
    }

    if ($Name -like 'Claim *' -or $Name -eq 'Claim read after payment') {
        return 'manager_token'
    }

    if ($Url -match '/api/' -and $Method -ne 'GET') {
        return 'manager_token'
    }

    return $null
}

function Get-BodyTemplate {
    param(
        [string]$Name,
        [string]$Method
    )

    if ($Method -notin @('POST', 'PUT', 'PATCH')) {
        return $null
    }

    switch ($Name) {
        'Auth register policyholder' {
            return @{ mode = 'raw'; raw = '{"username":"policy.postman","email":"policy.postman@example.com","password":"Password@123","firstName":"Smoke","lastName":"Policyholder","phoneNumber":"9999999999","role":"ROLE_POLICYHOLDER"}' }
        }
        'Auth register adjuster' {
            return @{ mode = 'raw'; raw = '{"username":"adjuster.postman","email":"adjuster.postman@example.com","password":"Password@123","firstName":"Smoke","lastName":"Adjuster","phoneNumber":"9999999998","role":"ROLE_ADJUSTER"}' }
        }
        'Auth register manager' {
            return @{ mode = 'raw'; raw = '{"username":"manager.postman","email":"manager.postman@example.com","password":"Password@123","firstName":"Smoke","lastName":"Manager","phoneNumber":"9999999997","role":"ROLE_MANAGER"}' }
        }
        'Auth login manager' {
            return @{ mode = 'raw'; raw = '{"usernameOrEmail":"manager.postman","password":"Password@123"}' }
        }
        'Auth refresh' { return @{ mode = 'raw'; raw = '{}' } }
        'Auth logout' { return @{ mode = 'raw'; raw = '{}' } }

        'Claim submit primary' {
            return @{ mode = 'raw'; raw = '{"policyNumber":"POL-POSTMAN","vehicleRegistration":"DL01PM1234","vehicleMake":"Honda","vehicleModel":"City","vehicleYear":2021,"incidentDate":"2026-03-01","incidentLocation":"Bangalore","incidentDescription":"Postman test claim","claimAmount":12500.00}' }
        }
        'Claim submit delete-target' {
            return @{ mode = 'raw'; raw = '{"policyNumber":"POL-POSTMAN-DEL","vehicleRegistration":"DL01PM5678","vehicleMake":"Maruti","vehicleModel":"Baleno","vehicleYear":2020,"incidentDate":"2026-03-01","incidentLocation":"Bangalore","incidentDescription":"Postman delete target","claimAmount":10000.00}' }
        }
        'Claim update' {
            return @{ mode = 'raw'; raw = '{"policyNumber":"POL-POSTMAN-UPD","vehicleRegistration":"DL01UPD1234","vehicleMake":"Toyota","vehicleModel":"Camry","vehicleYear":2022,"incidentDate":"2026-03-01","incidentLocation":"Bangalore","incidentDescription":"Updated after review","claimAmount":13000.00}' }
        }
        'Claim assign' {
            return @{ mode = 'raw'; raw = '{"adjusterId":{{adjuster_user_id}}}' }
        }
        'Claim put status ADJUSTED' {
            return @{ mode = 'raw'; raw = '{"status":"ADJUSTED","notes":"Adjusted after assessment","assignedAdjusterId":{{adjuster_user_id}}}' }
        }
        'Claim patch status APPROVED' {
            return @{ mode = 'raw'; raw = '{"status":"APPROVED","notes":"Approved after adjustment"}' }
        }

        'Document upload' {
            return @{
                mode = 'formdata'
                formdata = @(
                    @{ key = 'file'; type = 'file'; src = '{{upload_file}}' },
                    @{ key = 'claimId'; type = 'text'; value = '{{claim_id}}' },
                    @{ key = 'documentType'; type = 'text'; value = 'PHOTO_EVIDENCE' },
                    @{ key = 'description'; type = 'text'; value = 'Postman upload' }
                )
            }
        }

        'Assessment create' {
            return @{ mode = 'raw'; raw = '{"claimId":{{claim_id}},"assessedAmount":12000.00,"justification":"Primary inspection complete","notes":"No red flags"}' }
        }
        'Assessment decision' {
            return @{ mode = 'raw'; raw = '{"assessmentId":{{assessment_id}},"decision":"ADJUSTED","finalAmount":11800.50,"justification":"Depreciation applied"}' }
        }
        'Assessment adjustment' {
            return @{ mode = 'raw'; raw = '{"assessmentId":{{assessment_id}},"claimId":{{claim_id}},"adjustedAmount":11800.50,"adjustmentType":"OTHER","reason":"Manual adjustment for wear and tear","detailedNotes":"Detailed notes for audit"}' }
        }
        'Assessment request' { return @{ mode = 'raw'; raw = '{}' } }
        'Assessment notify-complete' {
            return @{ mode = 'raw'; raw = '{"claimId":{{claim_id}},"claimNumber":"{{claim_number}}","approvedAmount":11800.50}' }
        }

        'Payment process' {
            return @{ mode = 'raw'; raw = '{"claimId":{{claim_id}},"policyholderId":{{policy_user_id}},"amount":11800.50,"paymentMethod":"NEFT","beneficiaryName":"Smoke Policyholder","accountNumber":"123456789012","ifscCode":"HDFC0001234","bankName":"HDFC Bank","forceSimulateSuccess":true,"forceSimulateFailure":false}' }
        }

        'Notification test' { return @{ mode = 'raw'; raw = '{}' } }
        'Notification mark read' { return @{ mode = 'raw'; raw = '{}' } }
        'Notification mark all read' { return @{ mode = 'raw'; raw = '{}' } }
        'Notification send' {
            return @{ mode = 'raw'; raw = '{"userId":{{policy_user_id}},"claimId":{{claim_id}},"title":"Smoke Notification","message":"Notification endpoint smoke test","type":"SYSTEM_MESSAGE","actionUrl":"/claims/{{claim_id}}","senderId":{{manager_user_id}}}' }
        }

        'Report claim-event' {
            return @{ mode = 'raw'; raw = '{"claimId":{{claim_id}},"claimNumber":"{{claim_number}}","eventType":"POSTMAN_TEST","timestamp":"2026-03-04T13:00:00"}' }
        }
        'Report status-change' {
            return @{ mode = 'raw'; raw = '{"claimId":{{claim_id}},"claimNumber":"{{claim_number}}","oldStatus":"UNDER_REVIEW","newStatus":"APPROVED","timestamp":"2026-03-04T13:00:00"}' }
        }

        default {
            return @{ mode = 'raw'; raw = '{}' }
        }
    }
}

$folders = @{}
$folderOrder = @(
    'Config Server',
    'Eureka Server',
    'API Gateway',
    'Auth Service',
    'Claim Service',
    'Document Service',
    'Assessment Service',
    'Payment Service',
    'Notification Service',
    'Reporting Service',
    'Misc'
)

foreach ($res in $report.results) {
    $folderName = Get-FolderName -Url $res.url
    if (-not $folders.ContainsKey($folderName)) {
        $folders[$folderName] = New-Object System.Collections.ArrayList
    }

    $url = Convert-UrlToVariables -Url $res.url
    $tokenVar = Get-TokenVar -Name $res.name -Method $res.method -Url $res.url
    $bodyTemplate = Get-BodyTemplate -Name $res.name -Method $res.method

    $headers = New-Object System.Collections.ArrayList
    if ($tokenVar) {
        [void]$headers.Add([ordered]@{ key = 'Authorization'; value = "Bearer {{$tokenVar}}" })
    }
    if ($bodyTemplate -and $bodyTemplate.mode -eq 'raw') {
        [void]$headers.Add([ordered]@{ key = 'Content-Type'; value = 'application/json' })
    }

    $request = [ordered]@{
        method = $res.method
        header = $headers
        url = $url
    }

    if ($bodyTemplate) {
        if ($bodyTemplate.mode -eq 'raw') {
            $request.body = [ordered]@{
                mode = 'raw'
                raw = $bodyTemplate.raw
                options = [ordered]@{ raw = [ordered]@{ language = 'json' } }
            }
        } elseif ($bodyTemplate.mode -eq 'formdata') {
            $request.body = [ordered]@{
                mode = 'formdata'
                formdata = $bodyTemplate.formdata
            }
        }
    }

    $item = [ordered]@{
        name = "$($res.name) [$($res.method)]"
        request = $request
    }

    [void]$folders[$folderName].Add($item)
}

$latestUpload = Get-ChildItem -Path (Join-Path $root 'run-logs') -Filter 'smoke-upload-*.*' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
$uploadDefault = if ($latestUpload) { $latestUpload.FullName -replace '\\', '/' } else { 'c:/Users/shashank.nath/Desktop/Claimswift/run-logs/smoke-upload.pdf' }

$variables = @(
    @{ key = 'config_base'; value = 'http://localhost:8888' },
    @{ key = 'eureka_base'; value = 'http://localhost:8761' },
    @{ key = 'gateway_base'; value = 'http://localhost:8080' },
    @{ key = 'auth_base'; value = 'http://localhost:8081' },
    @{ key = 'claim_base'; value = 'http://localhost:8082' },
    @{ key = 'document_base'; value = 'http://localhost:8083' },
    @{ key = 'assessment_base'; value = 'http://localhost:8084' },
    @{ key = 'payment_base'; value = 'http://localhost:8085' },
    @{ key = 'notification_base'; value = 'http://localhost:8086' },
    @{ key = 'reporting_base'; value = 'http://localhost:8087' },
    @{ key = 'policy_token'; value = '' },
    @{ key = 'adjuster_token'; value = '' },
    @{ key = 'manager_token'; value = '' },
    @{ key = 'claim_id'; value = "$($ctx.claimId)" },
    @{ key = 'claim_number'; value = "$($ctx.claimNumber)" },
    @{ key = 'assessment_id'; value = "$($ctx.assessmentId)" },
    @{ key = 'payment_id'; value = "$($ctx.paymentId)" },
    @{ key = 'document_id'; value = "$($ctx.documentId)" },
    @{ key = 'notification_id'; value = "$($ctx.notificationId)" },
    @{ key = 'sent_notification_id'; value = "$($ctx.sentNotificationId)" },
    @{ key = 'policy_user_id'; value = "$($ctx.policyUserId)" },
    @{ key = 'adjuster_user_id'; value = "$($ctx.adjusterUserId)" },
    @{ key = 'manager_user_id'; value = "$($ctx.managerUserId)" },
    @{ key = 'upload_file'; value = $uploadDefault }
)

$collectionItems = New-Object System.Collections.ArrayList
foreach ($folderName in $folderOrder) {
    if ($folders.ContainsKey($folderName) -and $folders[$folderName].Count -gt 0) {
        [void]$collectionItems.Add([ordered]@{
            name = $folderName
            item = $folders[$folderName]
        })
    }
}

$collection = [ordered]@{
    info = [ordered]@{
        _postman_id = [guid]::NewGuid().Guid
        name = 'ClaimSwift Local APIs (Smoke Covered)'
        schema = 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'
        description = "Generated from $($latestReport.Name). Contains $($report.totals.total) requests that passed smoke tests."
    }
    item = $collectionItems
    variable = $variables
}

$outPath = Join-Path $root ("run-logs\ClaimSwift-Local-Smoke-{0}.postman_collection.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$collection | ConvertTo-Json -Depth 30 | Set-Content -Path $outPath -Encoding UTF8

Write-Output "COLLECTION=$outPath"
Write-Output "REQUESTS=$($report.totals.total)"
