#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

VERSION=$(sed -n "s/^version = '\(.*\)'/\1/p" build.gradle | head -1)
if [ -z "${VERSION}" ]; then
  echo "Surum build.gradle dosyasindan okunamadi." >&2
  exit 1
fi

echo "[*] Linux jar derleniyor (v${VERSION})..."
./gradlew clean test fatJar -PjavafxPlatform=linux --no-daemon --console=plain

PKG="dist-linux/LOXS-${VERSION}-linux-x64"
rm -rf "$PKG"
mkdir -p "$PKG"
cp "build/libs/loxs-java-${VERSION}-all.jar" "$PKG/LOXS-${VERSION}.jar"

cat > "$PKG/loxs" <<'LAUNCHER'
#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
JAR="$(find "$DIR" -maxdepth 1 -name 'LOXS-*.jar' | head -1)"

if [ -z "$JAR" ]; then
  echo "LOXS jar dosyasi bulunamadi." >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java bulunamadi. Java 17 veya uzeri kurun." >&2
  exit 1
fi

JAVA_MAJOR=$(java -version 2>&1 | awk -F'"' '/version/ {print $2; exit}' | cut -d. -f1)
if [[ "${JAVA_MAJOR}" =~ ^[0-9]+$ ]] && (( JAVA_MAJOR < 17 )); then
  echo "LOXS icin Java 17+ gerekli (mevcut: ${JAVA_MAJOR})." >&2
  exit 1
fi

JAVA_FLAGS=()
if [[ "${JAVA_MAJOR}" =~ ^[0-9]+$ ]] && (( JAVA_MAJOR >= 24 )); then
  JAVA_FLAGS+=(--enable-native-access=ALL-UNNAMED)
  JAVA_FLAGS+=(--sun-misc-unsafe-memory-access=allow)
fi

exec java "${JAVA_FLAGS[@]}" -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar "$JAR" "$@"
LAUNCHER
chmod +x "$PKG/loxs"

cat > "$PKG/README.txt" <<README
LOXS ${VERSION} - Linux x64
========================

Gereksinim: Java 17 veya uzeri (java -version ile kontrol edin).

Calistirma:
  ./loxs

Dosya izinleri korunmadiysa:
  chmod +x loxs
  ./loxs

Not: Masaustu (X11/Wayland) gerekir. Sunucuda baslatmak icin X11 yonlendirmesi
veya Xvfb kullanin.

Windows surumu icin LOXS-${VERSION}.msi dosyasini kullanin.
README

rm -f "dist-linux/LOXS-${VERSION}-linux-x64.tar.gz"
tar -czf "dist-linux/LOXS-${VERSION}-linux-x64.tar.gz" -C dist-linux "LOXS-${VERSION}-linux-x64"

echo "[+] Linux paketi hazir: dist-linux/LOXS-${VERSION}-linux-x64.tar.gz"
