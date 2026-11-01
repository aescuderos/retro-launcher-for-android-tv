# Retro Launcher for Android TV

A tiny, dependency-free front-end for [RetroArch](https://www.retroarch.com/) on Android TV.
Systems are listed in a menu on the right; the games of the selected system are shown on the left as a grid of box art.
Pick a game and it launches straight into RetroArch with the right core. No scraping, no database, no extra libraries.

## Features

- Systems list (right) + box-art grid (left), designed for a TV remote or gamepad.
- Launches RetroArch directly with the correct libretro core and your RetroArch config.
- Remembers the last system you used.
- Selection resets to the first game when you change system; no stale highlight is shown while the systems menu has focus.
- Gamepad shortcuts: **A** select, **B** back (grid → systems → exit), **L1 / R1** previous/next system.
- Fast: bitmaps are decoded on a small thread pool with a 48 MB LRU cache.
- Pure Java (Android framework only), tiny APK. `minSdk 21`, shows up in the Leanback launcher (with banner).

## Requirements

- An Android TV / Android device with **RetroArch** installed (package `com.retroarch`) and the libretro cores you need downloaded.
- ROMs (and BIOS files where required) on internal storage or an SD card.
- A `library.json` file plus box-art thumbnails (see below).

## How it works

On start the app looks for `library.json` in each of the app's external files directories (`getExternalFilesDirs`), i.e.

```
/sdcard/Android/data/com.gamefront/files/library.json
/storage/<SD-CARD-ID>/Android/data/com.gamefront/files/library.json
```

Thumbnails are read from a `thumbs/` folder next to it. Launching a game fires an explicit intent to
`com.retroarch/com.retroarch.browser.retroactivity.RetroActivityFuture` with the extras `ROM`, `LIBRETRO` and `CONFIGFILE`
(`/sdcard/Android/data/com.retroarch/files/retroarch.cfg`).

> The package id is `com.gamefront` for historical reasons; the app label is "Retro Launcher".

### `library.json`

```json
{
  "systems": [
    {
      "name": "Super Nintendo",
      "core": "/data/user/0/com.retroarch/cores/snes9x_libretro_android.so",
      "games": [
        ["ActRaiser", "/storage/ABCD-1234/Android/media/com.retroarch/roms/snes/ActRaiser.zip",
         "Nintendo - Super Nintendo Entertainment System/Named_Boxarts/ActRaiser.png"]
      ]
    }
  ]
}
```

Each game is `[title, absolute ROM path on the device, thumbnail path relative to thumbs/]`.
An empty thumbnail path shows a plain placeholder tile.

### Thumbnails

Use the [libretro-thumbnails](https://github.com/libretro-thumbnails) naming:
`thumbs/<System name>/Named_Boxarts/<ROM name without extension>.png`.

### Generating the library

`tools/make_library.py` scans a ROM folder and writes `library.json` (edit the `SYSTEMS` table at the top to match your folders and cores):

```bash
python3 tools/make_library.py /storage/ABCD-1234/Android/media/com.retroarch/roms \
    /path/to/roms/on/pc /path/to/thumbs/on/pc library.json
```

Arguments: ROM root as seen **on the device**, ROM root on the PC, thumbs root on the PC, output file.

## Build

No Gradle needed. You need JDK 17 and the Android SDK (platform `android-33`, build-tools `33.x`):

```bash
ANDROID_SDK=~/Android/Sdk ./build.sh 1 1.0     # -> out/retro-launcher.apk
```

The script creates a throw-away keystore in `~/.android/retro-launcher.jks` if you don't have one.

## Install and deploy

```bash
adb connect <tv-ip>:5555
adb install -r out/retro-launcher.apk
adb push library.json /sdcard/Android/data/com.gamefront/files/library.json   # or the SD-card path above
adb push thumbs /sdcard/Android/data/com.gamefront/files/thumbs
```

Then open **Retro Launcher** from the TV launcher. If you use a custom home screen, add `com.gamefront` to it.

## RetroArch tips (TV boxes)

- Heavy shaders such as `crt-pi` can drop low-end SoCs below 60 FPS and cause crackling audio. If you see that, enable `fps_show` and try a simple scanline shader or none.
- For gamepad lag: `video_threaded = false`, `video_max_swapchain_images = 2`, and keep `audio_latency` around 64–96.
- Set `input_menu_toggle_gamepad_combo = 2` (Start+Select) so you can always open the RetroArch menu from a gamepad.

## Project layout

```
AndroidManifest.xml
src/com/gamefront/MainActivity.java   the whole app
res/                                  icon and TV banner
tools/make_library.py                 library.json generator
build.sh                              Gradle-less build script
```

## License

MIT. RetroArch and libretro cores are separate projects under their own licenses. You are responsible for the legality of the ROMs you use.
