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

write_launcher() {
  local target="$1"
  local entrypoint="$2"

  cat > "$target" <<EOF
#!/usr/bin/env bash
cd "$PROJECT_ROOT"
exec "$PROJECT_ROOT/.venv/bin/python" "$PROJECT_ROOT/$entrypoint" "\$@"
EOF
  chmod +x "$target"
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

step "Installing launchers..."
LOCAL_BIN="${HOME}/.local/bin"
APPLICATIONS_DIR="${HOME}/.local/share/applications"
mkdir -p "$LOCAL_BIN" "$APPLICATIONS_DIR"

write_launcher "${LOCAL_BIN}/loxs" "loxs.py"
write_launcher "${LOCAL_BIN}/loxs-gui" "lox.py"

cat > "${APPLICATIONS_DIR}/loxs.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=LOXS
Comment=Authorized web vulnerability scanner GUI
Exec=${LOCAL_BIN}/loxs-gui
Path=${PROJECT_ROOT}
Icon=utilities-terminal
Terminal=false
Categories=Security;Utility;
Keywords=security;scanner;web;loxs;
StartupNotify=true
EOF

if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database "$APPLICATIONS_DIR" >/dev/null 2>&1 || true
fi

ok "Setup complete."
echo
echo "Run CLI from terminal:"
echo "  loxs"
echo
echo "Run GUI:"
echo "  loxs-gui"
echo "  Or open LOXS from the application menu."
echo
if ! printf '%s' ":${PATH}:" | grep -q ":${LOCAL_BIN}:"; then
  warn "${LOCAL_BIN} is not in PATH for this shell."
  echo "Add this to your shell config if 'loxs' is not found:"
  echo "  export PATH=\"\$HOME/.local/bin:\$PATH\""
fi
