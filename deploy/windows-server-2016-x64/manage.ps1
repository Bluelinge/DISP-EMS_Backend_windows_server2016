param(
  [ValidateSet('status', 'start', 'stop', 'restart', 'upgrade', 'backup', 'uninstall')]
  [string]$Action = 'status',
  [string]$HostAddress = '',
  [string]$VMName = 'DISP-EMS',
  [string]$SwitchName = 'DISP-EMS-Internal',
  [string]$NatName = 'DISP-EMS-NAT'
)

$ErrorActionPreference = 'Stop'
$scriptRoot = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
Import-Module Hyper-V -ErrorAction Stop

function Remove-PortProxy([int]$Port) {
  netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=$Port | Out-Null
}
function Get-CurrentVm { Get-VM -Name $VMName -ErrorAction SilentlyContinue }
function New-DataCheckpoint {
  $vm = Get-CurrentVm
  if ($null -eq $vm) { throw "VM '$VMName' does not exist." }
  $snapshotName = 'DISP-EMS-manual-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
  Checkpoint-VM -Name $VMName -SnapshotName $snapshotName | Out-Null
  Write-Host "Created Hyper-V checkpoint: $snapshotName"
}

$vm = Get-CurrentVm
switch ($Action) {
  'status' {
    if ($null -eq $vm) { Write-Host "VM '$VMName' does not exist."; exit 1 }
    Get-VM -Name $VMName | Format-List Name,State,Status,CPUUsage,MemoryAssigned,Uptime
    Get-VMNetworkAdapter -VMName $VMName | Select-Object VMName,SwitchName,Status,MacAddress
    Get-VMSnapshot -VMName $VMName -ErrorAction SilentlyContinue | Select-Object Name,CreationTime,CheckpointType
  }
  'start' {
    if ($null -eq $vm) { throw "VM '$VMName' does not exist." }
    Start-VM -Name $VMName | Out-Null
    Write-Host "Started $VMName"
  }
  'stop' {
    if ($null -eq $vm) { throw "VM '$VMName' does not exist." }
    Stop-VM -Name $VMName -Force
    Write-Host "Stopped $VMName"
  }
  'restart' {
    if ($null -eq $vm) { throw "VM '$VMName' does not exist." }
    Restart-VM -Name $VMName -Force
    Write-Host "Restarted $VMName"
  }
  'backup' {
    New-DataCheckpoint
  }
  'upgrade' {
    if ([string]::IsNullOrWhiteSpace($HostAddress)) {
      $HostAddress = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } | Select-Object -First 1 -ExpandProperty IPAddress)
    }
    if ([string]::IsNullOrWhiteSpace($HostAddress)) { throw 'Pass -HostAddress with the server IPv4 address.' }
    & (Join-Path $scriptRoot 'install.ps1') -HostAddress $HostAddress -VMName $VMName -SwitchName $SwitchName -NatName $NatName
  }
  'uninstall' {
    if ($null -ne $vm) {
      Stop-VM -Name $VMName -Force -ErrorAction SilentlyContinue
      Remove-VM -Name $VMName -Force
    }
    Remove-PortProxy 8089
    Remove-PortProxy 9000
    Remove-PortProxy 9001
    Remove-NetNat -Name $NatName -Confirm:$false -ErrorAction SilentlyContinue
    Remove-VMSwitch -Name $SwitchName -Force -ErrorAction SilentlyContinue
    Remove-NetFirewallRule -DisplayName 'DISP-EMS HTTP' -ErrorAction SilentlyContinue
    Write-Host 'DISP-EMS VM, NAT, port forwarding and firewall rule removed.'
    Write-Host 'Persistent VM files and checkpoints were kept under C:\ProgramData\DISP-EMS.'
  }
}
