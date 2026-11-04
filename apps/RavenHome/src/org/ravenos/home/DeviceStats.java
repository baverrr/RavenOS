package org.ravenos.home;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the Echo's health: system, processor, memory, storage, temperatures, network,
 * Bluetooth, display and sound. Everything comes from public APIs, /proc and /sys, so it runs
 * off the main thread. Keep one instance per screen: CPU usage is the change between two reads.
 */
final class DeviceStats {
    /** How a value is doing: shown as the meter's color and summed up in the header. */
    enum Level { OK, WARN, BAD }

    static final class Row {
        final String label;
        final String value;
        /** Meter fill from 0 to 1, or a negative number for a row without a meter. */
        final float meter;
        final Level level;

        Row(String label, String value) {
            this(label, value, -1f, Level.OK);
        }

        Row(String label, String value, float meter, Level level) {
            this.label = label;
            this.value = value;
            this.meter = meter;
            this.level = level;
        }
    }

    static final class Section {
        final String title;
        final List<Row> rows = new ArrayList<>();

        Section(String title) {
            this.title = title;
        }

        Section add(Row row) {
            rows.add(row);
            return this;
        }
    }

    /** Usage meters turn amber at 75 % and red at 90 %, as on the home lab dashboard. */
    private static final float WARN_FRACTION = 0.75f;
    private static final float BAD_FRACTION = 0.90f;
    /** Folder sizes are slow to add up; they are read again at most this often. */
    private static final long FOLDER_SIZE_MS = 60_000L;

    private final Context mContext;
    private long mLastCpuTotal = -1;
    private long mLastCpuIdle;
    private long mFolderSizesAt = -FOLDER_SIZE_MS;
    private long mVideoBytes;
    private long mPhotoBytes;
    private long mCameraBytes;

    DeviceStats(Context context) {
        mContext = context.getApplicationContext();
    }

    /** All sections, in the order the page shows them. */
    List<Section> read() {
        List<Section> sections = new ArrayList<>();
        sections.add(system());
        sections.add(processor());
        sections.add(memory());
        sections.add(storage());
        sections.add(temperatures());
        sections.add(network());
        sections.add(bluetooth());
        sections.add(displayAndSound());
        return sections;
    }

    /** One line for the "Device health" entry in Settings › About. */
    String summary() {
        List<String> parts = new ArrayList<>();
        Float cpu = thermalZone("mtktscpu");
        if (cpu != null) {
            parts.add(string(R.string.health_summary_cpu, temperature(cpu)));
        }
        ActivityManager.MemoryInfo mem = memoryInfo();
        parts.add(string(R.string.health_summary_memory,
                percent(1f - (float) mem.availMem / mem.totalMem)));
        StatFs data = new StatFs(Environment.getDataDirectory().getPath());
        parts.add(string(R.string.health_summary_storage, bytes(data.getAvailableBytes())));
        return String.join(" · ", parts);
    }

    // Sections

    private Section system() {
        String patch = Build.VERSION.SECURITY_PATCH;
        return new Section(string(R.string.health_system))
                .add(new Row(string(R.string.health_device),
                        ("Amzn".equalsIgnoreCase(Build.MANUFACTURER) ? "Amazon"
                                : Build.MANUFACTURER) + " " + Build.MODEL))
                .add(new Row(string(R.string.health_ravenos),
                        SystemProperties.get("ro.ravenos.version", "?")
                                + " · " + Build.VERSION.INCREMENTAL))
                .add(new Row(string(R.string.health_android), string(R.string.health_android_value,
                        Build.VERSION.RELEASE, patch)))
                .add(new Row(string(R.string.health_kernel), kernelVersion()))
                .add(new Row(string(R.string.health_uptime),
                        duration(SystemClock.elapsedRealtime())))
                .add(new Row(string(R.string.health_power), string(R.string.health_power_value)));
    }

