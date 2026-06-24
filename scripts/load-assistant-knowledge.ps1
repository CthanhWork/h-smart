param(
    [string]$ElasticsearchUrl = $env:ELASTICSEARCH_URL,
    [string]$IndexName = "hsmart-policy-index"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ElasticsearchUrl)) {
    $ElasticsearchUrl = "http://localhost:9200"
}

$ElasticsearchUrl = $ElasticsearchUrl.TrimEnd("/")
$KnowledgeFile = Join-Path $PSScriptRoot "..\interaction-service\src\main\resources\knowledge\hsmart-knowledge.ndjson"
$KnowledgeFile = [System.IO.Path]::GetFullPath($KnowledgeFile)

if (-not (Test-Path -LiteralPath $KnowledgeFile)) {
    throw "Knowledge file not found: $KnowledgeFile"
}

$indexExists = $true
try {
    Invoke-WebRequest -Method Head -Uri "$ElasticsearchUrl/$IndexName" -UseBasicParsing | Out-Null
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 404) {
        $indexExists = $false
    } else {
        throw
    }
}

if (-not $indexExists) {
    $mapping = @{
        mappings = @{
            dynamic = "strict"
            properties = @{
                title = @{ type = "text"; fields = @{ keyword = @{ type = "keyword" } } }
                content = @{ type = "text" }
                category = @{ type = "keyword" }
                version = @{ type = "integer" }
                updatedAt = @{ type = "date"; format = "yyyy-MM-dd" }
                source = @{ type = "keyword" }
            }
        }
    } | ConvertTo-Json -Depth 8

    Invoke-RestMethod -Method Put -Uri "$ElasticsearchUrl/$IndexName" `
        -ContentType "application/json; charset=utf-8" -Body $mapping | Out-Null
}

$bulkBody = [System.IO.File]::ReadAllText($KnowledgeFile, [System.Text.Encoding]::UTF8)
$bulkBody = $bulkBody.Replace('"hsmart-policy-index"', '"' + $IndexName + '"')
if (-not $bulkBody.EndsWith("`n")) {
    $bulkBody += "`n"
}

$response = Invoke-RestMethod -Method Post -Uri "$ElasticsearchUrl/_bulk?refresh=wait_for" `
    -ContentType "application/x-ndjson; charset=utf-8" -Body $bulkBody

if ($response.errors) {
    $failures = $response.items |
        Where-Object { $_.index.status -ge 300 } |
        ForEach-Object { "$($_.index._id): $($_.index.error.reason)" }
    throw "Elasticsearch bulk load failed: $($failures -join '; ')"
}

$count = (Invoke-RestMethod -Method Get -Uri "$ElasticsearchUrl/$IndexName/_count").count
Write-Host "Loaded H-Smart assistant knowledge into '$IndexName'. Current document count: $count"
