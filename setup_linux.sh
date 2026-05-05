#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

step() {
  printf '\033[36m[*] %s\033[0m\n' "$1"
}

ok() {
  printf '\033[32m[+] %s\033[0m\n' "$1"
}

warn() {
  printf '\033[33m[!] %s\033[0m\n' "$1"
}

if command -v python3 >/dev/null 2>&1; then
  PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1; then
  PYTHON_BIN="python"
else
  echo "Python 3 was not found. Install Python 3.10+ first." >&2
  exit 1
fi

step "Checking Python..."
"$PYTHON_BIN" - <<'PY'
import sys
if sys.version_info < (3, 10):
    raise SystemExit("Python 3.10+ is required.")
print("Python", ".".join(map(str, sys.version_info[:3])))
PY

step "Checking venv support..."
if ! "$PYTHON_BIN" -m venv --help >/dev/null 2>&1; then
  warn "python3-venv is missing."
  echo "Install it with one of these commands, then run this script again:"
  echo "  sudo apt update && sudo apt install -y python3-venv python3-tk"
  echo "  sudo dnf install -y python3-tkinter"
  echo "  sudo pacman -S python tk"
  exit 1
fi

step "Creating virtual environment: .venv"
if [ ! -d ".venv" ]; then
  "$PYTHON_BIN" -m venv .venv
fi

# shellcheck disable=SC1091
source .venv/bin/activate

step "Upgrading pip..."
python -m pip install --upgrade pip

step "Installing requirements..."
python -m pip install -r requirements.txt

step "Checking tkinter..."
if ! python - <<'PY'
import tkinter
print("tkinter ok")
PY
then
  warn "tkinter is missing. GUI needs tkinter."
  echo "Debian/Ubuntu: sudo apt install -y python3-tk"
  echo "Fedora:        sudo dnf install -y python3-tkinter"
  echo "Arch:          sudo pacman -S tk"
fi

step "Checking installed packages..."
python -m pip check

ok "Setup complete."
echo
echo "Run CLI:"
echo "  source .venv/bin/activate && python loxs.py"
echo
echo "Run GUI:"
echo "  source .venv/bin/activate && python lox.py"
