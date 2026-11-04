package org.ravenos.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.DynamicsProcessing;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Tunes the Echo's built-in speaker the way Fire OS did: a fuller low end, a less boxy and
 * harsh midrange, a little air on top, and a limiter so loud bass doesn't distort. It runs on
 * the device's main output mix and switches itself off while sound goes to headphones, the
 * audio jack, USB or Bluetooth, so those stay untouched.
 *
 * <p>Settings › Sound › Speaker tuning turns it off or moves the bass, mids and treble up to
 * 6 dB either way from the tune.
 */
final class SpeakerTuning {
    private static final String TAG = "RavenHome";

    /** Upper edge of each equalizer band, in Hz. */
    private static final float[] BAND_EDGES = {
            80f, 160f, 320f, 640f, 1280f, 2560f, 5120f, 10240f, 20000f};
    /** Gain of each band, in dB. */
    private static final float[] BAND_GAINS = {
            -2f, 4f, 2.5f, -1f, 0f, 0f, -1.5f, 1f, 2f};
    /** How much each band follows the bass, mids and treble sliders. */
    private static final float[] BASS_WEIGHTS = {0.5f, 1f, 0.6f, 0f, 0f, 0f, 0f, 0f, 0f};
    private static final float[] MIDS_WEIGHTS = {0f, 0f, 0f, 0.6f, 1f, 0.6f, 0f, 0f, 0f};
    private static final float[] TREBLE_WEIGHTS = {0f, 0f, 0f, 0f, 0f, 0f, 0.5f, 1f, 1f};

    static final String PREF_ENABLED = "speaker_tuning";
    static final String PREF_BASS = "speaker_bass";
    static final String PREF_MIDS = "speaker_mids";
    static final String PREF_TREBLE = "speaker_treble";
    /** Furthest a slider moves the tune, in dB. */
    static final int RANGE_DB = 6;

    /** Headroom for the boosts, made up by the limiter. */
    private static final float INPUT_GAIN_DB = -2f;

    private static SpeakerTuning sInstance;

    private final AudioManager mAudioManager;
    private final SharedPreferences mPrefs;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private DynamicsProcessing mEffect;

    private SpeakerTuning(Context context) {
        mAudioManager = context.getSystemService(AudioManager.class);
        mPrefs = new Prefs(context).raw();
    }

    /** The running tuning, or null if this device can't do it. */
    static synchronized SpeakerTuning get() {
        return sInstance != null && sInstance.mEffect != null ? sInstance : null;
    }

    /** Starts the tuning once per process. */
    static synchronized void start(Context context) {
        if (sInstance != null) {
            return;
        }
        sInstance = new SpeakerTuning(context.getApplicationContext());
        sInstance.init();
    }

    private void init() {
        try {
            mEffect = new DynamicsProcessing(0, 0, buildConfig(gains(mPrefs)));
        } catch (RuntimeException e) {
            Log.w(TAG, "Speaker tuning not available", e);
            return;
        }
        mAudioManager.registerAudioDeviceCallback(new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
                update();
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
                update();
            }
        }, mHandler);
        update();
    }

    /** The tune's band gains with the bass, mids and treble sliders added. */
    private static float[] gains(SharedPreferences prefs) {
        int bass = prefs.getInt(PREF_BASS, 0);
        int mids = prefs.getInt(PREF_MIDS, 0);
        int treble = prefs.getInt(PREF_TREBLE, 0);
        float[] gains = new float[BAND_GAINS.length];
        for (int i = 0; i < gains.length; i++) {
            gains[i] = BAND_GAINS[i] + bass * BASS_WEIGHTS[i] + mids * MIDS_WEIGHTS[i]
                    + treble * TREBLE_WEIGHTS[i];
        }
        return gains;
    }

    /** Applies changed settings at once, while sound is playing. */
    void reload() {
        float[] gains = gains(mPrefs);
        try {
            for (int i = 0; i < gains.length; i++) {
                mEffect.setPostEqBandAllChannelsTo(i,
                        new DynamicsProcessing.EqBand(true, BAND_EDGES[i], gains[i]));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Speaker tuning failed", e);
        }
        update();
    }

    private static DynamicsProcessing.Config buildConfig(float[] gains) {
        int bands = BAND_EDGES.length;
        DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                false, 0, false, 0, true, bands, true);
        builder.setInputGainAllChannelsTo(INPUT_GAIN_DB);
        DynamicsProcessing.Eq eq = new DynamicsProcessing.Eq(true, true, bands);
        for (int i = 0; i < bands; i++) {
            eq.setBand(i, new DynamicsProcessing.EqBand(true, BAND_EDGES[i], gains[i]));
        }
        builder.setPostEqAllChannelsTo(eq);
        // attack ms, release ms, ratio, threshold dB, post gain dB
        builder.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(
                true, true, 0, 1f, 60f, 10f, -1f, 0f));
        return builder.build();
    }

    /** On only while the speaker is the one playing, and the user hasn't turned it off. */
    private void update() {
        boolean speaker = mPrefs.getBoolean(PREF_ENABLED, true);
        for (AudioDeviceInfo device : mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_LINE_ANALOG:
                case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_HDMI:
                    speaker = false;
                    break;
                default:
                    break;
            }
        }
        try {
            mEffect.setEnabled(speaker);
            Log.i(TAG, "Speaker tuning " + (speaker ? "on" : "off"));
        } catch (RuntimeException e) {
            Log.w(TAG, "Speaker tuning failed", e);
        }
    }
}
