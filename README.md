# e-Toll Tracker Indonesia

Kotlin + Jetpack Compose + Room (MVVM). Bahasa Indonesia, terang/gelap otomatis, tanpa internet.
**Catatan: kode ini belum pernah dikompilasi atau diuji di perangkat oleh pembuatnya.**

## Cara build dari HP (GitHub Actions)
1. Buat repo kosong di github.com, lalu di Termux (dari F-Droid): `pkg install git unzip`
2. `unzip etoll-tracker-android.zip && cd etoll`
3. `git init -b main && git add . && git commit -m init`
4. `git remote add origin https://github.com/USERNAME/REPO.git && git push -u origin main`
   (login memakai Personal Access Token GitHub sebagai password)
5. Buka tab **Actions** → workflow *Build APK* → unduh artifact `etoll-tracker` → pasang APK (`app-debug.apk`).
Jika build gagal, kirim log error-nya ke Claude untuk diperbaiki.

## Build di PC
Android Studio → Open → Build > Build APK(s). Atau `gradle assembleDebug` (Gradle 8.7, JDK 17).
Release: set env `KS_PATH KS_PASS KS_ALIAS KS_KEYPASS` lalu `gradle assembleRelease bundleRelease`.
Ikon masih ikon sistem sementara; ganti sebelum publikasi Play Store.

## Status fitur
| Fitur | Status |
|---|---|
| Deteksi NFC (tidak ada / nonaktif / aktif) | Berhasil (implementasi, belum diuji perangkat) |
| Deteksi kartu (teknologi + UID) | Berhasil |
| Baca saldo Mandiri/Flazz/Brizzi/TapCash | **Terbatas** — butuh SDK/izin resmi penerbit; belum ada adapter |
| Gerbang masuk/keluar & tarif dari kartu | **Belum tersedia** (tidak ada sumber resmi); input manual tersedia |
| Impor riwayat resmi | Belum tersedia |
| GPS perkiraan lokasi | Belum tersedia (sengaja tidak diaktifkan) |
| Snapshot saldo, deteksi perubahan, klasifikasi pengguna | Berhasil |
| Rumus saldo & selisih, anti-duplikat | Berhasil |
| CRUD catatan, filter, pencarian | Berhasil (filter periode: semua/hari ini/7 hari/bulan ini; tanpa pilih tanggal bebas) |
| Grafik harian/bulanan, rata-rata per perjalanan | Berhasil |
| Ekspor CSV/PDF | Berhasil |
| Data demo, hapus semua data | Berhasil |
| Room/MVVM, offline | Berhasil |
| APK debug / AAB | Via GitHub Actions; belum dibuild |
