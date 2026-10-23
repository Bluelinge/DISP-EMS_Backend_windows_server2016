param(
  [string]$HostAddress = '',
  [string]$VMName = 'DISP-EMS',
  [string]$SwitchName = 'DISP-EMS-Internal',
  [string]$NatName = 'DISP-EMS-NAT',
  [string]$InternalPrefix = '172.29.0.0/24',
  [string]$VMAddress = '172.29.0.2',
  [string]$GatewayAddress = '172.29.0.1',
  [string]$WindowsSourcePath = '',
  [int]$MemoryGB = 4,
  [int]$ProcessorCount = 2,
  [switch]$PostReboot,
  [switch]$SkipCheckpoint
)

$ErrorActionPreference = 'Stop'
$scriptRoot = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$vmRoot = Join-Path $env:ProgramData 'DISP-EMS'
$vmDirectory = Join-Path $vmRoot 'VM'
$seedPath = Join-Path $vmDirectory 'cidata.vhdx'
$baseDiskPath = Join-Path $scriptRoot 'images\ubuntu-jammy-hyperv.vhdx'
$payloadPath = Join-Path $scriptRoot 'payload'
$bootstrapTemplate = Join-Path $scriptRoot 'bootstrap.sh.template'
$userDataTemplate = Join-Path $scriptRoot 'user-data.template'
$metaDataTemplate = Join-Path $scriptRoot 'meta-data'

function Fail([string]$Message) { throw $Message }
function Note([string]$Message) { Write-Host "==> $Message" }
function Require-Path([string]$Path, [string]$Message) {
  if (-not (Test-Path -LiteralPath $Path)) { Fail $Message }
}
function Write-Utf8NoBom([string]$Path, [string]$Content) {
  $encoding = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}
