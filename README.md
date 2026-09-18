# SSHDrop 🚀

**SSHDrop** is a secure, modern Android-to-Linux file transfer client built with Jetpack Compose, Material Design 3, and SSHJ. It enables fast, direct file synchronization, uploads, and downloads between your Android device and Linux servers running standard OpenSSH / SFTP daemons.

---

## ✨ Features

- **Standard SSH/SFTP**: Connects seamlessly to standard Linux OpenSSH servers over port 22 (or any custom port) with no special agent or daemon needed on the remote machine.
- **Strict Storage Access Framework (SAF) Compliance**:
  - Zero broad storage or media permissions requested (`READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, etc. are **never** used).
  - Interacts exclusively with Android's system file picker via `ACTION_OPEN_DOCUMENT_TREE` and `ACTION_OPEN_DOCUMENT`.
  - Streams data directly using `ContentResolver` and `DocumentFile`.
- **Foreground-Only Transfers**:
  - No hidden or battery-draining background services.
  - Lifecycle-aware transfers tied to `ViewModel` and `viewModelScope`.
  - Guarded navigation warnings if leaving an active transfer session.
- **Robust Authentication Options**:
  - Password-based authentication.
  - Private key authentication (RSA, ED25519, ECDSA in OpenSSH and PEM formats).
  - Optional passphrase support for encrypted private keys.
  - Secure in-app key importing via the Storage Access Framework.
- **Host Key Verification & Security**:
  - Strict host key fingerprint inspection (SHA-256).
  - First-time connection trust dialog with fingerprint confirmation.
  - Local known-hosts persistence backed by Room database.
- **Dual-Pane Browser Interface**:
  - Side-by-side or quick-toggle view between Android local storage and remote Linux filesystem.
  - Full remote directory management: folder creation (`mkdir`), item deletion, renaming, and hidden file toggle.
  - **Symlink Resolution & Following**: Accurately recognizes Linux symbolic links, displaying link badges, target paths (`➜ /path/to/target`), and following symlink directories seamlessly during navigation and recursive transfers with an optional toolbar toggle.
  - POSIX file attribute and permission mask display (e.g. `lrwxrwxrwx`, `drwxr-xr-x`).
- **Transfer Engine & Conflict Resolution**:
  - Real-time progress monitoring: transfer speed (MB/s), elapsed time, estimated time of arrival (ETA), and progress bars.
  - Recursive directory uploads and downloads preserving folder hierarchies.
  - Customizable conflict policies: **Overwrite**, **Keep Both (Auto-Rename)**, or **Skip**.
  - Detailed transfer history logs with timestamps, byte counts, and status tracking.

---

## 🛠️ Architecture & Tech Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material Design 3 (M3)](https://m3.material.io/)
- **Language**: 100% Kotlin
- **Architecture**: MVVM (Model-View-ViewModel) with Clean Architecture principles
- **SSH & SFTP Engine**: [SSHJ](https://github.com/hierynomus/sshj) (v0.39.0) with [BouncyCastle](https://www.bouncycastle.org/) cryptographic provider
- **Local Persistence**: [Room Database](https://developer.android.com/training/data-storage/room) with Kotlin Symbol Processing (KSP)
  - `ServerEntity`: Saved server connections & credentials
  - `KnownHostEntity`: Cached SSH host keys & SHA-256 fingerprints
  - `TransferHistoryEntity`: Completed & logged transfer records
- **Asynchronous Execution**: Kotlin Coroutines & `StateFlow` / `SharedFlow`
- **Unit & UI Testing**: Robolectric & Roborazzi screenshot testing

---

## 🔒 Permissions Used

SSHDrop strictly follows the principle of least privilege:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

No device storage permissions are declared or requested. File access is handled on demand through Android's system document provider.

---

## 🚀 Getting Started

### Prerequisites

- Android 8.0 (API level 26) or higher.
- A remote Linux host running `sshd` with SFTP enabled (standard default in Debian, Ubuntu, Fedora, CentOS, Arch Linux, etc.).

### Setting Up a Connection

1. **Add Server**:
   - Tap **Add Server** (`+`) on the home screen.
   - Enter a friendly name, remote hostname or IP address, and port (default `22`).
   - Fill in your Linux user account name.
2. **Choose Authentication Method**:
   - **Password**: Enter your remote password.
   - **Private Key**: Tap **Import Private Key** to select your `.pem`, `id_rsa`, or `id_ed25519` key file using Android's file picker. Enter a passphrase if the key is encrypted.
3. **Connect & Trust Host**:
   - Tap **Connect**.
   - On your initial connection, verify the displayed SHA-256 host key fingerprint and tap **Trust & Connect**.
4. **Transfer Files**:
   - Select local files or folders from the **Local Storage** pane and tap **Upload to Server**.
   - Browse remote directories on the **Remote SFTP** pane, select items, and tap **Download to Device**.
   - Monitor real-time throughput and status in the transfer banner.

---

## 🧪 Running Tests

To run local unit and Robolectric tests:

```bash
gradle :app:testDebugUnitTest
```

To verify Roborazzi screenshot tests:

```bash
gradle :app:verifyRoborazziDebug
```

---

## 📄 License

Distributed under the Apache 2.0 License. See `LICENSE` for more information.
