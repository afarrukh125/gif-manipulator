#!/usr/bin/env pwsh
$ErrorActionPreference = "Stop"

Set-Location -LiteralPath $PSScriptRoot

$jar = "target/giftools.jar"
if ($env:PORT) { $port = $env:PORT } else { $port = 8080 }

if (-not (Test-Path $jar)) {
    Write-Host "Building $jar ..."
    mvn -q package
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "GIF editor: http://localhost:$port"
java -jar $jar serve --port $port @args
exit $LASTEXITCODE
