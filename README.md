# Drive Player

An Android audiobook player for audio files in your Google Drive. It remembers where you left off in every book.

- **Folders are books.** Pick a Drive folder and its audio files (including subfolders) become chapters, sorted naturally so "Chapter 2" comes before "Chapter 10". You can also play a single file on its own.
- **Picks up where you left off.** It saves your chapter and position every few seconds, and whenever you pause, seek or change chapter. It also remembers your speed for each book. Everything is stored on the phone.
- **Streams from Drive**, or download a book for offline listening (the ⬇ button).
- **Speed** from 0.5× to 3×, **30-second skip back/forward** (in the app, the notification and the lock screen), and **bookmarks**.
- Background playback, lock-screen and Bluetooth controls, and it pauses when headphones are unplugged. Pressing play on Bluetooth after a reboot resumes the last book.
- Formats: MP3, M4A/M4B/AAC, OGG/Opus, FLAC and WAV.

## One-time setup

You do these steps once. After that, every push builds a new APK.

### 1. Add the signing key to GitHub

Google identifies the app by its signing key, so every build must be signed with the same key. The key is kept out of this public repo and stored as a GitHub secret.

1. Open the repo on GitHub, then go to **Settings → Secrets and variables → Actions → New repository secret**.
2. Name: `SIGNING_KEYSTORE_BASE64` (a secret named `MP3` also works)
3. Value: the entire contents of `SIGNING_KEYSTORE_BASE64.txt`, which Claude provided. Keep a private backup of that file too. If you lose the key, you'll need a new one, which means repeating step 2 and reinstalling the app.

The key's fingerprint, which you'll need in step 2, is:

```
SHA-1: 66:8A:10:39:A0:8F:CF:0F:1B:A9:F1:4B:8A:5F:30:58:4B:AE:94:18
```

### 2. Create a Google Cloud OAuth client (free)

1. Go to <https://console.cloud.google.com/> and create a project, for example "Drive Player".
2. Enable the Drive API: search for **Google Drive API** with the 🔍 at the top, open it, and tap **Enable**.
3. Open **Google Auth Platform** (in the ☰ menu, or search "OAuth") and tap **Get started**:
   - **App information:** name it "Drive Player" and enter your email as the support email.
   - **Audience:** choose **External**.
   - **Contact information:** your email.
   - **Finish:** accept the policy, then tap **Create**.
4. **Audience → Test users → + Add users:** add your own Google account. Leave the publishing status on **Testing**.
5. **Data Access → Add or remove scopes:** tick `.../auth/drive.readonly` (or paste `https://www.googleapis.com/auth/drive.readonly` under "Manually add scopes"), then tap **Update → Save**.
6. **Clients → + Create client:**
   - Application type: **Android**
   - Package name: `com.ejl191.driveplayer`
   - SHA-1 certificate fingerprint: the SHA-1 from step 1
   - Tap **Create**. You don't need to copy anything into the app. Google matches the app by package name and fingerprint.

When you sign in, Google will warn that the app isn't verified. That's expected for a personal app in Testing mode. Tap **Continue**.

### 3. Build and install

1. Once the secret is set, go to **Actions → Build APK → Run workflow**. Pushing any commit also starts a build.
2. When it finishes, open **Releases** on your phone (`https://github.com/ejl191/mp3/releases`) and tap the newest `DrivePlayer-N.apk`.
3. Allow your browser to install unknown apps when Android asks.

To update later, install a newer release over the old one. Your library and positions are kept.

## Zork app

Each build also produces `Zork-N.apk`, a separate app with Zork I, II and III (the original Infocom games, released by Microsoft under the MIT license). Install it from the same Releases page. It works offline, saves your place after every move, and doesn't need any of the Google setup above. See [`zork/README.md`](zork/README.md).

## Using it

1. **Sign in with Google**, then tap **Add from Drive**.
2. Browse **My Drive** or **Shared with me**, or search.
   - Tap the ▶ next to a folder, or **Play this folder as a book** inside one, to add it as a book.
   - Tap an audio file to play it on its own.
3. The book appears in your **Library**. Tapping it later resumes where you stopped.
4. If you add files to a Drive folder later, use **⋮ → Refresh from Drive** on that book.

## Notes and limitations

- Positions and bookmarks are stored on the phone only. Uninstalling the app or clearing its data erases them.
- Chapter markers inside a single M4B file aren't shown. Use the 30-second skip and the seek bar. Multi-file books get a full chapter list.
- Drive access is read-only. The app never changes your files.

## Project layout

```
app/src/main/java/com/ejl191/driveplayer/
  drive/      Google sign-in (Play services AuthorizationClient) and Drive REST calls
  data/       Room database: books + saved position, tracks, bookmarks
  playback/   Media3 PlaybackService (ExoPlayer + MediaSession), offline downloads (WorkManager)
  ui/         Jetpack Compose screens: Library, Browse, Player
```

To build locally with Android Studio, put the keystore at `keystore/driveplayer.jks` (it's gitignored) and run the app.
