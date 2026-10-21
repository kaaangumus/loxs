# Changelog

## 2.1.1

- Java 24+ ortamlarında görünen native access ve Unsafe uyarıları Linux launcher'da susturuldu.
- JavaFX "unnamed module" bilgi uyarısı kod tarafında kapatıldı.
- Linux launcher, Java sürümüne göre güvenli JVM bayraklarını otomatik seçer.

## 2.1.0

- Linux sürümü eklendi (JavaFX Linux native kütüphaneleri, çalıştırma betiği ve tar.gz paketi).
- Windows MSI ve Linux paketi aynı sürümden üretiliyor.
- Demo Lab kaldırıldı; bağımlılığı olan dosyalar ve Gradle alt projesi silindi.
- Windows'ta başlık çubuğu DWM ile koyu; Linux'ta JavaFX varsayılan başlık çubuğu kullanılır.
- Linux paketi Java 17+ gerektirir.

## 2.0.7

- Hedef URL örneği artık seçilen tarayıcıya göre değişiyor:
  - SQLi: `?id=1`
  - LFI: `?file=index`
  - XSS: `?q=test`
  - CRLF: `?next=/anasayfa`
  - Open Redirect: `?url=https://hedef.com`

## 2.0.6

- "Dosya Seç" butonu ikonlu ve tek satır metinle yeniden tasarlandı.
- Genel metin renkleri gri tondan beyaza yakın tonlara yükseltildi; başlıklar, ayar etiketleri, sekme ve tablo yazıları daha okunur hale getirildi.

## 2.0.5

- "Dosyadan" butonu hedef alanının yüksekliğini dolduracak şekilde yeniden tasarlandı: iki satırlı, koyu temaya uyumlu, hover'da vurgulanan buton.

## 2.0.4

- Başlık çubuğundan ve üst bardan "Yetkili Güvenlik Tarayıcısı" yazısı kaldırıldı; yalnızca `LOXS 2.0.4` görünür.

## 2.0.3

- Tarayıcıya göre parametre paneli: SQLi seçilince SQLi eşiği/taban farkı, LFI seçilince LFI kriteri görünür; diğer tarayıcılarda ilgisiz alanlar gizlenir.
- CRLF seçilince payload alanı tamamen gizlenir.
- Başlık çubuğundaki bozuk Türkçe karakterler düzeltildi (`Yetkili Güvenlik Tarayıcısı`).
- Panel altındaki açıklama seçilen tarayıcıya göre değişir.

## 2.0.2

- Açılıştaki beyaz ekran flaşı giderildi: sahne ve kök paneller ilk kareden itibaren koyu.
- Alert/onay pencereleri JavaFX `Alert` yerine tamamen koyu özel dialog pencereleriyle değiştirildi (ilk karede beyazlık yok).
- Rapor menüsü beyaz `ContextMenu` yerine koyu özel popup ile değiştirildi.
- Windows başlık çubuğu artık gösterilir gösterilmez koyuya boyanıyor (250 ms gecikme kaldırıldı).
- Thread, timeout, profil, SQLi eşiği, taban farkı, payload, çerez ve TLS ayarları ana ekranda sağdaki kalıcı "Tarama Ayarları" panelinde görünür hale getirildi.
- Taranan hedefe göre payload/LFI kriteri alanları otomatik gizlenip gösteriliyor.

## 2.0.1

- Üstteki “YETKİLİ TEST” ve “Nasıl Kullanılır?” metinleri kaldırıldı.
- “Örnek hedef” düğmesi ve metni kaldırıldı.
- Bağlantı testi için hedef doğrulaması eklendi; `d.d` gibi geçersiz hedefler reddediliyor.
- Alert ve context menü popup’ları koyu temaya bağlandı.
- Windows başlık çubuğu DWM ile koyu temaya alındı.
- Pencere boyutu ekrana daha rahat sığacak şekilde küçültüldü.
- Hedef alanı prompt’u sadeleştirildi.

## 2.0.0 — JavaFX

- LOXS arayüzü tamamen JavaFX’e taşındı.
- Swing/FlatLaf bağımlılığı kaldırıldı.
- Modern koyu tema, CSS tabanlı tasarım.
- Sol tarayıcı menüsü, kompakt hedef çubuğu, özet kartları ve boş durum ekranı.
- Bulgu tablosu + detay paneli.
- Ayarlar sekmesi: payload, çerez, profil, thread/timeout, SQLi eşikleri.
- HTML / CSV / JSON rapor.
- JavaFX tabanlı bağlantı testi ve yetki onayı.
- UTF-8 başlık ve metinler.

