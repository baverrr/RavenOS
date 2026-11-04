# Flashing RavenOS onto an Echo Show 8 (1st gen, 2019, "crown")

> [!WARNING]
> Unlocking uses the community **amonet** exploit. If it's interrupted at the wrong moment,
> the Echo can be **permanently bricked**. Most units can't be recovered from a hard brick.
> Read every step first and do it in one uninterrupted sitting.

This only works on the **1st gen Echo Show 8 (2019)**, model **C7H6N3** on the label underneath.
It does **not** work on the 2nd gen (2021) Echo Show 8.

## What you need

- The Echo, its power adapter, and a **micro-USB data cable** (not a charge-only cable).
- A Linux PC with `adb`, `fastboot`, `python3`, `python3-serial` and `dos2unix`.
- The amonet package **`amonet-crown-v2.0.1.zip`** from the attachments of the XDA thread
  [[UNLOCK][ROOT][TWRP][UNBRICK] Amazon Echo Show 8 1st Gen - 2019 (crown)](https://xdaforums.com/t/unlock-root-twrp-unbrick-amazon-echo-show-8-1st-gen-2019-crown.4766687/).
- The RavenOS zip from `out/target/product/crown/RavenOS-<version>-<date>-crown.zip`, plus its
  `.sha256`.
- About 30 minutes. The PC mustn't sleep during the unlock.

Everything on the Echo is erased. If you like, remove it from your Amazon account first
(Alexa app → Devices → the Echo → Deregister).

## 1. Prepare the PC

```bash
sudo apt install adb fastboot python3 python3-serial dos2unix
```

ModemManager grabs the MediaTek USB port and breaks the exploit. Stop it for the duration:

```bash
sudo systemctl stop ModemManager
```

Check the RavenOS zip:

```bash
cd out/target/product/crown && sha256sum -c RavenOS-*-crown.zip.sha256
```

## 2. Unlock the bootloader and install TWRP (amonet, no disassembly)

1. Extract `amonet-crown-v2.0.1.zip`, open a terminal in that folder, and fix line endings and
   permissions:

   ```bash
   dos2unix *.sh && chmod +x *.sh
   ```

2. Start the script and leave the terminal open:

   ```bash
   sudo ./fastbrick.sh
   ```

3. Unplug the Echo's power. Hold **all three top buttons** (volume up, volume down, mic mute),
   plug the power back in, and keep holding until **`=> FASTBOOT mode...`** appears on the screen.
4. Connect the micro-USB cable to the PC. The script detects the Echo.
5. Type **`YES`** when asked. Up to this point you can still back out safely.
6. **After the 10-second grace period, touch nothing.** Don't unplug the cable or power, and don't
   close the terminal. It takes up to 5 minutes, then the Echo reboots into **TWRP**.

## 3. Back up Fire OS (recommended)

In TWRP: **Backup** → tick **Boot** and **System** → swipe. Then copy it to the PC:

```bash
adb pull /sdcard/TWRP ./fireos-backup
```

Never back up and restore, or otherwise write, the **LK**, **Preloader**, **TZ** or **TEE**
partitions. The only safe way back to Fire OS is flashing a stock Amazon update in TWRP.

## 4. Wipe and install RavenOS

1. TWRP → **Wipe** → **Advanced Wipe** → tick **only System, Data and Cache** → swipe.
   If Data won't wipe (Fire OS encrypts it), use **Wipe → Format Data** and type `yes`.
2. TWRP → **Advanced** → **ADB Sideload** → swipe. Then on the PC:

   ```bash
   adb sideload RavenOS-<version>-<date>-crown.zip
   ```

3. TWRP → **Reboot** → **System**. The first boot takes 3–5 minutes and shows the raven boot
   animation.

The zip checks that it's on a crown, then writes only the `system` and `boot` partitions.

## 5. First boot

1. Tap anywhere on the home screen → **Settings** (gear) opens RavenOS settings. Use
   **Apps → Settings** for Android settings and Wi-Fi.
2. Connect Wi-Fi, then from the PC (approve the key prompt on the Echo's screen):

   ```bash
   adb connect 192.0.2.10:5555
   ```

   Replace `192.0.2.10` with the Echo's address (Android Settings → About → IP address).
3. Copy a background video (see `tools/make-background-video.sh`):

   ```bash
   adb shell df -h /data
   adb push background.mp4 /sdcard/Movies/RavenOS/
   ```

4. Set the weather location in RavenOS settings.

## Recovery

- **TWRP:** hold **only volume up** while plugging in the power (or run `boot-recovery.sh` from
  the amonet folder).
- **RavenOS won't boot:** in TWRP, sideload a plain LineageOS zip
  (`lineage-18.1-<date>-UNOFFICIAL-crown.zip`). That shows whether the problem is RavenOS's
  changes or the device support. `build-ravenos.sh` doesn't make one: build it in a separate
  sync with only the amazon-oss patches applied (the RavenOS patches change shared sources),
  with `source build/envsetup.sh && lunch lineage_crown-userdebug && mka bacon`.
- **Last resort (unbrick):** amonet 2.x exposes the Preloader USB download port. Hold **mic mute +
  volume down** while booting, with USB connected, then use MTKClient. Never write LK, Preloader,
  TZ or TEE1 in this mode; doing so is a hard brick.

Afterwards you can turn ModemManager back on with `sudo systemctl start ModemManager`.
