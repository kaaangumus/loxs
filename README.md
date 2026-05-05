<div align="center">
   <a href="https://github.com/coffinxp/loxs"><img src="https://github.com/user-attachments/assets/9fadee1e-a33c-46e3-9eca-c04aa47a443e" hight="225" width="450" align="center"/></a>
</div>

<br>
<br>
<br>

<div align="center">
   
|Loxs|Multi Vulnerability Scanner|for web application|
|----------------|--------------|-------------|
| `L`| `=`| `Local File Inclusion (LFI)`|
| `O`| `=`| `Open Redirection (OR)`|
| `X`| `=`| `Cross Site Scripting (XSS)`|
| `S`| `=`| `Structured Query Language Injection (SQLi)`|
|    |    | `Carriage Return Line Feed Injection (CRLF)`|

> **Loxs** is an easy-to-use tool that finds web issues like `LFI` - `OR` - `SQLi` - `XSS` - `CRLF`. <br><br> *`Made by`* - [`AnonKryptiQuz`](https://github.com/AnonKryptiQuz) x [`Coffinxp`](https://github.com/coffinxp) x [`HexShad0w`](https://github.com/HexShad0w) x [`Naho`](https://github.com/Naho666) x [`1hehaq`](https://github.com/1hehaq) x [`Hghost010`](https://github.com/Hghost0x00)!

</div>

<hr>

<br>
<br>
<br>


| Features                          | About                                                                       |
|-----------------------------------|-----------------------------------------------------------------------------|
| `LFI Scanner`                     | Detect Local File Inclusion vulnerabilities.                                |
| `OR Scanner`                      | Identify Open Redirect vulnerabilities.                                     |
| `SQL Scanner`                     | Detect SQL Injection vulnerabilities.                                       |
| `XSS Scanner`                     | Identify Cross-Site Scripting vulnerabilities.                              |
| `CRLF Scanner`                    | Detect Carriage Return Line Feed Injection vulnerabilities.                 |
| `Multi-threaded Scanning`         | Improved performance through multi-threading.                               |
| `Customizable Payloads`           | Adjust payloads to suit specific targets.                                   |
| `Success Criteria`                | Modify success detection criteria for specific use cases.                   |
| `User-friendly CLI`               | Simple and intuitive command-line interface.                                |
| `Save Vulnerable URLs`            | Option to save vulnerable URLs to a file for future reference.              |
| `HTML Report Generation`          | Generates a detailed HTML report of found vulnerabilities.                  |
<!-- | `Share HTML Report via Telegram`  | Share HTML vulnerability reports directly through Telegram.                 | -->

<br>
<hr>
<br>
<br>

| Language                          | Packages                                                                    |
|-----------------------------------|-----------------------------------------------------------------------------|
| ***Python***| `Python 3.x` `webdriver_manager` `selenium` `aiohttp` `beautifulsoup4` `colorama` `rich` `requests` `gitpython` `prompt_toolkit` `pyyaml` `Flask`|

<br>
<hr>
<br>

## Installation

### Clone the repository

```bash
git clone https://github.com/coffinxp/loxs.git
```
```bash
cd loxs
```

### Install the requirements

```bash
pip3 install -r requirements.txt
```

### Windows setup

PowerShell:

```powershell
.\setup_windows.ps1
```

Run the GUI after setup:

```powershell
.\.venv\Scripts\python.exe .\lox.py
```

Run the CLI after setup:

```powershell
.\.venv\Scripts\python.exe .\loxs.py
```

### Linux setup

```bash
chmod +x setup_linux.sh
./setup_linux.sh
```

If `python3-venv` or `tkinter` is missing on Debian/Ubuntu:

```bash
sudo apt update && sudo apt install -y python3-venv python3-tk
```

Run the GUI after setup:

```bash
source .venv/bin/activate && python lox.py
```

Run the CLI after setup:

```bash
source .venv/bin/activate && python loxs.py
```

### Run the Script

```bash
python3 loxs.py
```

### Run the Tkinter GUI

```powershell
python .\lox.py
```

or:

```powershell
python .\loxs_gui.py
```
<!-- to update the tool to the latest version
```bash
just edit the config.yml file with your tool directory
after pressing 5 and exiting from the tool run the tool again it will run with an updated version
``` -->

----

| Input Information         |                                                                                         |
|---------------------------|-----------------------------------------------------------------------------------------|
| Input URL/File            | Provide a single URL or an input file containing multiple URLs for scanning.            |
| Payload File              | Select or provide a custom payload file for the specific type of vulnerability scanning.|
| Success Criteria          | Define patterns or strings indicating a successful exploitation attempt.                |
| Concurrent Threads        | Set the number of threads for multi-threaded scanning.                                  |
| View and Save Results     | Display results in real-time during the scan, and save vulnerable URLs for future use.  |

----

## Authenticated Selenium Scans

If the target site requires a logged-in session, use the Selenium-based scanners with an existing Chrome profile.

1. Log in to the target site in Chrome with the profile you want to reuse.
2. Close Chrome before starting a scan so the profile directory is not locked.
3. When Loxs asks for the Chrome profile directory, enter the profile data folder path, for example `%LOCALAPPDATA%\Google\Chrome\User Data` on Windows or `~/.config/google-chrome` on Linux.
4. Use this mode for the Selenium-based scanners, especially Open Redirect and XSS.
5. When a profile is provided, Loxs automatically reduces concurrency to a single Selenium driver to avoid profile conflicts.

----

| Customization              |                                                                                          |
|----------------------------|------------------------------------------------------------------------------------------|
| Custom Payloads            | Modify or create payload files for different vulnerability types to target specific apps.|
| Success Criteria           | Adjust the tool's success patterns to more accurately detect successful exploitations.   |
| Concurrent Threads         | Control the number of threads used during the scan for performance optimization.         |


----

### Chrome Installation

```bash
wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb
```

```bash
sudo dpkg -i google-chrome-stable_current_amd64.deb
```

- If you encounter any errors during installation, use the following command:

```bash
sudo apt -f install
```

```bash
sudo dpkg -i google-chrome-stable_current_amd64.deb
```

----

### Chrome Driver Installation

```bash
wget https://storage.googleapis.com/chrome-for-testing-public/128.0.6613.119/linux64/chromedriver-linux64.zip
```
```bash
unzip chromedriver-linux64.zip
```
```bash
cd chromedriver-linux64 
```
```bash
sudo mv chromedriver /usr/bin
```
<hr>

> [!WARNING]  
> Loxs is intended for educational and ethical hacking purposes only. It should only be used to test systems you own or have explicit permission to test. Unauthorized use of third-party websites or systems without consent is illegal and unethical.

## 📊 Repository Stats

[![License](https://img.shields.io/badge/BSD-3-Clause.svg)](LICENSE)
[![Python](https://img.shields.io/badge/Python-3.7+-blue.svg)](https://www.python.org/downloads/)
![GitHub repo size](https://img.shields.io/github/repo-size/coffinxp/loxs)
![GitHub last commit](https://img.shields.io/github/last-commit/coffinxp/loxs)
[![GitHub Stars](https://img.shields.io/github/stars/coffinxp/loxs?style=social)](https://github.com/coffinxp/loxs/stargazers)
![GitHub issues](https://img.shields.io/github/issues/coffinxp/loxs)

<br>

<p align="center">
<img src="https://github.com/user-attachments/assets/9ec3fed0-45ff-4cb3-988c-f8cd66e85082">
</p>


<br>





