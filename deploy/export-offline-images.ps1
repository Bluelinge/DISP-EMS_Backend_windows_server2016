param(
  [string]$OutputDirectory = (Join-Path $PSScriptRoot 'offline')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$environmentFile = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path $environmentFile)) {
  throw "Missing $environmentFile. Copy .env.example to .env and set local deployment values first."
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
Push-Location $projectRoot
try {
  # Export the images that were just validated locally. Do not repull mutable tags here:
  # the archive must contain the exact tested image set.
  docker compose --env-file $environmentFile build
  $archive = Join-Path $OutputDirectory 'disp-ems-images.tar'
  docker image save --output $archive mysql:5.7.44 redis:7.2-alpine soldevelo/minio:2025.7.23 disp-ems-server:local disp-ems-web:local
  Copy-Item (Join-Path $PSScriptRoot 'docker-compose.offline.yml') (Join-Path $OutputDirectory 'docker-compose.yml') -Force
  Copy-Item (Join-Path $PSScriptRoot '.env.example') (Join-Path $OutputDirectory '.env.example') -Force
  Copy-Item (Join-Path $PSScriptRoot 'import-offline-images.ps1') (Join-Path $OutputDirectory 'import-offline-images.ps1') -Force
  Copy-Item (Join-Path $PSScriptRoot 'README.md') (Join-Path $OutputDirectory 'README.md') -Force
  Write-Host "Offline images exported to $OutputDirectory"
} finally {
  Pop-Location
}
