# LOXS

LOXS, yetkili güvenlik testleri için geliştirilmiş **Java 17 + JavaFX** masaüstü zafiyet tarayıcısıdır. Chrome/Selenium gerektirmez; HTTP tabanlı çalışır ve Windows MSI ile Linux tar.gz paketi üretir.

> Bu aracı yalnızca sahibi olduğunuz veya yazılı izin aldığınız sistemlerde kullanın.

## Tarayıcılar

| Tarayıcı | Yöntem |
| --- | --- |
| SQLi | Zaman tabanlı; taban çizgisi + iki aşamalı doğrulama |
| LFI | Parametre bazlı path traversal; kriter + taban yanıt karşılaştırması |
| CRLF | Benzersiz marker ile header/yanıt bölme kontrolü |
| XSS | Yansıyan payload kontrolü; escape edilmiş yansımalar elenir |
| Open Redirect | `Location` ve meta refresh kontrolü; aynı origin filtresi |

## Özellikler

- Modern koyu temalı JavaFX arayüzü, 125% DPI uyumlu
- **Hızlı / Standart / Derin** tarama profilleri
- Son ayarların kalıcı saklanması (çerez saklanmaz)
- Tarama öncesi yetki onayı
- Hedef URL'de parametre otomatik algılama
- Cookie header desteği
- TLS doğrulamasını kapatma seçeneği
- Sınırlı iş kuyruğu ve gerçek iptal
- HTML, CSV ve JSON raporu
- Raporlarda önem, güven seviyesi ve çözüm önerisi
- Windows MSI ve Linux tar.gz paketi

## Gereksinimler

- JDK 17+
- Gradle (veya projedeki wrapper)
- MSI için WiX Toolset 3.x (build script otomatik indirir)

## Çalıştırma

Windows:

```powershell
.\gradlew.bat run
```

Linux:

```bash
./gradlew run
```

## Testler

Windows:

```powershell
.\gradlew.bat test
```

Linux:

```bash
./gradlew test
```

## MSI (Windows)

```powershell
powershell -ExecutionPolicy Bypass -File .\build-msi.ps1
```

Çıktı:

```text
dist/LOXS-2.1.0.msi
```

## Linux Paketi

```bash
./package-linux.sh
```

Çıktı:

```text
dist-linux/LOXS-2.1.0-linux-x64.tar.gz
```

Paket içindeki `loxs` betiği ile çalıştırılır; Java 17+ ve masaüstü (X11/Wayland) gerekir.

## Sınırlar

- XSS tarayıcısı yansıyan XSS odaklıdır; DOM tabanlı XSS kapsam dışıdır.
- SQLi zaman tabanlıdır; boolean/error tabanlı SQLi kapsam dışıdır.
- Open Redirect kontrolü `Location` ve meta refresh ile sınırlıdır.
- POST formları taranmaz.

## Lisans / Sorumluluk

Eğitim, araştırma ve yetkili güvenlik testleri içindir. İzinsiz kullanım yasal sorumluluk doğurabilir.
