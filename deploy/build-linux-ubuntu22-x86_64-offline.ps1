param(
  [string]$OutputDirectory = (Join-Path $PSScriptRoot 'offline-linux-ubuntu22-x86_64'),
  [string]$DockerVersion = '29.9.0',
  [string]$ComposeVersion = 'v5.6.0'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$templateDirectory = Join-Path $PSScriptRoot 'linux-ubuntu22-x86_64'
$applicationDirectory = Join-Path $OutputDirectory 'app'
$runtimeDebDirectory = Join-Path $OutputDirectory 'runtime-debs'

if (-not (Test-Path (Join-Path $PSScriptRoot '.env'))) {
  throw 'Missing deploy/.env. Configure and validate the local deployment before building an offline package.'
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
New-Item -ItemType Directory -Path $runtimeDebDirectory -Force | Out-Null
if (Test-Path $applicationDirectory) {
  Get-ChildItem -LiteralPath $applicationDirectory -File -Filter '.tmp-*' -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
}

# Export only the exact application images tested on this build machine.
& (Join-Path $PSScriptRoot 'export-offline-images.ps1') -OutputDirectory $applicationDirectory

$engineFile = Join-Path $OutputDirectory 'docker-engine.tgz'
$composeFile = Join-Path $OutputDirectory 'docker-compose'
Invoke-WebRequest -Uri "https://download.docker.com/linux/static/stable/x86_64/docker-$DockerVersion.tgz" -OutFile $engineFile
Invoke-WebRequest -Uri "https://github.com/docker/compose/releases/download/$ComposeVersion/docker-compose-linux-x86_64" -OutFile $composeFile

# A minimal Ubuntu base image does not contain iptables or iproute2. Download their
# dependency closure for Jammy into the package so the installer never invokes apt online.
$volume = (Resolve-Path $runtimeDebDirectory).Path.Replace('\', '/')
& docker run --rm -v "${volume}:/out" ubuntu:22.04 bash -lc 'apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --download-only iptables iproute2 ca-certificates && cp /var/cache/apt/archives/*.deb /out/'
if ($LASTEXITCODE -ne 0) { throw 'Could not download Ubuntu runtime prerequisite packages.' }

Copy-Item (Join-Path $templateDirectory 'install.sh') (Join-Path $OutputDirectory 'install.sh') -Force
Copy-Item (Join-Path $templateDirectory 'README.md') (Join-Path $OutputDirectory 'README.md') -Force
Get-ChildItem -File -Recurse $OutputDirectory | Where-Object { $_.Name -ne 'SHA256SUMS' } |
  Get-FileHash -Algorithm SHA256 |
  ForEach-Object { "{0}  {1}" -f $_.Hash.ToLowerInvariant(), $_.Path.Substring($OutputDirectory.Length + 1).Replace('\', '/') } |
  Set-Content -Path (Join-Path $OutputDirectory 'SHA256SUMS') -Encoding ascii

$archive = Join-Path $PSScriptRoot 'disp-ems-ubuntu22-x86_64-offline.tar'
Push-Location $PSScriptRoot
try {
  & tar.exe -cf $archive (Split-Path $OutputDirectory -Leaf)
  if ($LASTEXITCODE -ne 0) { throw 'Could not create the Linux transfer archive.' }
} finally {
  Pop-Location
}

Write-Host "Ubuntu 22.04 x86_64 offline installer created at $OutputDirectory"
Write-Host "Linux transfer archive created at $archive"
