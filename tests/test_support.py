from pathlib import Path
import unittest

from loxs_support import headers_with_cookie, load_payload_file, normalize_url, parse_cookie_string


class SupportHelpersTest(unittest.TestCase):
    def test_normalize_url_adds_default_scheme(self) -> None:
        self.assertEqual(normalize_url("example.com/path"), "http://example.com/path")

    def test_normalize_url_keeps_existing_scheme(self) -> None:
        self.assertEqual(normalize_url("https://example.com"), "https://example.com")

    def test_parse_cookie_string_ignores_invalid_parts(self) -> None:
        self.assertEqual(
            parse_cookie_string("session=abc; invalid; theme=dark"),
            [{"name": "session", "value": "abc"}, {"name": "theme", "value": "dark"}],
        )

    def test_headers_with_cookie_includes_cookie_header(self) -> None:
        headers = headers_with_cookie("session=abc")

        self.assertIn("User-Agent", headers)
        self.assertEqual(headers["Cookie"], "session=abc")

    def test_load_payload_file_strips_empty_lines(self) -> None:
        payload_file = Path("test_payloads.tmp")
        try:
            payload_file.write_text("\none\n\n two \n", encoding="utf-8")
            self.assertEqual(load_payload_file(str(payload_file)), ["one", "two"])
        finally:
            payload_file.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
