#!/usr/bin/env pwsh
<#
.SYNOPSIS
Runs the GIF editor at login, in the background, with no window.

.DESCRIPTION
Puts a shortcut in the Startup folder that launches the jar with javaw, so the editor is already up at
http://localhost:8091 whenever you want it. There is no console, so the log goes to server.log beside this script.

.EXAMPLE
./install-startup.ps1
./install-startup.ps1 -Port 9000
./install-startup.ps1 -Uninstall
#>
[CmdletBinding()]
param(
    [int]$Port = 8091,
    [string]$BindAddress = "127.0.0.1",
    [switch]$Uninstall,
    [switch]$Start
)

$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

$name = "GIF Editor Server"
$startup = [Environment]::GetFolderPath("Startup")
$shortcut = Join-Path $startup "$name.lnk"

if ($Uninstall) {
    if (Test-Path -LiteralPath $shortcut) {
        Remove-Item -LiteralPath $shortcut
        Write-Host "Removed $shortcut"
        Write-Host "An editor already running stays up until you close it or log out."
    } else {
        Write-Host "Nothing to remove, $shortcut does not exist."
    }
    exit 0
}

$jar = Join-Path $PSScriptRoot "target\giftools.jar"
if (-not (Test-Path -LiteralPath $jar)) {
    Write-Host "Building $jar ..."
    mvn -q package
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# javaw rather than java: java.exe would put a console window on screen at every login.
$javaw = (Get-Command javaw.exe -ErrorAction SilentlyContinue).Source
if (-not $javaw -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\javaw.exe"
    if (Test-Path -LiteralPath $candidate) { $javaw = $candidate }
}
if (-not $javaw) {
    Write-Error "Cannot find javaw.exe. Put java on PATH or set JAVA_HOME, then run this again."
}

$log = Join-Path $PSScriptRoot "server.log"
# Sitting there from login, the JVM would otherwise size its heap off total RAM and idle near half a gigabyte.
$arguments = "-Xmx768m -jar `"$jar`" serve --port $Port --host $BindAddress --no-open --log-file `"$log`""

$shell = New-Object -ComObject WScript.Shell
$link = $shell.CreateShortcut($shortcut)
$link.TargetPath = $javaw
$link.Arguments = $arguments
$link.WorkingDirectory = $PSScriptRoot
$link.WindowStyle = 7
$link.Description = "GIF editor - local server on http://localhost:$Port/ (no window, logs to file)"
$link.Save()

Write-Host "Installed $shortcut"
Write-Host "  $javaw $arguments"
Write-Host "The editor starts at your next login on http://localhost:$Port/ and logs to $log"
Write-Host "Run this script with -Uninstall to stop it starting at login."

if ($Start) {
    Start-Process -FilePath $javaw -ArgumentList $arguments -WorkingDirectory $PSScriptRoot -WindowStyle Hidden
    Write-Host "Started it now as well."
}
