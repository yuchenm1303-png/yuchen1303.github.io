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

function Invoke-ShowuiPython {
  param([string[]]$Arguments)
  $condaArgs = @("run", "-n", $EnvName, "python") + $Arguments
  Invoke-Conda $condaArgs
}

function Test-ShowuiEnv {
  $envList = & $script:CondaExe env list
  if ($LASTEXITCODE -ne 0) { throw "Failed to read conda env list." }
  return [bool]($envList | Select-String -Pattern "^\s*$EnvName\s")
}

Write-Host "[1/5] Check conda"
$script:CondaExe = Find-Conda
if (-not $script:CondaExe) {
  Write-Host "Conda was not found. Please install Miniconda, then run this script again."
  Write-Host "Miniconda: https://docs.conda.io/en/latest/miniconda.html"
  exit 1
}
Write-Host "Using conda: $script:CondaExe"

Write-Host "[2/5] Create showui environment"
if (Test-ShowuiEnv) {
  Write-Host "Conda env showui already exists. Skipping create."
} else {
  Invoke-Conda @("create", "-n", $EnvName, "python=3.10", "pip", "-y")
}

Write-Host "[3/5] Install CUDA PyTorch"
Invoke-ShowuiPython @("-m", "ensurepip", "--upgrade")
Invoke-ShowuiPython @("-m", "pip", "install", "--upgrade", "pip")
Invoke-ShowuiPython @("-m", "pip", "install", "torch", "torchvision", "--index-url", "https://download.pytorch.org/whl/cu121")

Write-Host "[4/5] Install provider requirements"
if (-not (Test-Path -LiteralPath ".\requirements.txt")) {
  throw "requirements.txt not found. Please run this script from tools/showui-local-provider."
}
Invoke-ShowuiPython @("-m", "pip", "install", "-r", "requirements.txt")

Write-Host "[5/5] Verify CUDA"
$cudaInfo = & $script:CondaExe run -n $EnvName python -c "import torch; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU')"
if ($LASTEXITCODE -ne 0) {
  throw "CUDA verification command failed."
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

Write-Host ""
Write-Host "Install finished. Run .\start-windows.ps1 to start the service."
