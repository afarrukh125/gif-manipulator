#!/usr/bin/env pwsh
$ErrorActionPreference = "Stop"

Set-Location -LiteralPath $PSScriptRoot

$jar = "target/giftools.jar"
if ($env:PORT) { $port = $env:PORT } else { $port = 8091 }

$serveArgs = @($args)
if ($serveArgs.Count -gt 0 -and $serveArgs[0] -match '^\d+$') {
    $port = $serveArgs[0]
    $serveArgs = @($serveArgs | Select-Object -Skip 1)
}

$url = "http://localhost:$port"

function Test-Listening($p) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $client.Connect("127.0.0.1", $p); return $true }
    catch { return $false }
    finally { $client.Dispose() }
}

if (Test-Listening $port) {
    $isEditor = $false
    try { $isEditor = (Invoke-WebRequest -Uri "$url/" -UseBasicParsing -TimeoutSec 5).Content -match "GIF Tools" } catch {}
    if (-not $isEditor) {
        [Console]::Error.WriteLine("Port $port is taken by something that is not the GIF editor. Try another: ./run.ps1 9000")
        exit 1
    }
    Write-Host "GIF editor already running at $url"
    if ($serveArgs -notcontains "--no-open") { Start-Process $url }
    exit 0
}

if (-not (Test-Path $jar)) {
    Write-Host "Building $jar ..."
    mvn -q package
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "GIF editor: $url"
java -jar $jar serve --port $port @serveArgs
exit $LASTEXITCODE
