# Zork on your phone

Zork I, II and III (Infocom, 1980–82), as an Android app or a page you open in a browser.

- **Android app:** every push builds `Zork-N.apk` alongside Drive Player. Open the repo's **Releases** page on your phone and tap the newest `Zork-N.apk`. Installing a newer build over the old one keeps your saved games. The app is in `zorkapp/`: a full-screen WebView around the same page, with its fonts bundled so it works offline.

- `index.html` is the whole thing in one file: interpreter, UI and all three games. Open it in any browser, or copy it to your phone and open it from your files app. It works offline (only the fonts come from the web, and it falls back to system fonts without them).
- Your game is saved after every move, so you can close the page and tap **Continue** later. The in-game `save` / `restore` commands use one extra slot per game. Saves live in that browser's storage only.

## How it's built

- `src/zmachine.js` is a small Z-machine (version 3) interpreter with no UI of its own.
- `src/page.html` is the phone UI: status bar, scrolling transcript, direction and command buttons, and a command line.
- `stories/*.z3` are the original compiled story files from Microsoft's MIT-licensed release of the source code ([historicalsource/zork1](https://github.com/historicalsource/zork1), `zork2`, `zork3`). See `stories/LICENSE`.

After changing anything in `src/`, rebuild `index.html` and the app's copy in `zorkapp/src/main/assets/` with:

```
python3 zork/build.py
```
