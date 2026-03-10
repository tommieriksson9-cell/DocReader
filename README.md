# DocuMate 📄

A full-featured Android document reader and editor supporting **PDF**, **DOCX**, **XLSX**, and **TXT** files.

## Features

| Feature | PDF | DOCX | XLSX | TXT |
|---------|-----|------|------|-----|
| View    | ✅  | ✅   | ✅   | ✅  |
| Edit    | ❌  | ✅   | ✅   | ✅  |
| Create  | ❌  | ✅   | ✅   | ✅  |
| Share   | ✅  | ✅   | ✅   | ✅  |

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM + LiveData + Coroutines
- **Libraries**:
  - [Apache POI 5.2.3](https://poi.apache.org/) — DOCX & XLSX read/write
  - [AndroidPdfViewer](https://github.com/barteksc/AndroidPdfViewer) — PDF rendering
  - [iTextG](https://itextpdf.com/) — PDF creation
  - Material Design 3

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Clone and Build

```bash
git clone https://github.com/YOUR_USERNAME/DocuMate.git
cd DocuMate
./gradlew assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Open in Android Studio

1. `File → Open` → select the `DocuMate` folder
2. Wait for Gradle sync to complete
3. Hit **Run** (▶️) or press `Shift+F10`

## Project Structure

```
DocuMate/
├── app/src/main/
│   ├── java/com/documate/app/
│   │   ├── data/
│   │   │   ├── model/          # DocumentFile, DocumentType
│   │   │   └── repository/     # DocumentRepository
│   │   ├── ui/
│   │   │   ├── home/           # MainActivity, ViewModel, Adapter
│   │   │   ├── viewer/         # PDF + TXT viewer
│   │   │   └── editor/         # DOCX + XLSX editors
│   │   └── utils/
│   │       ├── DocxHandler.kt  # Apache POI DOCX logic
│   │       └── XlsxHandler.kt  # Apache POI XLSX logic
│   └── res/                    # Layouts, drawables, strings
└── .github/workflows/build.yml # CI: auto-build APK on push
```

## GitHub Actions CI

Every push to `main` or `develop` automatically:
1. Builds a debug APK
2. Uploads it as a downloadable artifact (kept 14 days)
3. Runs unit tests

Find your APK under **Actions → latest run → Artifacts**.

## Roadmap

- [ ] PDF annotation support
- [ ] Google Drive / Dropbox integration
- [ ] Dark mode
- [ ] Search within documents
- [ ] Recent files list

## License

MIT
