param(
  [string]$OutputDirectory = (Join-Path $PSScriptRoot 'offline'),
  [string]$AppPackageDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'offline-linux-ubuntu22-x86_64'),
  [string]$UbuntuHyperVZip = '',
  [string]$UbuntuVhdxPath = '',
  [Parameter(Mandatory = $true)]
  [string]$WindowsIsoPath,
  [string]$ArchivePath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'DISP-EMS-Windows-Server-2016-x64-offline.zip'),
  [switch]$RefreshImages
)

$ErrorActionPreference = 'Stop'
$templateRoot = $PSScriptRoot
$ubuntuUrl = 'https://partner-images.canonical.com/hyper-v/desktop/jammy/release/current/ubuntu-jammy-hyperv-amd64-ubuntu-desktop-hyperv.vhdx.zip'
$appRoot = Join-Path $AppPackageDirectory 'app'

function Require-Path([string]$Path, [string]$Message) {
  if (-not (Test-Path -LiteralPath $Path)) { throw $Message }
}
function Copy-IsoSxs([string]$IsoPath, [string]$Destination) {
  Require-Path $IsoPath "Missing Windows Server ISO: $IsoPath"
  $image = $null
  try {
    Write-Host "Mounting Windows Server ISO: $IsoPath"
    $image = Mount-DiskImage -ImagePath (Resolve-Path $IsoPath).Path -Access ReadOnly -PassThru
    Start-Sleep -Seconds 2
    $volume = Get-Volume |
      Where-Object { $_.DriveLetter -and $_.DriveType -eq 'CD-ROM' -and $_.FileSystem -eq 'UDF' } |
      Sort-Object Size -Descending |
      Select-Object -First 1
    if ($null -eq $volume) { throw 'Could not determine the mounted Windows ISO drive letter.' }
    $sxsSource = Join-Path ("$($volume.DriveLetter):") 'sources\sxs'
    Require-Path $sxsSource 'The Windows ISO does not contain sources\sxs.'
    $files = Get-ChildItem -LiteralPath $sxsSource -File -Recurse
    $bytes = ($files | Measure-Object -Property Length -Sum).Sum
    if ($files.Count -lt 1 -or $bytes -lt 50MB) {
      throw "The ISO sources\sxs appears incomplete: $($files.Count) files, $bytes bytes."
    }
    New-Item -ItemType Directory -Path (Split-Path $Destination -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $sxsSource -Destination $Destination -Recurse -Force
    $featureWim = Join-Path ("$($volume.DriveLetter):") 'sources\install.wim'
    Require-Path $featureWim 'The Windows ISO does not contain sources\install.wim.'
    $windowsSourceRoot = Split-Path (Split-Path $Destination -Parent) -Parent
    Copy-Item -LiteralPath $featureWim -Destination (Join-Path $windowsSourceRoot 'install.wim') -Force
    Write-Host "Extracted Windows feature source: $($files.Count) files, $bytes bytes"
    Write-Host "Copied Windows feature WIM: $((Get-Item -LiteralPath $featureWim).Length) bytes"
  } finally {
    if ($null -ne $image) {
      Dismount-DiskImage -ImagePath (Resolve-Path $IsoPath).Path -ErrorAction SilentlyContinue
    }
  }
}

Require-Path $appRoot "Missing application payload: $appRoot. Build deploy/offline-linux-ubuntu22-x86_64 first."
Require-Path (Join-Path $appRoot 'disp-ems-images.tar') 'Missing application image archive.'
Require-Path (Join-Path $appRoot 'docker-compose.yml') 'Missing offline docker-compose.yml.'
Require-Path (Join-Path $AppPackageDirectory 'docker-engine.tgz') 'Missing offline Docker Engine archive.'
Require-Path (Join-Path $AppPackageDirectory 'docker-compose') 'Missing offline Docker Compose plugin.'
Require-Path (Join-Path $AppPackageDirectory 'runtime-debs') 'Missing offline Ubuntu runtime packages.'

if ($RefreshImages) {
  $exportScript = Join-Path (Split-Path $PSScriptRoot -Parent) 'export-offline-images.ps1'
  Require-Path $exportScript 'Missing deploy/export-offline-images.ps1.'
  & $exportScript -OutputDirectory $appRoot
  if ($LASTEXITCODE -ne 0) { throw 'Refreshing application images failed.' }
}

$buildWorkspace = Join-Path (Split-Path $OutputDirectory -Parent) ('.disp-ems-build-' + [guid]::NewGuid().ToString('N'))
$stagingDirectory = Join-Path $buildWorkspace 'package'
$payloadDirectory = Join-Path $stagingDirectory 'payload'
$imageDirectory = Join-Path $stagingDirectory 'images'
$sourceDirectory = Join-Path $stagingDirectory 'windows-source\sources\sxs'
$tempDirectory = Join-Path $buildWorkspace 'download-and-extract'

New-Item -ItemType Directory -Path $payloadDirectory, $imageDirectory, $tempDirectory -Force | Out-Null

try {
  if (-not [string]::IsNullOrWhiteSpace($UbuntuVhdxPath)) {
    Require-Path $UbuntuVhdxPath "Missing Ubuntu Hyper-V VHDX: $UbuntuVhdxPath"
    Copy-Item -LiteralPath (Resolve-Path $UbuntuVhdxPath).Path -Destination (Join-Path $imageDirectory 'ubuntu-jammy-hyperv.vhdx') -Force
  } else {
    if ([string]::IsNullOrWhiteSpace($UbuntuHyperVZip)) {
      $UbuntuHyperVZip = Join-Path $tempDirectory 'ubuntu-jammy-hyperv-amd64.vhdx.zip'
      Write-Host 'Downloading the official Ubuntu Hyper-V image...'
      Invoke-WebRequest -Uri $ubuntuUrl -OutFile $UbuntuHyperVZip -UseBasicParsing
    } else {
      Require-Path $UbuntuHyperVZip "Missing Ubuntu Hyper-V image archive: $UbuntuHyperVZip"
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $unpackDirectory = Join-Path $tempDirectory 'ubuntu'
    [System.IO.Compression.ZipFile]::ExtractToDirectory((Resolve-Path $UbuntuHyperVZip).Path, $unpackDirectory)
    $ubuntuVhdx = Get-ChildItem -LiteralPath $unpackDirectory -Filter '*.vhdx' -File -Recurse | Select-Object -First 1
    if ($null -eq $ubuntuVhdx) { throw 'The Ubuntu archive does not contain a VHDX file.' }
    Copy-Item -LiteralPath $ubuntuVhdx.FullName -Destination (Join-Path $imageDirectory 'ubuntu-jammy-hyperv.vhdx') -Force
  }
  Copy-Item -LiteralPath (Join-Path $AppPackageDirectory 'docker-engine.tgz') -Destination $payloadDirectory -Force
  Copy-Item -LiteralPath (Join-Path $AppPackageDirectory 'docker-compose') -Destination $payloadDirectory -Force
  Copy-Item -LiteralPath (Join-Path $AppPackageDirectory 'runtime-debs') -Destination $payloadDirectory -Recurse -Force
  Copy-Item -LiteralPath $appRoot -Destination $payloadDirectory -Recurse -Force
  (Get-FileHash (Join-Path $appRoot 'disp-ems-images.tar') -Algorithm SHA256).Hash.ToLowerInvariant() |
    Set-Content -LiteralPath (Join-Path $payloadDirectory 'app\release.txt') -Encoding ASCII

  Copy-IsoSxs $WindowsIsoPath $sourceDirectory

  Copy-Item -LiteralPath (Join-Path $templateRoot 'install.ps1') -Destination $stagingDirectory -Force
  Copy-Item -LiteralPath (Join-Path $templateRoot 'manage.ps1') -Destination $stagingDirectory -Force
  Copy-Item -LiteralPath (Join-Path $templateRoot 'install.bat') -Destination $stagingDirectory -Force
  Copy-Item -LiteralPath (Join-Path $templateRoot 'README.md') -Destination $stagingDirectory -Force
  Copy-Item -LiteralPath (Join-Path $templateRoot 'bootstrap.sh.template') -Destination $stagingDirectory -Force
  Copy-Item -LiteralPath (Join-Path $templateRoot 'user-data.template') -Destination $stagingDirectory -Force
  Copy-Item -LiteralPath (Join-Path $templateRoot 'meta-data') -Destination $stagingDirectory -Force

  $previousOutput = $OutputDirectory + '.previous-' + [guid]::NewGuid().ToString('N')
  if (Test-Path -LiteralPath $OutputDirectory) {
    Move-Item -LiteralPath $OutputDirectory -Destination $previousOutput
  }
  Move-Item -LiteralPath $stagingDirectory -Destination $OutputDirectory

  $sumFile = Join-Path $OutputDirectory 'SHA256SUMS'
  Get-ChildItem -LiteralPath $OutputDirectory -File -Recurse |
    Get-FileHash -Algorithm SHA256 |
    ForEach-Object {
      $relative = $_.Path.Substring($OutputDirectory.Length + 1).Replace('\', '/')
      "{0}  {1}" -f $_.Hash.ToLowerInvariant(), $relative
    } |
    Set-Content -LiteralPath $sumFile -Encoding ASCII

  $archiveTemporary = $ArchivePath + '.tmp.zip'
  Remove-Item -LiteralPath $archiveTemporary -Force -ErrorAction SilentlyContinue
  $tar = Get-Command tar.exe -ErrorAction SilentlyContinue
  if ($null -eq $tar) { throw 'Windows tar.exe is required to create the ZIP64 transfer archive.' }
  & $tar.Source -a -c -f $archiveTemporary -C $OutputDirectory .
  if ($LASTEXITCODE -ne 0) { throw "tar.exe failed to create the transfer archive (exit code $LASTEXITCODE)." }
  Move-Item -LiteralPath $archiveTemporary -Destination $ArchivePath -Force
  if (Test-Path -LiteralPath $previousOutput) {
    Remove-Item -LiteralPath $previousOutput -Recurse -Force
  }
  Write-Host "Windows Server 2016 offline package created at $OutputDirectory"
  Write-Host "Transfer archive created at $ArchivePath"
} finally {
  if (Test-Path -LiteralPath $buildWorkspace) {
    Remove-Item -LiteralPath $buildWorkspace -Recurse -Force -ErrorAction SilentlyContinue
  }
}