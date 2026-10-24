# Catatku

Catat ide + simpan link, gaya chat WhatsApp. Kategori via `#tag`. Sync ke Cloudflare (Worker + D1).

## Fitur
- Ketik → kirim, jadi bubble. `#tag` otomatis jadi kategori, bisa diklik.
- Link otomatis bisa diklik.
- Chip filter: 📌 Pin, 🔗 Link, semua tag (urut paling sering).
- Filter tag aktif → catatan baru otomatis ikut tag itu.
- Saran tag saat ketik `#`. Tombol `#` di kolom input.
- Tahan bubble: Edit / Salin / Pin / Bagikan / Hapus (bisa Batal).
- Share dari app lain (Chrome, YouTube, TikTok, Shopee…) → pilih **Catatku**.
- Cari teks. Pemisah hari (Hari ini / Kemarin).
- Offline dulu, sync otomatis (tiap simpan + tiap 30 menit). ⏱ = belum sync, ✓ = sudah.
- Export JSON.

## 1. Backend (sekali saja)
D1 `catatku` sudah dibuat + tabel sudah ada.

```bash
cd backend
npx wrangler login
npx wrangler deploy
npx wrangler secret put API_TOKEN     # isi token rahasia bebas, panjang
```
Catat URL hasil deploy: `https://catatku-api.<akun>.workers.dev`

Tanpa terminal: Dashboard Cloudflare → Workers → Create → paste `src/index.js` →
Settings → Bindings → D1 → nama `DB` → pilih `catatku` → Variables → Secret `API_TOKEN`.

## 2. APK
**Cara A — GitHub (tanpa Android Studio):**
1. Buat repo private, push folder ini ke branch `main`.
2. Tab Actions → tunggu "Build APK" hijau (~5 menit).
3. Releases → `catatku.apk` → download di HP → install.

**Cara B — Android Studio:** Open folder → Run.

## 3. Di HP
Buka app → isi URL Worker + Token → Simpan & Sync. Install di HP lain, isi sama → data ikut.
