# RavenOS flash log

Every build flashed to the Echo, newest first, with what went into it. Each build is named by
its fingerprint's build number (`rvYYMMDDHHMM`, the time the build started). Check which build
the Echo runs with:

```bash
adb shell getprop ro.build.fingerprint
```

## How changes get into a build

One session coordinates the project: **Ravenos build cont 1** (in the Claude app). It owns the
build lock and the Echo.

- Other sessions send it a change (repo, commit, patch file, one line on what it does) instead of
  building or flashing themselves.
- It runs every `build-ravenos.sh` and every flash, one at a time.
- It adds each change to **Next flash** below. When a flash succeeds, it moves that list into a
  new entry under its build number.
- Changes made only on the Echo (settings, files pushed over adb) are listed too, under the flash
  they followed, since a fresh install won't have them.

## Next flash

Nothing yet.

## Flashes

### rv2611031909 (3 Nov, 19:09)

- **No headphone volume limit** (`overlay/frameworks/base/core/res/res/values/config.xml`):
  `config_safe_media_volume_enabled` is false, so an amp on the rear jack can go to full volume
  without the "listening at high volume" warning holding it to about 40 %.
  Checked: `mSafeMediaVolumeState=SAFE_MEDIA_VOLUME_DISABLED`.

### rv2611031900 (3 Nov, 19:00)

- **No encryption in Settings** (packages/apps/Settings `0018-Settings-Hide-encryption-where-the-device-can-t-encr.patch`):
  with `ro.crypto.state=unsupported`, the Encrypt tablet item is hidden and Security ›
  Encryption & credentials is now **Credential storage**. Checked on the Echo.

### rv2611031720 (3 Nov, 17:20)

- Call volume removed from Settings › Sound; the Echo doesn't make calls. (RavenOS Settings
  overlay)
- Remote buttons page in Settings › Connected devices: give any Portal TV remote button except
  Power another action (go home or back, D-pad moves, play/pause, next or previous track,
  volume, mute, notifications, quick settings, screen off, Alarms & timers, voice assistant,
  RavenOS settings, or open any app), right on the Echo. Red, Green and Blue are now mapped so
  they can be given one too. A key-filter service in RavenHome carries the choices out; it
  only runs while a button is changed and only touches this remote. (RavenHome, keylayout)
- All apps icons redrawn in the RavenOS look, automatically for every app including ones
  installed later: a dark glass tile with a faint amber glow and the app's symbol in a warm
  white-to-amber gradient. The symbol is picked out of the app's own icon (its foreground,
  or whatever stands out from its background colour) and shaded by its own light and dark
  parts; an icon with no clear symbol becomes a two-tone version in the same colours. Files
  and Camera use hand-drawn folder and camera symbols. (RavenHome)

Flashed on the way, to tune the icons: rv2611031647 (first try: two-tone Camera and Files, small
gear), rv2611031656, rv2611031704 and rv2611031712.