## 1.4.3

- Boş dev tablo yerine özet kartları ve “Taramaya hazır” durumu eklendi.
- Bulgular / Temiz / Hata / Süre kartları canlı güncelleniyor.
- Boş durum ekranına örnek hedef ve kullanım rehberi düğmeleri eklendi.
- Tarama sırasında “Taranıyor…” durumu gösteriliyor.
- Boş durum simgesi düzgün görünen LOXS markasıyla değiştirildi.

## 1.4.2

- Pencere başlığındaki bozuk UTF-8 metin düzeltildi (ASCII başlık).
- Hedef alanına “Hedef URL'ler” başlığı eklendi.
- Parametre sütununun dar pencerede kırpılması azaltıldı.
- 920x600 minimum boyutta yerleşim doğrulandı.

## 1.4.1

- Yerleşim yeniden düzenlendi: hedef çubuğu üstte, gelişmiş ayarlar ayrı sekmede.
- Sonuç/günlük alanı pencereyle birlikte esniyor.
- Hedef alanı küçük pencerelerde küçülebiliyor; sabit genişlik kaldırıldı.
- Ayarlar sekmesi kaydırılabilir; dar pencerede taşma yapmıyor.
- Buton dinleyicileri yeni yerleşime taşındı.

## 1.4.0

- Responsive yerleşim: hedef alanı, sekmeler ve tablo küçük pencerelere uyum sağlıyor.
- Kompakt sidebar ve daha modern başlık/rozet düzeni.
- Gereksiz pill buton görünümü kaldırıldı.
- UTF-8 sertleştirmesi: dosya, stdout/stderr ve MSI JVM ayarları.
- Türkçe karakterler için Segoe UI tabanlı UI fontları.
- Minimum pencere boyutu küçültüldü.

## 1.3.0

- Tam Tarama modu ana uygulamadan çıkarıldı.
- Subdomain, crawler, parameter harvester ve toplu tarama ayrı **LOXS Recon** projesine taşındı.
- Ana LOXS artık yalnızca tekil zafiyet tarayıcılarına odaklanıyor.
- Jsoup ve recon bağımlılıkları ana paketten kaldırıldı.

## 1.2.3

- IP/port hedeflerinde port kaybı düzeltildi.
- IP ve URL-only taramalarda gereksiz kök host istekleri kaldırıldı.
- Tam Tarama kapsamı artık yalnızca seed URL ile sınırlandırılabiliyor.

## 1.2.2

- Girdi türü otomatik algılama: IP, domain, URL ve localhost.
- IP/localhost modunda subdomain, DNS brute-force ve Wayback otomatik kapatıldı.
- URL girdisinde “tüm domain” / “sadece bu host” kapsam seçimi eklendi.
- Tarama öncesi kapsam özeti ve onay penceresi eklendi.
- IP adresi + port desteği düzeltildi.

## 1.2.1

- Yetki onayı, geçersiz hedef/payload doğrulamasından sonra sorulacak şekilde düzenlendi.
- MSI paket sürümü 1.2.1’e yükseltildi.

## 1.2.0 — Profesyonel kullanım sürümü

- Hedef alanı artık boş başlıyor; dummy `target.example` kaldırıldı.
- Payload alanı placeholder ile sadeleştirildi.
- Tarama profilleri: **Hızlı / Standart / Derin**.
- Son kullanılan tarayıcı, thread, timeout ve görünüm ayarları kalıcı olarak saklanıyor.
- Çerez bilgisi güvenlik nedeniyle diske yazılmıyor.
- Tarama öncesi yetki onayı eklendi.
- Hakkında ekranında sürüm, Java ve işletim sistemi bilgisi gösteriliyor.
- Raporlara önem, güven seviyesi ve çözüm önerisi eklendi.
- JSON rapor çıktısı eklendi.
- HTML rapor başlığına kapsam/metodoloji bölümü eklendi.
- Tam Tarama ekranına profil, ayar hafızası ve JSON raporu eklendi.
- MSI sürümü **1.2.0** olarak yükseltildi.

## 1.1.0

- Subdomain keşfi, crawler, parameter harvester ve akıllı scanner routing.
- Tam Tarama modu.
- Wayback CDX desteği.
- Teknik listeler.
- Jsoup tabanlı HTML/JS analizi.
- Demo Lab ayrı araç olarak MSI dışına alındı.

## 1.0.0

- Java 17 + Swing + FlatLaf GUI.
- SQLi, LFI, CRLF, XSS ve Open Redirect tarayıcıları.
- HTML/CSV rapor.
- Windows MSI.
