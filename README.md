# KOREA MASTER — Aplikasi Android Online + Offline

Proyek Android ini membungkus HTML Korea Master yang kamu kirim ke dalam aplikasi. Gambar rakun yang kamu kirim dipakai sebagai ikon aplikasi Android.

## Fitur yang disiapkan

- **Langsung bisa offline sejak instalasi pertama.** HTML asli ikut dibundel di APK, jadi tidak perlu membuka Netlify lebih dulu.
- **Materi tetap lokal.** CSS, JavaScript, dataset, dan gambar maskot yang sudah ada di HTML disimpan di dalam aplikasi.
- **Progres tersimpan di perangkat.** Penyimpanan browser lokal diaktifkan; akun pada HTML asli memang akun lokal, bukan akun server.
- **Pembaruan Netlify di background.** Saat internet tersedia, aplikasi memeriksa `https://koreamasterbyrk.netlify.app/` tanpa menahan tampilan belajar. Konten hanya diterima jika masih berbentuk HTML Korea Master yang valid.
- **Update tidak memutus sesi belajar.** Jika sudah ada versi lokal, pembaruan baru disimpan dan diterapkan ketika aplikasi ditutup lalu dibuka lagi.
- **Ekspor kamus JSON.** Tombol ekspor diarahkan ke dialog Android “Simpan sebagai”.
- **Lebih ramah untuk HP.** Tidak memakai framework JavaScript eksternal atau harus mengunduh semua materi setiap kali aplikasi dibuka.
- **APK bisa dibuat lewat GitHub Actions.** Workflow build sudah disertakan.

## Cara membuat APK lewat GitHub

1. Unduh ZIP proyek ini lalu ekstrak di laptop/komputer.
2. Buat repository baru di GitHub.
3. Buka folder hasil ekstrak `KoreaMaster_Android`. Upload **isi foldernya** ke halaman utama repository, supaya `settings.gradle`, `build.gradle`, `app/`, dan `.github/` berada langsung di halaman utama repository. Jangan menaruh semuanya satu tingkat folder terlalu dalam.
4. Pastikan folder `.github/workflows/build-apk.yml` ikut terunggah.
5. Buka tab **Actions** di repository. Pilih **Build Korea Master APK**, lalu tekan **Run workflow**. Build juga berjalan otomatis setelah ada push ke branch `main` atau `master`.
6. Tunggu sampai proses berstatus berhasil/centang hijau. Buka run tersebut, gulir ke bagian **Artifacts**, lalu unduh `KoreaMaster-APK`.
7. Ekstrak ZIP artifact. Di dalamnya ada `app-debug.apk`.
8. Kirim APK itu ke HP Android lalu pasang. Android mungkin meminta konfirmasi untuk mengizinkan pemasangan dari sumber tempat APK dibuka.

## Cara kerja update

- Saat aplikasi dibuka, konten yang tersimpan di HP langsung ditampilkan terlebih dahulu.
- Pengecekan Netlify berjalan di background dengan timeout; gagal koneksi tidak menghalangi belajar.
- Jika server mendukung `ETag` atau `Last-Modified`, aplikasi menggunakan header tersebut untuk menghindari mengunduh ulang konten yang tidak berubah.
- Jika versi situs berubah, HTML baru disimpan di penyimpanan aplikasi. Ketika cache versi lama sudah dipakai saat startup, versi terbaru aktif setelah aplikasi dibuka ulang agar halaman tidak me-reload di tengah belajar.
- Pembaruan diambil hanya dari domain Netlify yang ditetapkan. Jika situs tidak bisa diakses atau respons tidak valid, aplikasi tetap memakai salinan lokal.

## Catatan yang perlu diketahui

- ZIP ini adalah **proyek siap-build**, bukan file APK jadi. GitHub Actions yang membuat APK final.
- Akun dan progres pada HTML asli disimpan lokal di aplikasi/perangkat. Menghapus data aplikasi dapat menghapus progres itu.
- Tombol suara memakai kemampuan Speech Synthesis Android System WebView. Ketersediaan suara Korea offline tergantung pada paket text-to-speech yang ada di HP; kalau tidak tersedia, materi lain tetap berjalan.
- Build pertama GitHub Actions membutuhkan internet untuk mengambil Gradle dan Android SDK. Setelah APK terpasang, materi belajar tidak membutuhkan paket data.
- Bila suatu saat struktur situs Netlify diubah sehingga bukan lagi satu file HTML mandiri, aplikasi menolak respons yang tidak cocok dan mempertahankan versi lokal agar tidak mengganti konten dengan halaman error.
