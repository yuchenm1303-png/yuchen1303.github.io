Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$EnvName = "showui"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

function Find-Conda {
  $cmd = Get-Command conda -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }

  $candidates = @(
    "C:\Users\$env:USERNAME\miniconda3\Scripts\conda.exe",
    "C:\Users\$env:USERNAME\anaconda3\Scripts\conda.exe",
    "C:\ProgramData\miniconda3\Scripts\conda.exe",
    "C:\ProgramData\Anaconda3\Scripts\conda.exe"
  )
  foreach ($path in $candidates) {
    if (Test-Path -LiteralPath $path) { return $path }
  }
  return $null
}

function Invoke-Conda {
  param([string[]]$Arguments)
  & $script:CondaExe @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "conda command failed: $($Arguments -join ' ')"
  }
}

function Test-ShowuiEnv {
  $envList = & $script:CondaExe env list
  if ($LASTEXITCODE -ne 0) { throw "Failed to read conda env list." }
  return [bool]($envList | Select-String -Pattern "^\s*$EnvName\s")
}

$script:CondaExe = Find-Conda
if (-not $script:CondaExe) {
  Write-Host "Conda was not found. Install Miniconda, then run .\bootstrap-windows.ps1."
  exit 1
}

if (-not (Test-ShowuiEnv)) {
  Write-Host "Conda env showui was not found. Run first:"
  Write-Host "  .\bootstrap-windows.ps1"
  exit 1
}

Write-Host "Checking PyTorch..."
$torchVersion = & $script:CondaExe run -n $EnvName python -c "import torch; print(torch.__version__)" 2>$null
if ($LASTEXITCODE -ne 0) {
  Write-Host "Torch is not installed in the showui env. Run first:"
  Write-Host "  .\bootstrap-windows.ps1"
  exit 1
}
Write-Host "PyTorch: $torchVersion"

Write-Host "Checking CUDA..."
$cudaInfo = & $script:CondaExe run -n $EnvName python -c "import torch; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU')"
if ($LASTEXITCODE -ne 0) {
  throw "CUDA check command failed."
}
$cudaInfo | ForEach-Object { Write-Host $_ }
if ($cudaInfo -match "False") {
  Write-Host ""
  Write-Host "CUDA=False. The model will not be started."
  Write-Host "- PyTorch may have been installed as the CPU-only build."
  Write-Host "- The NVIDIA driver may be too old or may not match the selected CUDA wheel."
  Write-Host "- Upgrade the NVIDIA driver, or use the current Windows CUDA wheel from the PyTorch selector."
  exit 1
}

$env:SHOWUI_HOST = if ($env:SHOWUI_HOST) { $env:SHOWUI_HOST } else { "127.0.0.1" }
$env:SHOWUI_PORT = if ($env:SHOWUI_PORT) { $env:SHOWUI_PORT } else { "9100" }
$env:SHOWUI_MAX_PIXELS = if ($env:SHOWUI_MAX_PIXELS) { $env:SHOWUI_MAX_PIXELS } else { "602112" }
$env:SHOWUI_MAX_NEW_TOKENS = if ($env:SHOWUI_MAX_NEW_TOKENS) { $env:SHOWUI_MAX_NEW_TOKENS } else { "64" }

Write-Host ""
Write-Host "Service URL: http://$($env:SHOWUI_HOST):$($env:SHOWUI_PORT)"
Write-Host "Health URL:  http://$($env:SHOWUI_HOST):$($env:SHOWUI_PORT)/health"
Write-Host ""
Invoke-Conda @("run", "-n", $EnvName, "python", "server.py")
