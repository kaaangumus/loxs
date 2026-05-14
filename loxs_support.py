from __future__ import annotations

import random
import urllib.parse
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

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