    private Section processor() {
        Section s = new Section(string(R.string.health_processor));
        int cores = Runtime.getRuntime().availableProcessors();
        long maxKhz = readLong("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq", 0);
        s.add(new Row(string(R.string.health_chip), chipName()));
        s.add(new Row(string(R.string.health_cores), string(R.string.health_cores_value, cores,
                coreName(), maxKhz / 1_000_000f)));

        Float usage = cpuUsage();
        if (usage != null) {
            s.add(new Row(string(R.string.health_usage), percent(usage), usage, level(usage)));
        } else {
            s.add(new Row(string(R.string.health_usage), string(R.string.health_measuring)));
        }

        StringBuilder clocks = new StringBuilder();
        for (int i = 0; i < cores; i++) {
            if (i > 0) {
                clocks.append(" · ");
            }
            String cpu = "/sys/devices/system/cpu/cpu" + i;
            boolean online = i == 0 || readLong(cpu + "/online", 1) == 1;
            long khz = online ? readLong(cpu + "/cpufreq/scaling_cur_freq", 0) : 0;
            clocks.append(khz > 0 ? String.format(Locale.getDefault(), "%.2f", khz / 1_000_000f)
                    : string(R.string.health_core_off));
        }
        s.add(new Row(string(R.string.health_clocks), string(R.string.health_ghz, clocks)));

        String load = readFirstLine("/proc/loadavg");
        if (load != null) {
            String[] parts = load.split(" ");
            if (parts.length >= 3) {
                s.add(new Row(string(R.string.health_load),
                        parts[0] + " · " + parts[1] + " · " + parts[2]));
            }
        }
        return s;
    }

    private Section memory() {
        ActivityManager.MemoryInfo mem = memoryInfo();
        long used = mem.totalMem - mem.availMem;
        float fraction = (float) used / mem.totalMem;
        Section s = new Section(string(R.string.health_memory))
                .add(new Row(string(R.string.health_installed), installedMemory(mem.totalMem)))
                .add(new Row(string(R.string.health_in_use),
                        string(R.string.health_of, bytes(used), bytes(mem.totalMem)),
                        fraction, level(fraction)))
                .add(new Row(string(R.string.health_available), bytes(mem.availMem)));
        long swapTotal = meminfoKb("SwapTotal") * 1024;
        if (swapTotal > 0) {
            long swapUsed = swapTotal - meminfoKb("SwapFree") * 1024;
            float swap = (float) swapUsed / swapTotal;
            s.add(new Row(string(R.string.health_swap),
                    string(R.string.health_of, bytes(swapUsed), bytes(swapTotal)), swap,
                    level(swap)));
        }
        return s;
    }

