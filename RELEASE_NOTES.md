## Portal v1.0.0

WorldEdit seçimiyle bölge portalı oluşturma eklentisi.

**Uyumluluk:** Minecraft 1.16.5 – 1.21.x · Spigot / Paper / Purpur · Java 8+ · WorldEdit 7.x veya FastAsyncWorldEdit

**Test edildi (gerçek sunucu + gerçek WorldEdit + bot oyuncular):** Paper 1.16.5 (Java 11), Paper 1.21.4 (Java 21), Paper 1.21.11 (Java 21)

### Kurulum
1. `Portal-1.0.0.jar` dosyasını `plugins/` klasörüne atın.
2. WorldEdit/FAWE kurulu olsun (sadece `/portal oluştur` için).
3. Sunucuyu başlatın.

### Komutlar
- `/portal oluştur <ad>` — WorldEdit pos1/pos2 seçiminden portal
- `/portal tphere <ad>` — bulunduğun tam konum + bakış yönü hedef olur
- `/portal düzenle <ad> <pos1|pos2> <x> <y> <z>` — tab'da mevcut koordinatlar önerilir (`~` desteklenir)
- `/portal sil <ad>` · `/portal bilgi <ad>` · `/portal liste` · `/portal yenile`

### Öne çıkanlar
- `portal.<ad>` yetkisi yoksa: uyarı + 3 blok geri atma + savrulma (güvenli konum araması)
- 3 sn ışınlanma bekleme süresi, 3 sn uyarı mesajı bekleme süresi (config'den ayarlı)
- Yetkisi olmayanlar `/portal` komutunu önerilerde hiç göremez
- `portals.yml` atomik kayıt, bozuk dosyada otomatik yedek
- Tüm mesajlar/sesler/süreler `config.yml`'de, HEX renk desteği
