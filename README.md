# MyMusic Muy 🎧

**MyMusic Muy** adalah aplikasi pemutar musik lokal berbasis Android yang ringan, elegan, dan fokus pada pengalaman visual. Aplikasi ini dirancang untuk memberikan kontrol penuh kepada pengguna atas koleksi musik mereka dengan UI yang modern dan bersih.

## ✨ Fitur Utama
* **Folder Picker:** Pilih folder musik lu secara spesifik tanpa perlu nge-scan seluruh memori HP.
* **Dynamic UI:** Background aplikasi berubah otomatis mengikuti warna dominan cover album (menggunakan Palette API).
* **Mini Player:** Kontrol musik tetap tersedia saat lu lagi nyari lagu lain di daftar putar.
* **Full Screen Player:** Tampilan pemutaran layar penuh dengan kontrol navigasi yang intuitif.
* **Support Multi-Format:** Mendukung `.mp3`, `.m4a`, `.wav`, `.flac`, `.ogg`, dan banyak lagi.
* **LRC Support (In Progress):** Persiapan fitur sinkronisasi lirik otomatis berbasis file `.lrc`.

## 📸 Penampakan UI
Aplikasi ini memiliki dua mode tampilan utama:
1.  **Main View:** Daftar lagu dengan Mini Player di bagian bawah.
2.  **Full Screen Mode:** Tampilan fokus dengan fitur *SAMPUL* dan *LIRIK* yang bisa di-swipe atau diklik manual.

## 🛠️ Teknologi yang Digunakan
* **Language:** Kotlin
* **Architecture:** Lifecycle-aware components (Service, Activity, BroadcastReceiver)
* **Concurrency:** Coroutines & LifecycleScope
* **UI Components:** ViewPager2, RecyclerView, Palette API
* **Storage:** Storage Access Framework (SAF) untuk akses file yang aman di Android terbaru.

## 🔓 Open Source & Lisensi
Tentu saja! Project ini adalah **Open Source**. 

Gue percaya berbagi kode adalah cara terbaik buat belajar bareng. Project ini berada di bawah lisensi **MIT License**. Artinya, lu bebas buat:
* Pake aplikasi ini buat pribadi.
* Modifikasi kodenya sesuka hati.
* Sebarin lagi atau dipake buat project lain.

*Syaratnya cuma satu: Jangan hapus atribusi pembuat aslinya.*

## 🚀 Cara Install (Buat Developer)
1.  Clone repository ini:
    ```bash
    git clone [https://github.com/username/mymusic-muy.git](https://github.com/username/mymusic-muy.git)
    ```
2.  Buka di **Android Studio (Jellyfish ke atas disarankan)**.
3.  Tunggu Gradle Sync selesai.
4.  Run ke emulator atau device fisik (Minimal Android 7.0 / API 24).

---
Dibuat dengan ❤️ oleh **Rycl**.
