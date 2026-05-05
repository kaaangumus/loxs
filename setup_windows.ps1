param(
    [switch]$SkipVenv
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[*] $Message" -ForegroundColor Cyan
}

function Write-Ok {
    param([string]$Message)
    Write-Host "[+] $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[!] $Message" -ForegroundColor Yellow
}

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

Write-Step "Checking Python..."
$Python = Get-Command python -ErrorAction SilentlyContinue
if (-not $Python) {
    throw "Python was not found. Install Python 3.11+ from https://www.python.org/downloads/windows/ and enable 'Add python.exe to PATH'."
}

$PythonVersion = python -c "import sys; print('.'.join(map(str, sys.version_info[:3])))"
Write-Ok "Python $PythonVersion"

if (-not $SkipVenv) {
    Write-Step "Creating virtual environment: .venv"
    if (-not (Test-Path ".\.venv")) {
        python -m venv .venv
    }

    $PythonExe = ".\.venv\Scripts\python.exe"
    $PipExe = ".\.venv\Scripts\pip.exe"
} else {
    Write-Warn "Skipping venv. Installing into the active Python environment."
    $PythonExe = "python"
    $PipExe = "python -m pip"
}

Write-Step "Upgrading pip..."
& $PythonExe -m pip install --upgrade pip

Write-Step "Installing requirements..."
& $PythonExe -m pip install -r .\requirements.txt

Write-Step "Checking tkinter..."
& $PythonExe -c "import tkinter; print('tkinter ok')"

Write-Step "Checking installed packages..."
& $PythonExe -m pip check

Write-Ok "Setup complete."
Write-Host ""
Write-Host "Run CLI:" -ForegroundColor Cyan
Write-Host "  .\.venv\Scripts\python.exe .\loxs.py"
Write-Host ""
Write-Host "Run GUI:" -ForegroundColor Cyan
Write-Host "  .\.venv\Scripts\python.exe .\lox.py"
