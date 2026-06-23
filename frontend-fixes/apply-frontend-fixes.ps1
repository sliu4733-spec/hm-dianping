$ErrorActionPreference = "Stop"

$sourceRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$targetRoot = "F:\nginx-1.18.0\html\hmdp"

if (!(Test-Path $targetRoot)) {
    throw "Frontend directory not found: $targetRoot"
}

Copy-Item -LiteralPath (Join-Path $sourceRoot "frontend-fixes\index.html") -Destination (Join-Path $targetRoot "index.html") -Force
Copy-Item -LiteralPath (Join-Path $sourceRoot "frontend-fixes\shop-list.html") -Destination (Join-Path $targetRoot "shop-list.html") -Force
Copy-Item -LiteralPath (Join-Path $sourceRoot "frontend-fixes\customer-service.html") -Destination (Join-Path $targetRoot "customer-service.html") -Force
Copy-Item -LiteralPath (Join-Path $sourceRoot "frontend-fixes\map.html") -Destination (Join-Path $targetRoot "map.html") -Force
Copy-Item -LiteralPath (Join-Path $sourceRoot "frontend-fixes\js\location.js") -Destination (Join-Path $targetRoot "js\location.js") -Force
Copy-Item -LiteralPath (Join-Path $sourceRoot "frontend-fixes\js\footer.js") -Destination (Join-Path $targetRoot "js\footer.js") -Force

Write-Host "Frontend files copied to $targetRoot"
Write-Host "Now refresh browser with Ctrl+F5."
