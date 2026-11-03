# Signal Station (Android)

A 55-channel OSINT console for Android. The whole app is one web page (`web/index.html`) wrapped in a small Kotlin WebView shell, so you edit tools in one file and both the web and Android versions update.

## What the Android shell adds
- **Live lookups everywhere.** Pages are served from a real https origin, so DNS, RDAP, crt.sh, Shodan InternetDB, RIPEstat, Wayback, Nominatim, GitHub, Reddit and the other public APIs run inline.
- **Share-to-pivot.** Share a link, IP or email from any app → *Signal Station* → it lands in the pivot bar with matching tools suggested.
- **Real file pickers** for EXIF, ELA, QR, hashing and image comparison.
- **Exports save to Downloads/SignalStation** (case reports as Markdown/JSON, scrubbed photos).
- **External links open in your browser** or the matching app, never inside the console.
- Back button closes an open tool before leaving the app.

## Get an APK without installing anything (recommended)
1. Create a new GitHub repository and upload this folder's contents (keep the `.github` folder).
2. GitHub runs **Actions → Build APK** automatically on every push (or run it by hand from the Actions tab).
3. When it finishes (~4 minutes), open the run and download **signal-station-apk**. Unzip it to get `app-debug.apk`.
4. Copy it to your phone, open it, and allow "Install unknown apps" for your file manager when asked.

## Build in Android Studio
1. Run `bash scripts/build-www.sh` (copies `web/index.html` into the app's assets; already done once).
2. Open this folder in Android Studio (Koala or newer). If it asks for a Gradle wrapper, choose to use Gradle 8.9 or run `gradle wrapper --gradle-version 8.9` once.
3. Plug in your phone with USB debugging on and press **Run**.

## Editing tools
Every channel is one `tool({...})` block in `web/index.html`: an id, band, fields, and an async `run(values, out)` function. `out` has helpers: `kv`, `table`, `links`, `pre`, `note`, `warn`, `err`, `img`, `meter`. Network tools can add `alt(values)` returning fallback links. After editing, rerun `scripts/build-www.sh`.

## Libraries (loaded from jsDelivr at runtime)
exifr 7.1.3 (EXIF), jsQR 1.4.0 (QR), libphonenumber-js 1.10.51 (phone parsing). Everything else is written in-file.

## Responsible use
Built for public-information research, security work, journalism and self-auditing. Don't use it to stalk, harass or expose private people.
