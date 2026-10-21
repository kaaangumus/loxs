# LOXS 2.1.1

LOXS — yetkili güvenlik testleri için Java 17 + JavaFX masaüstü zafiyet tarayıcısı.

> Bu sürüm, LOXS'un JavaFX tabanlı güncel sürümüdür. Eski Python fork dosyaları kaldırılmıştır.

> Bu aracı yalnızca sahibi olduğunuz veya yazılı izin aldığınız sistemlerde kullanın.

## İndirmeler

| Platform | Dosya | Gereksinim |
| --- | --- | --- |
| Windows x64 | `LOXS-2.1.1-windows-x64.msi` | Windows 10/11, JDK gerekmez |
| Linux x64 | `LOXS-2.1.1-linux-x64.tar.gz` | Java 17+, X11/Wayland masaüstü |

## Kurulum

### Windows

1. `LOXS-2.1.1-windows-x64.msi` dosyasını çalıştırın.
2. Kurulum `%LOCALAPPDATA%\LOXS` altına yapılır.
3. Başlat menüsünden veya masaüstü kısayolundan **LOXS** açılır.

Kod imzalama sertifikası olmadığı için SmartScreen "Yine de çalıştır" uyarısı gösterebilir.

### Linux

```bash
tar -xzf LOXS-2.1.1-linux-x64.tar.gz
cd LOXS-2.1.1-linux-x64
chmod +x loxs
./loxs
```

Java 17+ gerekir. Masaüstü oturumu yoksa X11 yönlendirmesi veya Xvfb kullanın.

## Tarayıcılar

| Tarayıcı | Yöntem |
| --- | --- |
| SQLi | Zaman tabanlı; taban çizgisi + iki aşamalı doğrulama |
| LFI | Parametre bazlı path traversal; kriter + taban yanıt karşılaştırması |
| CRLF | Benzersiz marker ile header/yanıt bölme kontrolü |
| XSS | Yansıyan payload kontrolü; escape edilmiş yansımalar elenir |
| Open Redirect | `Location` ve meta refresh kontrolü; aynı origin filtresi |

## Öne çıkanlar (2.1.1)

- Windows MSI ve Linux tar.gz paketi aynı sürümden üretilir.
- Java 24+ ortamlarında terminal uyarıları temizlendi.
- Linux için JavaFX native kütüphaneleri jar içinde gelir.
- Tarayıcıya göre değişen parametre paneli ve hedef URL örnekleri.
- Tamamen koyu, ilk kareden itibaren beyaz flaş yapmayan arayüz.
- Özel koyu bilgi/hata/onay pencereleri.
- HTML, CSV ve JSON raporu.
- Demo Lab kaldırıldı; araç tek başına çalışır.

## Doğrulama

- Windows: `gradlew test` → 17/17 geçti.
- Linux: `gradlew test -PjavafxPlatform=linux` → 17/17 geçti.
- Windows MSI sessiz kurulum ve uygulama başlatma test edildi.
- Linux paketi: launcher, jar sınıf yükleme ve JavaFX native dosyaları doğrulandı.

## Sınırlar

- XSS tarayıcısı yansıyan XSS odaklıdır; DOM tabanlı XSS kapsam dışıdır.
- SQLi zaman tabanlıdır; boolean/error tabanlı SQLi kapsam dışıdır.
- Open Redirect kontrolü `Location` ve meta refresh ile sınırlıdır.
- POST formları taranmaz.

## Lisans / Sorumluluk

Eğitim, araştırma ve yetkili güvenlik testleri içindir. İzinsiz kullanım yasal sorumluluk doğurabilir.