function Assert-Administrator {
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
  $principal = New-Object Security.Principal.WindowsPrincipal($identity)
  if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Fail 'Run PowerShell as Administrator.'
  }
}
function Assert-HostRequirements {
  $operatingSystem = Get-CimInstance Win32_OperatingSystem
  if ($operatingSystem.Caption -notmatch 'Windows Server 2016' -or $operatingSystem.OSArchitecture -ne '64-bit') {
    Fail "This package requires Windows Server 2016 x64; detected '$($operatingSystem.Caption)' ($($operatingSystem.OSArchitecture))."
  }
  $memoryGB = [math]::Floor((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
  if ($memoryGB -lt 8) { Fail "At least 8 GB RAM is required; detected $memoryGB GB." }
  $systemDrive = Get-Volume -DriveLetter $env:SystemDrive.TrimEnd(':')
  if ($systemDrive.SizeRemaining -lt 30GB) { Fail 'At least 30 GB free on the system volume is required.' }
  $processors = Get-CimInstance Win32_Processor
  if (-not ($processors | Where-Object { $_.VirtualizationFirmwareEnabled })) {
    Fail 'Hardware virtualization is disabled or unavailable. Enable Intel VT-x/AMD-V in BIOS/UEFI.'
  }
}
function Get-HostIPv4 {
  $candidate = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.IPAddress -ne $GatewayAddress } |
    Select-Object -First 1 -ExpandProperty IPAddress
  if ($candidate) { return $candidate }
  return (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' } | Select-Object -First 1 -ExpandProperty IPAddress)
}
function Register-ResumeTask {
  $taskName = 'DISP-EMS Windows offline deployment'
  $arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $scriptRoot 'install.ps1')`" -HostAddress `"$HostAddress`" -VMName `"$VMName`" -SwitchName `"$SwitchName`" -NatName `"$NatName`" -InternalPrefix `"$InternalPrefix`" -VMAddress `"$VMAddress`" -GatewayAddress `"$GatewayAddress`" -WindowsSourcePath `"$WindowsSourcePath`" -MemoryGB $MemoryGB -ProcessorCount $ProcessorCount -PostReboot"
  $action = New-ScheduledTaskAction -Execute 'PowerShell.exe' -Argument $arguments
  $trigger = New-ScheduledTaskTrigger -AtStartup
  $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -RunLevel Highest
  Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
  Note 'Hyper-V requires a restart. The deployment will continue automatically after reboot.'
  Restart-Computer -Force
  exit 0
}
function Ensure-HyperV {
  Import-Module ServerManager -ErrorAction Stop
  $feature = Get-WindowsFeature -Name Hyper-V
  if ($feature.Installed) { Import-Module Hyper-V -ErrorAction Stop; return }
  $resolvedSource = (Resolve-Path $WindowsSourcePath).Path
  $featureRoot = Split-Path (Split-Path $resolvedSource -Parent) -Parent
  $featureWim = Join-Path $featureRoot 'install.wim'
  $useFeatureWim = Test-Path -LiteralPath $featureWim
  if ($useFeatureWim) {
    $os = Get-CimInstance Win32_OperatingSystem
    $edition = if ($os.Caption -match 'Datacenter|数据中心') { 'Datacenter|数据中心' } else { 'Standard|标准' }
    $installationType = (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion').InstallationType
    $experience = if ($installationType -match 'Core') { 'Server Core|服务器核心' } else { 'Desktop Experience|桌面体验' }
    $images = @(Get-WindowsImage -ImagePath $featureWim)
    $image = $images |
      Where-Object { $_.ImageName -match $edition -and $_.ImageName -match $experience } |
      Select-Object -First 1
    if ($null -eq $image) {
      $image = $images | Where-Object { $_.ImageName -match $edition } | Select-Object -First 1
    }
    if ($null -eq $image) { Fail "No $edition installation image exists in $featureWim. Available images: $($images.ImageName -join '; ')" }
  }
  if ($useFeatureWim) {
    Require-Path $featureWim "Hyper-V offline feature WIM was not found: $featureWim"
    $resolvedSource = "WIM:$featureWim`:$($image.ImageIndex)"
  } else {
    Require-Path $resolvedSource "Hyper-V offline feature source was not found: $resolvedSource"
  }
  Note "Installing Hyper-V from offline source: $resolvedSource"
  $result = Install-WindowsFeature -Name Hyper-V -IncludeManagementTools -Source $resolvedSource
  if ($result.RestartNeeded -eq 'Yes') { Register-ResumeTask }
  Import-Module Hyper-V -ErrorAction Stop
}
function Ensure-Network {
  $switch = Get-VMSwitch -Name $SwitchName -ErrorAction SilentlyContinue
  if ($null -eq $switch) {
    Note "Creating internal Hyper-V switch: $SwitchName"
    New-VMSwitch -Name $SwitchName -SwitchType Internal | Out-Null
  }
  $adapterName = "vEthernet ($SwitchName)"
  $adapter = Get-NetAdapter -Name $adapterName -ErrorAction SilentlyContinue
  if ($null -eq $adapter) { Fail "The Hyper-V adapter '$adapterName' was not created." }
  $gatewayExists = Get-NetIPAddress -InterfaceAlias $adapterName -IPAddress $GatewayAddress -ErrorAction SilentlyContinue
  if ($null -eq $gatewayExists) {
    New-NetIPAddress -InterfaceAlias $adapterName -IPAddress $GatewayAddress -PrefixLength 24 | Out-Null
  }
  if ($null -eq (Get-NetNat -Name $NatName -ErrorAction SilentlyContinue)) {
    Note "Creating NAT network: $NatName"
    New-NetNat -Name $NatName -InternalIPInterfaceAddressPrefix $InternalPrefix | Out-Null
  }
}
function New-CidataDisk([string]$TargetPath) {
  New-Item -ItemType Directory -Path $vmDirectory -Force | Out-Null
  if (Test-Path -LiteralPath $TargetPath) { Remove-Item -LiteralPath $TargetPath -Force }
  New-VHD -Path $TargetPath -SizeBytes 2GB -Dynamic | Out-Null
  $mounted = $false
  try {
    $disk = Mount-VHD -Path $TargetPath -Passthru
    $mounted = $true
    $disk | Get-Disk | Set-Disk -IsOffline $false -IsReadOnly $false
    $partition = $disk | Get-Disk | Initialize-Disk -PartitionStyle GPT -PassThru |
      New-Partition -UseMaximumSize -AssignDriveLetter |
      Format-Volume -FileSystem FAT32 -NewFileSystemLabel 'CIDATA' -Confirm:$false
    $drive = "$($partition.DriveLetter):"
    New-Item -ItemType Directory -Path (Join-Path $drive 'payload') -Force | Out-Null
    Get-ChildItem -LiteralPath $payloadPath -Force | ForEach-Object {
      Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $drive 'payload') -Recurse -Force
    }
    $bootstrap = (Get-Content -Raw -LiteralPath $bootstrapTemplate).Replace('__HOST_ADDRESS__', $HostAddress).Replace('__VM_ADDRESS__', $VMAddress)
    $userData = (Get-Content -Raw -LiteralPath $userDataTemplate).Replace('__VM_ADDRESS__', $VMAddress).Replace('__GATEWAY_ADDRESS__', $GatewayAddress)
    Write-Utf8NoBom (Join-Path $drive 'bootstrap.sh') $bootstrap
    Write-Utf8NoBom (Join-Path $drive 'user-data') $userData
    Copy-Item -LiteralPath $metaDataTemplate -Destination (Join-Path $drive 'meta-data') -Force
  } finally {
    if ($mounted) { Dismount-VHD -Path $TargetPath }
  }
}
function Get-SeedDrive([object]$VM) {
  $drives = Get-VMHardDiskDrive -VMName $VM.Name
  return $drives | Where-Object { $_.Path -and ([System.IO.Path]::GetFileName($_.Path) -ieq 'cidata.vhdx') } | Select-Object -First 1
}
function Wait-VMOff([string]$Name) {
  for ($attempt = 1; $attempt -le 60; $attempt++) {
    if ((Get-VM -Name $Name).State -eq 'Off') { return }
    Start-Sleep -Seconds 1
  }
  Fail "VM '$Name' did not stop in time."
}
function New-UpgradeCheckpoint {
  if ($SkipCheckpoint) { return }
  $snapshotName = 'DISP-EMS-before-upgrade-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
  Note "Creating Hyper-V checkpoint: $snapshotName"
  Checkpoint-VM -Name $VMName -SnapshotName $snapshotName | Out-Null
}
function Replace-SeedDisk([object]$VM) {
  $temporarySeed = Join-Path $vmDirectory ('cidata-update-' + [guid]::NewGuid().ToString('N') + '.vhdx')
  New-CidataDisk $temporarySeed
  $wasRunning = $VM.State -eq 'Running'
  if ($wasRunning) { Stop-VM -Name $VMName -Force }
  Wait-VMOff $VMName
  $attachedSeed = Get-SeedDrive $VM
  if ($null -eq $attachedSeed) { Fail "VM '$VMName' does not have the persistent CIDATA disk." }
  Remove-VMHardDiskDrive -VMHardDiskDrive $attachedSeed
  if (Test-Path -LiteralPath $seedPath) {
    $backupSeed = $seedPath + '.previous-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    Move-Item -LiteralPath $seedPath -Destination $backupSeed -Force
  }
  Move-Item -LiteralPath $temporarySeed -Destination $seedPath -Force
  Add-VMHardDiskDrive -VMName $VMName -Path $seedPath
  Start-VM -Name $VMName | Out-Null
}
function Ensure-VM([object]$ExistingVM) {
  if ($null -ne $ExistingVM) {
    if (-not (Test-Path -LiteralPath (Join-Path $vmDirectory 'ubuntu-jammy-hyperv.vhdx'))) {
      Fail "Existing VM '$VMName' has no persistent Ubuntu disk under $vmDirectory. Refusing to initialize a replacement VM."
    }
    New-UpgradeCheckpoint
    Replace-SeedDisk $ExistingVM
    return $false
  }
  if ((Test-Path -LiteralPath $vmDirectory) -and (Get-ChildItem -LiteralPath $vmDirectory -Force | Measure-Object).Count -gt 0) {
    Fail "Persistent directory exists but VM '$VMName' does not. Refusing to overwrite existing data under $vmDirectory."
  }
  Require-Path $baseDiskPath 'Missing images\ubuntu-jammy-hyperv.vhdx.'
  New-CidataDisk $seedPath
  New-Item -ItemType Directory -Path $vmDirectory -Force | Out-Null
  $vmDisk = Join-Path $vmDirectory 'ubuntu-jammy-hyperv.vhdx'
  Copy-Item -LiteralPath $baseDiskPath -Destination $vmDisk -Force
  Note "Creating Hyper-V VM: $VMName"
  New-VM -Name $VMName -Generation 2 -MemoryStartupBytes ($MemoryGB * 1GB) -VHDPath $vmDisk -SwitchName $SwitchName | Out-Null
  Set-VMProcessor -VMName $VMName -Count $ProcessorCount
  Set-VM -Name $VMName -AutomaticStopAction ShutDown -AutomaticStartAction StartIfRunning
  Set-VMFirmware -VMName $VMName -EnableSecureBoot Off
  Add-VMHardDiskDrive -VMName $VMName -Path $seedPath
  Start-VM -Name $VMName | Out-Null
  return $true
}
function Configure-PortProxy {
  foreach ($port in @(8089, 9000, 9001)) {
    netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=$port | Out-Null
    netsh interface portproxy add v4tov4 listenaddress=0.0.0.0 listenport=$port connectaddress=$VMAddress connectport=$port | Out-Null
  }
  if ($null -eq (Get-NetFirewallRule -DisplayName 'DISP-EMS HTTP' -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName 'DISP-EMS HTTP' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8089,9000,9001 | Out-Null
  }
}

Assert-Administrator
Assert-HostRequirements
Require-Path $payloadPath 'Missing payload directory. Copy the complete offline package.'
Require-Path $baseDiskPath 'Missing images\ubuntu-jammy-hyperv.vhdx. Rebuild the offline package.'
Require-Path $bootstrapTemplate 'Missing bootstrap.sh.template.'
Require-Path $userDataTemplate 'Missing user-data.template.'
Require-Path $metaDataTemplate 'Missing meta-data.'
if ([string]::IsNullOrWhiteSpace($WindowsSourcePath)) { $WindowsSourcePath = Join-Path $scriptRoot 'windows-source\sources\sxs' }
if ([string]::IsNullOrWhiteSpace($HostAddress)) { $HostAddress = Get-HostIPv4 }
if ([string]::IsNullOrWhiteSpace($HostAddress) -or $HostAddress.Contains(' ')) { Fail 'Pass -HostAddress with the Windows Server IPv4 address reachable by browsers.' }

Ensure-HyperV
if ($PostReboot) { Unregister-ScheduledTask -TaskName 'DISP-EMS Windows offline deployment' -Confirm:$false -ErrorAction SilentlyContinue }
Ensure-Network
$existingVm = Get-VM -Name $VMName -ErrorAction SilentlyContinue
$isFreshInstall = Ensure-VM $existingVm
Configure-PortProxy

Note 'Waiting for the Linux application to become reachable'
$ready = $false
for ($attempt = 1; $attempt -le 120; $attempt++) {
  if (Test-NetConnection -ComputerName $VMAddress -Port 8089 -InformationLevel Quiet -WarningAction SilentlyContinue) { $ready = $true; break }
  Start-Sleep -Seconds 5
}
if ($ready) {
  if ($isFreshInstall) { Write-Host "DISP-EMS has been deployed. Open http://$HostAddress`:8089" }
  else { Write-Host "DISP-EMS has been upgraded without deleting application data. Open http://$HostAddress`:8089" }
  Write-Host 'Initial administrator: ADMIN-001 / dispadmin'
} else {
  Write-Warning 'The VM is running but the application is not ready yet. Run .\manage.ps1 -Action status and inspect the VM console or wait a few minutes.'
}
