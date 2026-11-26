# TikIG Saver — TikTok & Instagram Downloader (Android)

Aplikasi Android Native (Kotlin + Jetpack Compose) dengan engine on-device **`yt-dlp` + `FFmpeg`** (`youtubedl-android`) serta ekstraktor slide foto TikTok & Carousel Instagram.

## Fitur Utama
- **Download Video HD (Resolusi & FPS Asli)**: Memilih stream resolusi tertinggi dan FPS tertinggi (misal `1080p @ 60 FPS`) dan melakukan remux MP4 tanpa re-encode agar **60 FPS tetap 60 FPS** (tidak turun ke 30 FPS).
- **Download Video Normal**: Pilihan resolusi standar (`<= 720p`) untuk ukuran file lebih hemat.
- **Download MP3 Saja**: Ekstraksi audio MP3 berkualitas tinggi langsung di HP via FFmpeg on-device.
- **Pemilih Foto / Slide Carousel**: Untuk postingan foto TikTok (`/photo/`) maupun Carousel Instagram (`/p/`), aplikasi menampilkan grid seluruh foto dengan tombol **Download Image** di setiap gambar serta **Download Semua**.
- **Share Langsung dari TikTok & IG**: Tekan tombol *Share* di aplikasi TikTok/Instagram lalu pilih **TikIG Saver**.
- **Verifikasi Resolusi & FPS**: Setelah video selesai diunduh, aplikasi membaca metadata file (`MediaMetadataRetriever` / `MediaExtractor`) untuk menampilkan resolusi & FPS aktual.
- **Login Cookie IG Opsional**: Tersedia WebView login Instagram di tab **Pengaturan** jika ingin mengunduh Reels/Carousel yang membutuhkan sesi akun.

## Cara Build Menjadi APK

### Opsi 1: Otomatis via GitHub Actions (Tanpa Install Android Studio)
1. Upload folder `TikIGDownloader` ini ke repository GitHub baru.
2. Buka tab **Actions** di GitHub -> jalankan workflow **Build Android APK** (otomatis jalan saat push ke `main`/`master` atau lewat tombol **Run workflow**).
3. Setelah selesai, unduh file **`TikIG-Saver-Debug-APK`** dari bagian **Artifacts** dan install `.apk` di HP Android kamu.

### Opsi 2: Build Lokal dengan Android Studio
1. Buka folder `C:\Users\adhit\.gemini\antigravity\scratch\TikIGDownloader` di **Android Studio** (dengan JDK 17 & Android SDK 35).
2. Tunggu Gradle sync selesai, lalu klik **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
3. File APK akan tersedia di `app/build/outputs/apk/debug/app-debug.apk`.
