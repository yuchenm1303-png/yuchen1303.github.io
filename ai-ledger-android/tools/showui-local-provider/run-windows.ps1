Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "ShowUI local provider directory: $ScriptDir"
Write-Host ""
Write-Host "Recommended environment setup:"
Write-Host "  conda create -n showui python=3.10 -y"
Write-Host "  conda activate showui"
Write-Host ""
Write-Host "Install the CUDA build of PyTorch from the official selector:"
Write-Host "  https://pytorch.org/get-started/locally/"
Write-Host ""
Write-Host "Default CUDA 12.1 example for many Windows NVIDIA setups:"
Write-Host "  python -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121"
Write-Host "If your driver supports a newer CUDA wheel, you can choose cu126 or cu128 from the PyTorch site."
Write-Host ""

if (-not (Test-Path ".\requirements.txt")) {
  throw "requirements.txt not found. Please run this script from tools/showui-local-provider."
}

Write-Host "Python runtime:"
python --version
python -c "import sys; print(sys.executable)"

python -m ensurepip --upgrade
python -m pip install --upgrade pip
python -m pip install -r requirements.txt

Write-Host ""
Write-Host "Checking PyTorch installation..."
$torchVersion = python -c "import torch; print(torch.__version__)" 2>$null
if ($LASTEXITCODE -ne 0) {
  Write-Host ""
  Write-Host "PyTorch is not installed in the current Python environment."
  Write-Host "Please run the CUDA PyTorch install command first, then run this script again:"
  Write-Host "  python -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121"
  Write-Host "If your driver supports a newer CUDA wheel, choose cu126 or cu128 from https://pytorch.org/get-started/locally/."
  exit 1
}
Write-Host "PyTorch: $torchVersion"

Write-Host ""
Write-Host "CUDA check:"
$cudaInfo = python -c "import torch; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU')"
Write-Host $cudaInfo
if ($cudaInfo -match "False") {
  Write-Host ""
  Write-Host "CUDA is not available."
  Write-Host "- PyTorch may have been installed as the CPU-only build."
  Write-Host "- Your NVIDIA driver may not match the selected CUDA wheel."
  Write-Host "- Re-run the CUDA PyTorch install command, for example:"
  Write-Host "  python -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121"
}

$env:SHOWUI_MODEL_ID = if ($env:SHOWUI_MODEL_ID) { $env:SHOWUI_MODEL_ID } else { "showlab/ShowUI-2B" }
$env:SHOWUI_HOST = if ($env:SHOWUI_HOST) { $env:SHOWUI_HOST } else { "127.0.0.1" }
$env:SHOWUI_PORT = if ($env:SHOWUI_PORT) { $env:SHOWUI_PORT } else { "9100" }
$env:SHOWUI_MIN_PIXELS = if ($env:SHOWUI_MIN_PIXELS) { $env:SHOWUI_MIN_PIXELS } else { "200704" }
$env:SHOWUI_MAX_PIXELS = if ($env:SHOWUI_MAX_PIXELS) { $env:SHOWUI_MAX_PIXELS } else { "602112" }
$env:SHOWUI_MAX_NEW_TOKENS = if ($env:SHOWUI_MAX_NEW_TOKENS) { $env:SHOWUI_MAX_NEW_TOKENS } else { "64" }

Write-Host ""
Write-Host "Starting ShowUI provider at http://$($env:SHOWUI_HOST):$($env:SHOWUI_PORT)"
python server.py
