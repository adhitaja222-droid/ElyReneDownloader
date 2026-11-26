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