Checked after this flash: Call volume is gone from Sound; All apps shows amber camera, folder
and gear symbols on glass tiles; Remote buttons is at the bottom of Connected devices and lists
every button with its default, and a button opens its action list. Not yet checked: a changed
button with the real remote (adb key presses don't come from the remote).

### rv2611031616 (3 Nov, 16:16)

- Navigation bar has Back and Home only; the Recents button is gone. The first try
  (rv2611031606) only changed the phone layout; the Echo uses the tablet one, now changed
  too. (RavenOS SystemUI overlay)

Checked after this flash: the bar shows only Back and Home.

### rv2611031606 (3 Nov, 16:06)

- Speaker tuning for the built-in speaker, like Fire OS's: fuller bass, a less boxy and harsh
  midrange, a little treble, and a limiter so loud bass doesn't distort. Off while sound goes
  to headphones, the audio jack, USB or Bluetooth. Settings › Sound › Speaker tuning turns it
  off or moves bass, mids and treble up to 6 dB either way. (RavenHome)

Checked after this flash: the tuning is on the main output and Speaker tuning sits under Media
volume in Settings › Sound; moving a slider applies at once. The Recents button was still
there (fixed for the next flash).

### rv2611031552 (3 Nov, 15:52)

- Every Settings screen is one glass card: the homepage no longer splits into groups, and
  category titles are small labels inside the card instead of gaps between cards.
  (`packages/apps/Settings` 0017)
- Tap and remote-button sounds are audible again at low volume: they still follow media
  volume, but on the speaker they use a flatter curve (about −26 dB at media 4/15 instead of
  −47 dB). (`device/amazon/mt8163-common` 0001)
- No tap, key or remote-button sounds while music or other media plays on the speaker (such
  as Bluetooth audio from a phone); they come back when it stops. (`frameworks/base` 0010)
- Bluetooth album art works: the phone's cover art was downloaded but no app could open it,
  because the Bluetooth app only registered its cover art provider when the setting was on in
  the app itself, not through an overlay. The provider is now always registered.
  (`packages/apps/Bluetooth` 0001)

Checked after this flash: taps play at about −13 dB (−41 dB before). With the phone playing
(on rv2611031616): album art shows (confirmed by the user), and a tap plays no sound.

### rv2611031307 (3 Nov, 13:07)

- Settings homepage list sits between the status and navigation bars from the start, so it never
  runs behind the nav buttons; the search bar no longer slides back on every upward scroll.
  Replaces 0014's approach. (`packages/apps/Settings` 0016, ravenOS servant 1)

Checked after this flash: the homepage at scroll top and scrolled fades out cleanly at both bars,
the search bar hides when scrolled and comes back at the top, and the remote's Up reaches it. On
rv2611031255, 0015 was confirmed on About tablet and Camera's app info.

### rv2611031255 (3 Nov, 12:55)

- Tried to make the Settings homepage end above the navigation bar right after it opens; checked
  afterwards, it didn't work (fixed in 0016). (`packages/apps/Settings` 0014, ravenOS servant 1)
- About tablet and app info pages: no dark box behind the header (name or app icon); the glass
  card starts below it with its rounded top. (`packages/apps/Settings` 0015, ravenOS servant 1)

### rv2611031244 (3 Nov, 12:44)

- Device health page: system, processor (usage, core clocks, load), memory, storage (including
  background videos, photos and camera), temperatures, Wi-Fi (signal, band, IP, router, DNS),
  Bluetooth, display and sound, on glass cards refreshed every 5 seconds. Meters turn amber at
  75 % and red at 90 %. Opens from Settings › About tablet (with a live summary) and from the
  RavenOS page. (RavenHome)
- Album art on the now-playing card when the Echo plays from a phone over Bluetooth (when the
  phone sends cover art). (RavenHome)
- Settings lists (About tablet and every other page) end above the navigation bar and fade out
  where there is more to scroll, instead of looking cut off. (`packages/apps/Settings` 0013,
  ravenOS servant 1)

Checked after this flash: About tablet shows Device health with its summary, and the list ends
cleanly. Found with the new page: the Echo's DNS is the ISP's, not
AdGuard Home; that is still set by hand on the Echo.

### rv2611031226 (3 Nov, 12:26)

- On-screen keyboard works with the remote: the arrows move an amber-outlined key and OK types
  it (Shift, ?123, Delete, Space and Enter included). The first press only shows the outline;
  a touch hides it. (`packages/inputmethods/LatinIME` 0004, ravenOS servant 1)

Checked on the Echo with injected key presses: typing, ?123, Delete and Back all work. Not yet
tried with the real remote.

### rv2611031210 (3 Nov, 12:10), flashed by the ravenOS tweaks pt2 session

- Portal TV remote key layout (`keylayout/Vendor_1915_Product_eeee.kl`): the Search/mic button
  toggles mute; the other buttons work as before; Red/Green/Blue do nothing. Android now treats the
  remote as a remote rather than a full keyboard.
- Settings: with a remote, Up from the first homepage row moves to the search bar, and Down comes
  back. (`packages/apps/Settings` 0012)

On the Echo only: Microphone processing set to far field (`persist.sys.audio.mic_processing`).
Verified after this flash: the misty-pavilion background video plays.

### rv2611031134 (3 Nov, 11:34)

- Tap, navigation and remote-button sounds follow the volume buttons (media volume). Alarms,
  notifications and the ringer keep their own volume. (`frameworks/base` 0009)
- Dicio microphone fix: records from the voice-communication input, because the Echo's
  voice-recognition input has no gain. (Dicio fork 9a8a9d0, `apps/Dicio/patches` 0005)

On the Echo only: the home background `interstellar-720p.mp4` (1.8 GB, no other copy kept) was
deleted from `/sdcard/Movies/RavenOS` and replaced with `misty-pavilion.mp4` (5.3 MB, 1422x800
30 fps 12 s loop), freeing about 1.6 GB. A reference copy and the encode recipe are in
`media/backgrounds/`. (Portal session)

### rv2611031021 (3 Nov, 10:21)

- Data Saver tile removed from quick settings and the tile editor. (`frameworks/base` 0008)

On the Echo only: Data Saver switched off (`cmd netpolicy set restrict-background false`).

### rv2611031013 (3 Nov, 10:13), flashed by the ravenOS tweaks pt2 session (formerly settings retheming)

- Dicio voice assistant and RHVoice text-to-speech added to the image.
- Settings: amber focus outline on rows, a tick when the remote moves through a list, and the
  homepage search bar scrolls away. (`packages/apps/Settings` 0011)

### rv2611030942 (3 Nov, 09:42)

- Battery Saver tile removed from quick settings and the tile editor. (`frameworks/base` 0007)
- Settings dialogs drawn as glass cards (Bluetooth pairing, Rename and others).
  (`packages/apps/Settings` 0009)
- Remote up/down steps through the Settings homepage rows. (`packages/apps/Settings` 0010)

On the Echo only: "show on-screen keyboard while a physical keyboard is connected" turned on
(`show_ime_with_hard_keyboard=1`), because the remote registered as a keyboard.

### rv2611030846 (3 Nov, 08:46)

- Touch glow follows the finger about three times faster (40 ms instead of 120 ms; fades in
  over 80 ms instead of 150 ms), in all eight apps that have it.

### rv2611030736 (3 Nov, 07:36)

- Touch glow added to RavenHome (RavenOS settings, Alarms & timers, All apps, alarm ring
  screen), EchoParts, LineageParts, Default apps / Permission manager and Files.
- RavenHome restarts itself once per boot if storage access is granted but shared storage reads
  as empty (the background video was black on the first boot after a flash).

### rv2611030025 (3 Nov, 00:25)

- Obsidian glass theme across Settings, the RavenOS page, Charging sounds, Microphone
  processing, quick settings, the screen record dialog, Default apps, Permission manager, Files,
  All apps, the Pair a phone pop-up, Alarms & timers and the home screen text and buttons.
- All pop-up notifications, toasts and alert and error dialogs, from any app, use the theme.
- Touch glow in Settings, Settings search and the keyboard (ravenOS tweaks pt2 session).
- Remote D-pad support with focus rings in RavenHome (Portal session).
- Camera2 restored, with camera, microphone and storage granted by default.
- Battery, Accounts and the light/dark theme options removed from Settings; Printing turned off.
- Bluetooth shows up as "RavenOS".
- Wi-Fi without IPv6, so the ISP's IPv6 DNS can't bypass AdGuard Home (the static IP and DNS
  are still set by hand on the Echo).
- Weather retries as soon as the network comes up after boot.
- The background video restarts when it ends, and recovers if playback stalls.
- Each build gets its own build number, so Android refreshes its cached app data after a flash.
