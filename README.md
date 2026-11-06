# RavenOS

![Android 11](https://img.shields.io/badge/Android-11-3DDC84?logo=android&logoColor=white)
![LineageOS 18.1](https://img.shields.io/badge/LineageOS-18.1-167C80?logo=lineageos&logoColor=white)
![Device](https://img.shields.io/badge/device-Echo%20Show%208%20(2019)-232F3E)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

A custom Android build for the 1st gen Amazon Echo Show 8 (2019, codename `crown`). It removes Alexa and turns the Echo into a clock, weather display, Bluetooth speaker and offline voice assistant.

<p align="center">
  <img src="docs/images/home.png" alt="The home screen with the clock, weather and shortcut buttons" width="860">
</p>

> [!TIP]
> This repo is the `vendor/ravenos` folder of a LineageOS 18.1 source tree. Clone it into `vendor/ravenos`, not on its own.

> [!IMPORTANT]
> Installing RavenOS uses the community amonet exploit. If it's interrupted, the Echo can be bricked for good. It also erases Fire OS and everything on the device. Only the 1st gen (2019) Echo Show 8, model C7H6N3, is supported. You do this at your own risk.

**[What it does](#what-it-does)** · **[What you need](#what-you-need)** · **[Install](#install)** · **[How to use it](#how-to-use-it)** · **[Help](#help)**

## What it does

- **Home screen.** A clock over a looping video of your choice. Tap it for weather, alarms, timers, what's playing and shortcuts. Also has a night mode and a device health page.
- **Bluetooth speaker.** The Echo shows up as "RavenOS" and shows your phone's album art.
- **Speaker tuning.** Bass, Mids and Treble sliders for the built-in speaker. The rear jack has no volume limit, so a line-in amp gets full level.
- **"Hey Raven" voice control.** Wake word, speech recognition and speech all run on the Echo, with no internet. Set reminders, alarms, timers and a sleep screen, or ask for the weather.
- **Portal TV remote.** The D-pad works everywhere, and every button except Power can be changed on the Echo.
- **Dark glass theme.** Settings, the keyboard, dialogs and app icons share one look. Ten colour variants are included.
- **No accounts or tracking.** Weather from Open-Meteo is the only regular internet request.

<table>
  <tr>
    <td><img src="docs/images/settings.png" alt="Settings in the glass theme"></td>
    <td><img src="docs/images/apps.png" alt="All apps with themed icons"></td>
  </tr>
  <tr>
    <td align="center">Settings</td>
    <td align="center">All apps</td>
  </tr>
  <tr>
    <td><img src="docs/images/speaker.png" alt="Speaker tuning sliders in Sound settings"></td>
    <td><img src="docs/images/remote.png" alt="The Remote buttons page"></td>
  </tr>
  <tr>
    <td align="center">Speaker tuning</td>
    <td align="center">Remote buttons</td>
  </tr>
</table>

## What you need

- A 1st gen (2019) Echo Show 8, model C7H6N3, and a micro-USB data cable
- A Linux PC (Ubuntu or Debian) with about 300 GB free and 16 GB or more of RAM
- `repo`, `adb`, `fastboot`, `python3`, `python3-serial` and `dos2unix`
- The amonet package `amonet-crown-v2.0.1.zip` from the XDA thread linked in [FLASHING.md](FLASHING.md)

## Install

1. Download LineageOS 18.1 with the amazon-oss device files:

   ```bash
   mkdir -p crown && cd crown
   repo init -u https://github.com/LineageOS/android.git -b lineage-18.1 --depth=1
   git clone https://github.com/amazon-oss/local_manifests.git -b lineage-18.1 .repo/local_manifests
   repo sync -c --no-clone-bundle --no-tags -j$(nproc --all)
   ```

2. Add RavenOS and apply the patches. The amazon-oss patches go first:

   ```bash
   git clone https://github.com/baverrr/RavenOS.git vendor/ravenos
   mkdir -p vendor/extra
   echo '-include vendor/ravenos/BoardConfigRavenOS.mk' > vendor/extra/BoardConfigExtra.mk
   bash patches/apply.sh
   bash vendor/ravenos/patches/apply.sh
   ```

   RavenOS's `apply.sh` skips patches already in place, so you can fix a conflict and run it again.

3. Build it. This makes `out/target/product/crown/RavenOS-<version>-<date>-crown.zip` and a `.sha256` file:

   ```bash
   nice -n 18 ionice -c3 bash vendor/ravenos/build-ravenos.sh
   ```

4. Flash it. Follow [FLASHING.md](FLASHING.md) step by step: unlock with amonet, install TWRP, wipe, then sideload the zip over USB.

<details>
<summary><b>Build options</b></summary>

| Option | What it does |
|---|---|
| `build-ravenos.sh RavenHome` | Builds just that one module |
| `RAVENOS_GLASS_THEME=tide` | Picks another theme. Default is `obsidian`. Names are the folders in [themes/](themes) |
| `JOBS=12` | Parallel build jobs (default 6) |

The script uses ccache if it's installed, and stops early if a RavenOS patch is missing, such as after a `repo sync`. Each build gets its own `rvYYMMDDHHMM` number so Android refreshes its app cache after a dirty flash.
</details>

## How to use it

1. **Set up voice.** On first boot, open RHVoice once and download an English voice so "Hey Raven" can talk.
2. **Add a background.** Copy an `.mp4` to `/sdcard/Movies/RavenOS/`, for example `adb push clip.mp4 /sdcard/Movies/RavenOS/`. See [media/backgrounds](media/backgrounds/README.md).
3. **Talk to it.** Say "Hey Raven", then "wake me up at seven" or "go to sleep". The full list is in [VOICE.md](VOICE.md#what-you-can-say).
4. **Change remote buttons.** Go to Settings › Connected devices › Remote buttons. Power can't be changed.

<details>
<summary><b>Connecting an amp to the rear jack</b></summary>

Use a 3.5 mm stereo to 2×RCA cable into the amp's AUX, line or CD input (never phono). Set the Echo to about 80 to 90% volume and the amp to the loudest you'd ever want, then control volume from the Echo or the remote. The built-in speaker and its tuning turn off while the jack is in use. If you hear hum, power both from the same strip or add a ground loop isolator.
</details>

## Help

**Apps show wrong labels or colours after flashing.** Make sure the build has a new `rv…` build number. Dirty flashes need one.

**"Hey Raven" answers on screen but doesn't speak.** Open RHVoice and download an English voice.

**A remote button change does nothing.** Check that Remote buttons is on in Settings › Accessibility.

**The flash stops with "system app updates in /data".** Run `adb uninstall <pkg>` for each listed app (this keeps the system copy), then flash again.

**The Echo won't boot after a flash.** Hold the buttons for TWRP and see [FLASHING.md › Recovery](FLASHING.md#recovery).

**Things to know.** It's a `userdebug` build with test keys, and adb is on, including over Wi-Fi on port 5555. Keep the Echo on a network you trust. There's no `/data` encryption, and flashing needs USB because TWRP has no Wi-Fi.

<details>
<summary><b>For developers</b></summary>

- Install guide: [FLASHING.md](FLASHING.md). Voice: [VOICE.md](VOICE.md). Theme: [THEME.md](THEME.md)
- Source changes live as `git am` patches in [patches/](patches), in folders that mirror the project they change. Export a new one with `git format-patch -1 -o "$PWD/vendor/ravenos/patches/<project>" --start-number <next>`
- Launcher, settings, speaker tuning and remote service: [apps/RavenHome/](apps/RavenHome)
- Retrain the wake word: [tools/wakeword/](tools/wakeword) (Docker, no recordings needed)
- Every flashed build is logged in [CHANGELOG.md](CHANGELOG.md)
</details>

## License

RavenOS's own code, overlays and patches are under the [Apache License 2.0](LICENSE). Bundled apps keep their own licenses: [Dicio](https://github.com/Stypox/dicio-android) (GPL-3.0, patched in `apps/Dicio/patches`), [RHVoice](https://github.com/RHVoice/RHVoice) (LGPL-2.1 and GPL, the F-Droid build), the openWakeWord tooling (Apache 2.0), and IBM Plex Sans fonts (SIL OFL 1.1, see [FONTS-LICENSE.txt](apps/RavenHome/FONTS-LICENSE.txt)).

RavenOS is not made by or affiliated with Amazon or LineageOS.
