# Mi Explorer (Xiaomi MIUI / HyperOS File Manager)

A modern, high-performance Android File Explorer inspired by Xiaomi's iconic **Mi File Manager (MIUI / HyperOS)**, built from scratch using Kotlin, Jetpack Compose, Material Design 3, and Coroutines.

## Key Features

- **Dual-Tab Experience**:
  - **Recent Tab**: Clean chronological timeline of recently created, captured, and downloaded files grouped into "Today", "Yesterday", and "Earlier", with quick filter chips (All, Images, Docs, APKs, Archives, Music).
  - **Storage Tab**: Streamlined storage capacity overview ("Internal storage: X GB free of Y GB") with quick clean shortcut.
- **8 Signature MIUI Category Squircles**:
  - **Images** (Cyan/Blue)
  - **Videos** (Vibrant Violet)
  - **Docs** (Warm Yellow)
  - **Music** (Coral Red)
  - **APKs** (Android Green)
  - **Downloads** (Sky Blue)
  - **Archives** (Golden Amber)
  - **Cleaner** (Mint Emerald)
- **Deep Clean & Storage Optimizer**:
  - Scans for temporary cache files, obsolete APK packages, large files (>15MB), and empty directories.
  - One-tap cleanup with animated optimization feedback.
- **Transfer to PC (Wireless FTP)**:
  - Built-in wireless transfer mode with IP/Port configuration (`ftp://...`) allowing direct cable-free management from PC browsers or Windows File Explorer.
- **File Management Operations**:
  - Path navigation with interactive breadcrumbs.
  - Selection mode, Copy, Cut, and Paste via floating clipboard bar.
  - Create new folders and files.
  - Batch deletion and file renaming.
  - ZIP compression and archive extraction.
  - File properties & details inspection.
- **Built-in Viewers**:
  - **Text Editor**: Plain text, code, markdown, and JSON editor with line numbering and save functionality.
  - **Photo Viewer**: Full-screen preview with metadata inspection and navigation.
  - **APKs & Apps Manager**: Listing installed applications with APK size and system settings shortcuts.
- **100% Free**:
  - Zero billing libraries, no in-app purchases, completely clean.

## Architecture & Technology Stack

- **Target Runtime**: Android (JDK 21, Android SDK 36, AGP 9.1.1, Gradle 9.3.1)
- **UI Framework**: Jetpack Compose with Material Design 3
- **Language**: Kotlin 2.2.21
- **Package Name / Application ID**: `com.pkstudio.miexplorer.app`
- **Namespace**: `com.mi.explorer`
