# Portal

WorldEdit seçimiyle bölge portalı oluşturma eklentisi. **Minecraft 1.16.5 – 1.21.x** (Spigot / Paper / Purpur), Java 8+.

Her sürüm GitHub Actions üzerinde **gerçek Paper 1.16.5 ve 1.21.4 sunucularında, gerçek WorldEdit ve bot oyuncularla** test edilir.

## Kurulum

1. [Releases](../../releases) sayfasından `Portal-x.y.z.jar` indirin, `plugins/` klasörüne atın.
2. WorldEdit 7.x **veya** FastAsyncWorldEdit kurulu olsun (sadece `/portal oluştur` için gerekir).
3. Sunucuyu başlatın. `plugins/Portal/config.yml` ve `portals.yml` oluşur.

## Komutlar

| Komut | Açıklama | Yetki |
|---|---|---|
| `/portal oluştur <ad>` | WorldEdit pos1/pos2 seçiminden portal oluşturur | `portal.create` |
| `/portal tphere <ad>` | Bulunduğun tam konum + bakış yönünü hedef yapar | `portal.tphere` |
| `/portal düzenle <ad> <pos1\|pos2> <x> <y> <z>` | Köşe koordinatını değiştirir (`~`, `~5` desteklenir) | `portal.edit` |
| `/portal sil <ad>` | Portalı kalıcı olarak siler | `portal.delete` |
| `/portal bilgi <ad>` | Koordinatlar + hedef (sadece yazan görür) | `portal.info` |
| `/portal liste` | Tüm portallar | `portal.info` |
| `/portal yenile` | config.yml ve portals.yml'i yeniden yükler | `portal.reload` |

`oluştur/olustur`, `düzenle/duzenle` ikisi de çalışır. Tüm argümanlarda tab tamamlama vardır; `düzenle` komutunda x/y/z önerisi portalın **mevcut** koordinatıdır.

## Yetkiler

- `portal.<portalAdı>` — o portalı kullanma (ör. `portal.deneme`). Yoksa uyarı alır, 3 blok geri atılır ve savrulur.
- `portal.admin` — tüm komutlar (+ `admin-bypass: true` ise tüm portallar).
- Komut yetkisi olmayan oyuncular `/portal` komutunu sohbet önerilerinde **hiç göremez**.

## Davranış

- Işınlanma bekleme süresi (varsayılan 3 sn): ard arda portala girişi engeller.
- Uyarı mesajları bekleme süresi (varsayılan 3 sn): spam engeli.
- Geri atma güvenli konum arar (duvar, lav, ateş, başka portal içine atmaz).
- Tüm değişiklikler anında `portals.yml`'e atomik olarak yazılır; dosya bozulursa yedeği alınır.
- Tüm mesajlar, sesler ve süreler `config.yml`'den ayarlanır (HEX renk `&#RRGGBB` desteklenir).

## Derleme

```bash
mvn clean package        # target/Portal-1.0.0.jar
```