    private Section storage() {
        StatFs data = new StatFs(Environment.getDataDirectory().getPath());
        long total = data.getTotalBytes();
        long free = data.getAvailableBytes();
        float fraction = 1f - (float) free / total;
        long now = SystemClock.elapsedRealtime();
        if (now - mFolderSizesAt >= FOLDER_SIZE_MS) {
            mFolderSizesAt = now;
            mVideoBytes = folderSize(Background.VIDEO_DIR);
            mPhotoBytes = folderSize(Background.PHOTO_DIR);
            mCameraBytes = folderSize(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM));
        }
        return new Section(string(R.string.health_storage))
                .add(new Row(string(R.string.health_internal),
                        string(R.string.health_of, bytes(total - free), bytes(total)),
                        fraction, level(fraction)))
                .add(new Row(string(R.string.health_free), bytes(free)))
                .add(new Row(string(R.string.health_videos), bytes(mVideoBytes)))
                .add(new Row(string(R.string.health_photos), bytes(mPhotoBytes)))
                .add(new Row(string(R.string.health_camera), bytes(mCameraBytes)));
    }

    private Section temperatures() {
        Section s = new Section(string(R.string.health_temperatures));
        addTemperature(s, R.string.health_temp_cpu, thermalZone("mtktscpu"), 75, 90);
        addTemperature(s, R.string.health_temp_pmic, thermalZone("mtktspmic"), 65, 80);
        addTemperature(s, R.string.health_temp_case, thermalZone("case_virtual"), 50, 60);
        // The hottest of the board sensors.
        Float board = null;
        for (String type : new String[] {"mtkts1", "mtkts3", "mtkts4", "mtkts5",
                "therm0_s", "therm1_s", "therm2_s"}) {
            Float t = thermalZone(type);
            if (t != null && (board == null || t > board)) {
                board = t;
            }
        }
        addTemperature(s, R.string.health_temp_board, board, 65, 80);
        if (s.rows.isEmpty()) {
            s.add(new Row(string(R.string.health_temperatures),
                    string(R.string.health_unavailable)));
        }
        return s;
    }

    private Section network() {
        Section s = new Section(string(R.string.health_network));
        WifiManager wifi = mContext.getSystemService(WifiManager.class);
        if (wifi == null || !wifi.isWifiEnabled()) {
            return s.add(new Row(string(R.string.health_wifi), string(R.string.health_off)));
        }
        WifiInfo info = wifi.getConnectionInfo();
        if (info == null || info.getNetworkId() == -1) {
            return s.add(new Row(string(R.string.health_wifi),
                    string(R.string.health_not_connected)));
        }
        String ssid = info.getSSID();
        if (ssid != null && ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        s.add(new Row(string(R.string.health_wifi), ssid));
        int rssi = info.getRssi();
        int bars = wifi.calculateSignalLevel(rssi);
        int maxBars = wifi.getMaxSignalLevel();
        float strength = maxBars > 0 ? (float) bars / maxBars : 0f;
        // A strong signal is good, so the colors run the other way from usage.
        Level signal = strength >= 0.5f ? Level.OK : strength > 0f ? Level.WARN : Level.BAD;
        s.add(new Row(string(R.string.health_signal), string(R.string.health_signal_value,
                signalName(bars, maxBars), rssi), Math.max(strength, 0.04f), signal));
        int mhz = info.getFrequency();
        s.add(new Row(string(R.string.health_band), string(R.string.health_band_value,
                mhz >= 5000 ? "5" : "2.4", channel(mhz), info.getLinkSpeed())));

        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        Network net = cm.getActiveNetwork();
        LinkProperties lp = net != null ? cm.getLinkProperties(net) : null;
        if (lp != null) {
            List<String> v4 = new ArrayList<>();
            boolean v6 = false;
            for (LinkAddress address : lp.getLinkAddresses()) {
                if (address.getAddress() instanceof Inet4Address) {
                    v4.add(address.getAddress().getHostAddress());
                } else if (!address.getAddress().isLinkLocalAddress()) {
                    v6 = true;
                }
            }
            if (!v4.isEmpty()) {
                s.add(new Row(string(R.string.health_ip), String.join(", ", v4)));
            }
            s.add(new Row(string(R.string.health_ipv6),
                    string(v6 ? R.string.health_on : R.string.health_off)));
            for (RouteInfo route : lp.getRoutes()) {
                if (route.isDefaultRoute() && route.getGateway() instanceof Inet4Address) {
                    s.add(new Row(string(R.string.health_gateway),
                            route.getGateway().getHostAddress()));
                    break;
                }
            }
            List<String> dns = new ArrayList<>();
            for (InetAddress server : lp.getDnsServers()) {
                dns.add(server.getHostAddress());
            }
            if (!dns.isEmpty()) {
                s.add(new Row(string(R.string.health_dns), String.join(", ", dns)));
            }
        }
        return s;
    }

    private Section bluetooth() {
        Section s = new Section(string(R.string.health_bluetooth));
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return s.add(new Row(string(R.string.health_bluetooth),
                    string(R.string.health_off)));
        }
        s.add(new Row(string(R.string.health_bt_name), adapter.getName()));
        List<String> connected = new ArrayList<>();
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            if (device.isConnected()) {
                String name = device.getAlias();
                connected.add(name != null ? name : device.getName());
            }
        }
        s.add(new Row(string(R.string.health_bt_connected), connected.isEmpty()
                ? string(R.string.health_nothing_connected) : String.join(", ", connected)));
        s.add(new Row(string(R.string.health_bt_paired),
                String.valueOf(adapter.getBondedDevices().size())));
        return s;
    }

    private Section displayAndSound() {
        Section s = new Section(string(R.string.health_display_sound));
        Display display = mContext.getSystemService(WindowManager.class).getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        s.add(new Row(string(R.string.health_screen), string(R.string.health_screen_value,
                metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                Math.round(display.getRefreshRate()))));
        ContentResolver cr = mContext.getContentResolver();
        int brightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 0);
        boolean auto = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        s.add(new Row(string(R.string.health_brightness), percent(brightness / 255f)
                + (auto ? " · " + string(R.string.health_auto) : "")));
        AudioManager audio = mContext.getSystemService(AudioManager.class);
        s.add(volumeRow(audio, R.string.health_media_volume, AudioManager.STREAM_MUSIC));
        s.add(volumeRow(audio, R.string.health_alarm_volume, AudioManager.STREAM_ALARM));
        String mic = SystemProperties.get("persist.sys.audio.mic_processing", "");
        if (!mic.isEmpty()) {
            s.add(new Row(string(R.string.health_mic), capitalize(mic.replace('_', ' '))));
        }
        return s;
    }

    // Helpers

    private Row volumeRow(AudioManager audio, int label, int stream) {
        int max = audio.getStreamMaxVolume(stream);
        int index = audio.getStreamVolume(stream);
        float fraction = max > 0 ? (float) index / max : 0f;
        return new Row(string(label), audio.isStreamMute(stream) ? string(R.string.health_muted)
                : string(R.string.health_of, String.valueOf(index), String.valueOf(max)),
                fraction, Level.OK);
    }

    private void addTemperature(Section s, int label, Float celsius, int warn, int bad) {
        if (celsius == null) {
            return;
        }
        Level level = celsius >= bad ? Level.BAD : celsius >= warn ? Level.WARN : Level.OK;
        s.add(new Row(string(label), temperature(celsius), Math.min(1f, celsius / 100f), level));
    }

    /** CPU busy fraction since the last read, from /proc/stat; null on the first read. */
    private Float cpuUsage() {
        String line = readFirstLine("/proc/stat");
        if (line == null || !line.startsWith("cpu ")) {
            return null;
        }
        String[] f = line.trim().split("\\s+");
        long total = 0;
        for (int i = 1; i < f.length; i++) {
            total += Long.parseLong(f[i]);
        }
        // idle + iowait
        long idle = Long.parseLong(f[4]) + (f.length > 5 ? Long.parseLong(f[5]) : 0);
        Float usage = null;
        if (mLastCpuTotal >= 0 && total > mLastCpuTotal) {
            usage = 1f - (float) (idle - mLastCpuIdle) / (total - mLastCpuTotal);
            usage = Math.max(0f, Math.min(1f, usage));
        }
        mLastCpuTotal = total;
        mLastCpuIdle = idle;
        return usage;
    }

    /** Degrees Celsius of the thermal zone with this type, or null. */
    private static Float thermalZone(String type) {
        File[] zones = new File("/sys/class/thermal").listFiles(
                (dir, name) -> name.startsWith("thermal_zone"));
        if (zones == null) {
            return null;
        }
        for (File zone : zones) {
            if (type.equals(readFirstLine(zone + "/type"))) {
                long milli = readLong(zone + "/temp", Long.MIN_VALUE);
                // Unplugged sensors read far below zero.
                return milli > -20_000 ? milli / 1000f : null;
            }
        }
        return null;
    }

    /** "4.9.337" from "4.9.337-g9478a34fb79d-ab…": the rest is the build's commit and number. */
    private static String kernelVersion() {
        String version = System.getProperty("os.version", "");
        int dash = version.indexOf('-');
        return dash > 0 ? version.substring(0, dash) : version;
    }

    private String chipName() {
        String platform = SystemProperties.get("ro.board.platform", Build.HARDWARE);
        return platform.startsWith("mt") ? "MediaTek " + platform.toUpperCase(Locale.ROOT)
                : platform;
    }

    /** Arm core name from the "CPU part" line of /proc/cpuinfo. */
    private static String coreName() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("CPU part")) {
                    switch (line.substring(line.indexOf(':') + 1).trim()) {
                        case "0xd03": return "Cortex-A53";
                        case "0xd04": return "Cortex-A35";
                        case "0xd05": return "Cortex-A55";
                        case "0xd07": return "Cortex-A57";
                        case "0xd08": return "Cortex-A72";
                        case "0xd09": return "Cortex-A73";
                        default: return "Arm";
                    }
                }
            }
        } catch (IOException e) {
            // Fall through.
        }
        return "Arm";
    }

    private ActivityManager.MemoryInfo memoryInfo() {
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        mContext.getSystemService(ActivityManager.class).getMemoryInfo(mem);
        return mem;
    }

    /** The RAM fitted, rounded up to a whole size: the kernel keeps some for itself. */
    private String installedMemory(long total) {
        long gb = 1L << 30;
        long rounded = ((total + gb / 2 - 1) / (gb / 2)) * (gb / 2);
        return bytes(rounded);
    }

    private static long meminfoKb(String key) {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith(key + ":")) {
                    return Long.parseLong(line.replaceAll("[^0-9]", ""));
                }
            }
        } catch (IOException | NumberFormatException e) {
            // Fall through.
        }
        return 0;
    }

    private static long folderSize(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        long size = 0;
        for (File f : files) {
            size += f.isDirectory() ? folderSize(f) : f.length();
        }
        return size;
    }

    private static int channel(int mhz) {
        if (mhz == 2484) {
            return 14;
        }
        if (mhz >= 2412 && mhz < 2484) {
            return (mhz - 2407) / 5;
        }
        return mhz >= 5000 ? (mhz - 5000) / 5 : 0;
    }

    private String signalName(int bars, int maxBars) {
        float f = maxBars > 0 ? (float) bars / maxBars : 0f;
        return string(f >= 0.75f ? R.string.health_signal_great : f >= 0.5f
                ? R.string.health_signal_good : f > 0f ? R.string.health_signal_fair
                : R.string.health_signal_poor);
    }

    private static Level level(float fraction) {
        return fraction >= BAD_FRACTION ? Level.BAD
                : fraction >= WARN_FRACTION ? Level.WARN : Level.OK;
    }

    private String duration(long millis) {
        long minutes = millis / 60_000;
        long days = minutes / (24 * 60);
        long hours = (minutes / 60) % 24;
        long mins = minutes % 60;
        if (days > 0) {
            return string(R.string.health_duration_days, days, hours);
        }
        return hours > 0 ? string(R.string.health_duration_hours, hours, mins)
                : string(R.string.health_duration_minutes, mins);
    }

    static String bytes(long bytes) {
        if (bytes >= 1L << 30) {
            return String.format(Locale.getDefault(), "%.1f GB", bytes / (float) (1L << 30));
        }
        if (bytes >= 1L << 20) {
            return String.format(Locale.getDefault(), "%d MB", bytes >> 20);
        }
        return String.format(Locale.getDefault(), "%d KB", bytes >> 10);
    }

    private static String percent(float fraction) {
        return Math.round(fraction * 100) + " %";
    }

    private static String temperature(float celsius) {
        return String.format(Locale.getDefault(), "%.1f °C", celsius);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String readFirstLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String line = r.readLine();
            return line != null ? line.trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static long readLong(String path, long fallback) {
        String line = readFirstLine(path);
        try {
            return line != null ? Long.parseLong(line) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String string(int id, Object... args) {
        return mContext.getString(id, args);
    }
}
