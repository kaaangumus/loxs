# LOXS

LOXS is a Python web vulnerability scanner for authorized security testing. It includes the original CLI workflow plus a Tkinter GUI with authenticated scan support, dark theme, grouped HTML reports, and Windows/Linux setup scripts.

> Use this tool only on systems you own or have explicit permission to test.

## Scanners

| Scanner | Purpose |
| --- | --- |
| LFI | Local File Inclusion checks with configurable success criteria |
| Open Redirect | Redirect parameter testing with Selenium support |
| SQLi | Time-based SQL injection checks |
| XSS | Reflected XSS checks with Selenium alert detection |
| CRLF | Header/body injection checks with built-in payloads |

## Highlights

- CLI and Tkinter GUI launchers
- Cookie header support for authenticated targets
- Selenium cookie injection for XSS and Open Redirect scans
- Chrome profile support for existing logged-in browser sessions
- Dark themed GUI with scanner-specific controls
- Multi-threaded request-based scans
- HTML report export grouped by site and vulnerability type
- Windows PowerShell setup script
- Linux setup script with `.venv` creation

## Requirements

- Python 3.10+
- Google Chrome or Chromium for Selenium-based scans
- Tkinter for GUI mode

Python packages are listed in [requirements.txt](requirements.txt).

## Installation

### Clone

```bash
git clone https://github.com/kaaangumus/loxs.git
cd loxs
```

### Windows

Run PowerShell in the project folder:

```powershell
.\setup_windows.ps1
```

Start the GUI:

```powershell
.\.venv\Scripts\python.exe .\lox.py
```

Start the CLI:

```powershell
.\.venv\Scripts\python.exe .\loxs.py
```

If PowerShell blocks scripts, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\setup_windows.ps1
```

### Linux

```bash
chmod +x setup_linux.sh
./setup_linux.sh
```

Start the GUI:

```bash
source .venv/bin/activate
python lox.py
```

Start the CLI:

```bash
source .venv/bin/activate
python loxs.py
```

If venv or Tkinter is missing on Debian/Ubuntu:

```bash
sudo apt update
sudo apt install -y python3-venv python3-tk
```

Fedora:

```bash
sudo dnf install -y python3-tkinter
```

Arch:

```bash
sudo pacman -S python tk
```

## GUI Usage

Launch:

```bash
python lox.py
```

Basic flow:

1. Select a scanner: `SQLi`, `LFI`, `CRLF`, `XSS`, or `Open Redirect`.
2. Enter one or more target URLs, one per line.
3. Select or edit the payload file when the scanner needs one.
4. Paste a raw browser cookie string if the target requires login.
5. Set thread count and timeout.
6. Click `Start`.
7. Click `Export Report` after results appear.

Scanner-specific controls:

| Control | When It Appears |
| --- | --- |
| Payload file | All scanners except CRLF |
| LFI criteria | LFI only |
| Chrome profile | XSS and Open Redirect |

## Cookie Authentication

For authenticated targets, copy the full browser cookie header and paste it into the GUI or CLI cookie prompt.

Example:

```text
session=abc123; PHPSESSID=xyz789
```

Where to get it:

1. Open the target in your browser after logging in.
2. Press `F12`.
3. Open the `Network` tab.
4. Click any authenticated request.
5. Copy the `Cookie:` request header value.

Request-based scanners send it as:

```http
Cookie: session=abc123; PHPSESSID=xyz789
```

Selenium scanners visit the target domain first, inject the cookies into Chrome, then continue to the payload URL.

## Chrome Profile Authentication

For XSS and Open Redirect, you may also reuse a logged-in Chrome profile.

1. Log in to the target in Chrome.
2. Close Chrome completely.
3. In LOXS GUI, choose the Chrome profile directory.

Common profile paths:

Windows:

```text
%LOCALAPPDATA%\Google\Chrome\User Data
```

Linux:

```text
~/.config/google-chrome
```

When a profile is used, scanning should be kept single-threaded to avoid Chrome profile locks.

## Reports

The GUI exports HTML reports with:

- Site summary
- Unique findings grouped by site, scanner, endpoint, and parameter
- One visible finding per vulnerable URL parameter
- Working payloads hidden under expandable detail sections
- Full raw results table

This keeps reports readable while preserving evidence for payloads that worked.

## Payloads

Default payload files live under [payloads](payloads):

```text
payloads/
  lfi.txt
  or.txt
  xss.txt
  xsspollygots.txt
  sqli/
    generic.txt
    mysql.txt
    oracle.txt
    postgresql.txt
    xor.txt
```

You can edit these files or select custom payload files in the GUI.

## CLI Usage

Run:

```bash
python loxs.py
```

Menu:

```text
1] LFI Scanner
2] OR Scanner
3] SQLi Scanner
4] XSS Scanner
5] CRLF Scanner
6] tool Update
7] Exit
```

The CLI prompts for URL files, payload files, cookies, thread count, and reports depending on the selected scanner.

## Development Checks

Syntax check:

```bash
python -m py_compile loxs.py loxs_gui.py lox.py
```

Dependency check:

```bash
python -m pip check
```

## Legal Notice

LOXS is intended for education, research, and authorized security testing only. Do not scan third-party systems without clear permission. You are responsible for complying with applicable laws and rules.

## License

See [LICENSE](LICENSE).
