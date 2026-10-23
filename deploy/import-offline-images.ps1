param(
  [string]$PackageDirectory = $PSScriptRoot
)

$ErrorActionPreference = 'Stop'
docker load --input (Join-Path $PackageDirectory 'disp-ems-images.tar')
if (-not (Test-Path (Join-Path $PackageDirectory '.env'))) {
  Copy-Item (Join-Path $PackageDirectory '.env.example') (Join-Path $PackageDirectory '.env')
  throw "Edit the generated .env with production passwords, then run this script again."
}
Push-Location $PackageDirectory
try {
  docker compose up -d --no-build
  docker compose ps
} finally {
  Pop-Location
}
