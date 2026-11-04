package org.ravenos.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * The home-screen background: muted looping videos from /sdcard/Movies/RavenOS, or a photo
 * slideshow from /sdcard/Pictures/RavenOS, or nothing. Videos are cropped to fill the screen
 * and resume where they left off, so a long film keeps playing through instead of restarting.
 */
final class Background implements TextureView.SurfaceTextureListener {
    private static final String TAG = "RavenBackground";

    static final File VIDEO_DIR = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "RavenOS");
    static final File PHOTO_DIR = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "RavenOS");

    private static final String[] VIDEO_EXT = {".mp4", ".m4v", ".mkv", ".webm", ".mov", ".3gp", ".ts"};
    private static final String[] PHOTO_EXT = {".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif"};
    private static final String KEY_VIDEO_INDEX = "video_index";
    private static final String KEY_POSITION_PREFIX = "video_pos:";
    private static final long SAVE_POSITION_MS = 30_000L;
    private static final long FADE_MS = 1_200L;
    private static final int MAX_FILES = 5000;
    private static final long RESCAN_SOON_MS = 10_000L;
    private static final long RESCAN_MS = 60_000L;

    private final Handler mHandler;
    private final ExecutorService mExecutor;
    private final Prefs mPrefs;
    private final TextureView mVideoView;
    private ImageView mFront;
    private ImageView mBack;

    private boolean mRunning;
    /** Bumped on every start/stop so late async results from an older run are dropped. */
    private int mGeneration;

    private List<File> mVideos = new ArrayList<>();
    private int mVideoIndex;
    private MediaPlayer mPlayer;
    private Surface mSurface;
    private File mCurrentVideo;
    private int mVideoWidth;
    private int mVideoHeight;

    private List<File> mPhotos = new ArrayList<>();
    private int mPhotoIndex;

    private final Runnable mSavePosition = new Runnable() {
        @Override
        public void run() {
            if (isStalled()) {
                // The decoder can stop without a completion or error callback (seen at the
                // end of a long video). Treat it like a completion: forget the position and move
                // on, since reopening the same video would replay the stalled stretch.
                Log.w(TAG, "Video stalled, moving on from " + mCurrentVideo);
                if (mCurrentVideo != null) {
                    mPrefs.raw().edit().remove(positionKey(mCurrentVideo)).apply();
                }
                nextVideo();
                return;
            }
            savePosition();
            mHandler.postDelayed(this, SAVE_POSITION_MS);
        }
    };

    /** Playback position at the last check, to notice a video that stopped moving. */
    private int mLastPosition = -1;

    private final Runnable mAdvancePhoto = this::advancePhoto;

    /** Number of looks in a row that found nothing to show. */
    private int mEmptyScans;

    /** Called when shared storage still reads as empty after a few looks; see setOnStorageBlind. */
    private Runnable mOnStorageBlind;

    private final Runnable mRescan = () -> {
        if (mRunning) {
            mRunning = false;
            start();
        }
    };

    Background(Handler handler, ExecutorService executor, Prefs prefs, TextureView videoView,
            ImageView photoA, ImageView photoB) {
        mHandler = handler;
        mExecutor = executor;
        mPrefs = prefs;
        mVideoView = videoView;
        mFront = photoA;
        mBack = photoB;
        mVideoView.setSurfaceTextureListener(this);
        mVideoView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyCrop());
    }

    /**
     * Sets what to do when shared storage itself (not just the RavenOS folders) keeps reading as
     * empty, which means this process can't see it.
     */
    void setOnStorageBlind(Runnable onStorageBlind) {
        mOnStorageBlind = onStorageBlind;
    }

    boolean isRunning() {
        return mRunning;
    }

    void start() {
        if (mRunning) {
            return;
        }
        mRunning = true;
        final int generation = ++mGeneration;
        final String mode = mPrefs.background();
        if ("none".equals(mode)) {
            return;
        }
        mExecutor.execute(() -> {
            List<File> videos = new ArrayList<>();
            List<File> photos = new ArrayList<>();
            if (!"photos".equals(mode)) {
                scan(VIDEO_DIR, VIDEO_EXT, videos, 0);
                Collections.sort(videos);
            }
            if ("photos".equals(mode) || ("auto".equals(mode) && videos.isEmpty())) {
                scan(PHOTO_DIR, PHOTO_EXT, photos, 0);
                Collections.shuffle(photos);
            }
            // A real /sdcard always has some folders in it.
            final File[] root = videos.isEmpty() && photos.isEmpty()
                    ? Environment.getExternalStorageDirectory().listFiles() : null;
            final boolean blind = videos.isEmpty() && photos.isEmpty()
                    && (root == null || root.length == 0);
            mHandler.post(() -> {
                if (generation != mGeneration || !mRunning) {
                    return;
                }
                if (!videos.isEmpty()) {
                    mEmptyScans = 0;
                    mVideos = videos;
                    startVideo();
                } else if (!photos.isEmpty()) {
                    mEmptyScans = 0;
                    mPhotos = photos;
                    mPhotoIndex = 0;
                    advancePhoto();
                } else {
                    // Right after boot, shared storage isn't readable yet and the folders look
                    // empty; files can also be copied over later. Look again.
                    mEmptyScans++;
                    if (blind && mEmptyScans >= 2 && mOnStorageBlind != null) {
                        mOnStorageBlind.run();
                    }
                    mHandler.postDelayed(mRescan, mEmptyScans <= 6 ? RESCAN_SOON_MS : RESCAN_MS);
                }
            });
        });
    }

    void stop() {
        if (!mRunning) {
            return;
        }
        mRunning = false;
        mGeneration++;
        mHandler.removeCallbacks(mRescan);
        mHandler.removeCallbacks(mAdvancePhoto);
        mHandler.removeCallbacks(mSavePosition);
        releasePlayer(true);
        mVideoView.setVisibility(View.GONE);
        for (ImageView v : new ImageView[] {mFront, mBack}) {
            v.animate().cancel();
            v.setImageDrawable(null);
            v.setAlpha(0f);
        }
        mVideos = new ArrayList<>();
        mPhotos = new ArrayList<>();
    }

    // Video

    private void startVideo() {
        mVideoIndex = Math.floorMod(mPrefs.raw().getInt(KEY_VIDEO_INDEX, 0), mVideos.size());
        mVideoView.setVisibility(View.VISIBLE);
        if (mVideoView.isAvailable()) {
            openVideo(mVideoView.getSurfaceTexture());
        }
        // Otherwise onSurfaceTextureAvailable() opens it.
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (mRunning && !mVideos.isEmpty() && mPlayer == null) {
            openVideo(texture);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        applyCrop();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        releasePlayer(true);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
    }

    private void openVideo(SurfaceTexture texture) {
        releasePlayer(false);
        final File file = mVideos.get(mVideoIndex);
        final MediaPlayer player = new MediaPlayer();
        mPlayer = player;
        mCurrentVideo = file;
        mVideoWidth = 0;
        mVideoHeight = 0;
        try {
            mSurface = new Surface(texture);
            player.setSurface(mSurface);
            player.setDataSource(file.getPath());
            player.setVolume(0f, 0f);
            // No MediaPlayer looping: on this hardware it can stall silently at the end.
            // Completion reopens the video instead (see below).
            player.setLooping(false);
            player.setOnVideoSizeChangedListener((mp, w, h) -> {
                mVideoWidth = w;
                mVideoHeight = h;
                applyCrop();
            });
            player.setOnPreparedListener(mp -> {
                if (mp != mPlayer) {
                    return;
                }
                int resume = mPrefs.raw().getInt(positionKey(file), 0);
                if (resume > 0 && resume < mp.getDuration() - 5_000) {
                    mp.seekTo(resume);
                }
                mp.start();
                mLastPosition = -1;
                mHandler.removeCallbacks(mSavePosition);
                mHandler.postDelayed(mSavePosition, SAVE_POSITION_MS);
            });
            player.setOnCompletionListener(mp -> {
                // Next video, or the same one again from the start when there's only one.
                mPrefs.raw().edit().remove(positionKey(file)).apply();
                nextVideo();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "Can't play " + file + " (" + what + ", " + extra + ")");
                dropVideo(file);
                return true;
            });
            player.prepareAsync();
        } catch (IOException | IllegalStateException | SecurityException e) {
            Log.w(TAG, "Can't open " + file, e);
            dropVideo(file);
        }
    }

    private void nextVideo() {
        if (!mRunning || mVideos.isEmpty()) {
            return;
        }
        mVideoIndex = (mVideoIndex + 1) % mVideos.size();
        mPrefs.raw().edit().putInt(KEY_VIDEO_INDEX, mVideoIndex).apply();
        if (mVideoView.isAvailable()) {
            openVideo(mVideoView.getSurfaceTexture());
        }
    }

    /** Skips a video that won't play; falls back to photos when none are left. */
    private void dropVideo(File file) {
        releasePlayer(false);
        mVideos.remove(file);
        if (mVideos.isEmpty()) {
            mVideoView.setVisibility(View.GONE);
            if ("auto".equals(mPrefs.background())) {
                final int generation = mGeneration;
                mExecutor.execute(() -> {
                    List<File> photos = new ArrayList<>();
                    scan(PHOTO_DIR, PHOTO_EXT, photos, 0);
                    Collections.shuffle(photos);
                    mHandler.post(() -> {
                        if (generation == mGeneration && mRunning && !photos.isEmpty()) {
                            mPhotos = photos;
                            advancePhoto();
                        }
                    });
                });
            }
            return;
        }
        mVideoIndex = mVideoIndex % mVideos.size();
        if (mVideoView.isAvailable()) {
            openVideo(mVideoView.getSurfaceTexture());
        }
    }

    private boolean isStalled() {
        if (!mRunning || mPlayer == null) {
            return false;
        }
        int position;
        try {
            position = mPlayer.getCurrentPosition();
        } catch (IllegalStateException e) {
            return true;
        }
        boolean stalled = position == mLastPosition;
        mLastPosition = position;
        return stalled;
    }

    private void savePosition() {
        if (mPlayer == null || mCurrentVideo == null) {
            return;
        }
        try {
            if (mPlayer.isPlaying()) {
                mPrefs.raw().edit()
                        .putInt(positionKey(mCurrentVideo), mPlayer.getCurrentPosition())
                        .apply();
            }
        } catch (IllegalStateException e) {
            // Not prepared yet.
        }
    }

    private void releasePlayer(boolean savePosition) {
        mHandler.removeCallbacks(mSavePosition);
        if (mPlayer != null) {
            if (savePosition) {
                savePosition();
            }
            mPlayer.release();
            mPlayer = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    private static String positionKey(File file) {
        // Size is part of the key so a replaced file with the same name starts over.
        return KEY_POSITION_PREFIX + file.getName() + ":" + file.length();
    }

    /** Scales the video to cover the whole screen, cropping the overflow evenly. */
    private void applyCrop() {
        int viewW = mVideoView.getWidth();
        int viewH = mVideoView.getHeight();
        if (viewW == 0 || viewH == 0 || mVideoWidth == 0 || mVideoHeight == 0) {
            return;
        }
        float scale = Math.max((float) viewW / mVideoWidth, (float) viewH / mVideoHeight);
        Matrix m = new Matrix();
        m.setScale(mVideoWidth * scale / viewW, mVideoHeight * scale / viewH,
                viewW / 2f, viewH / 2f);
        mVideoView.setTransform(m);
    }

    // Photos

    private void advancePhoto() {
        if (!mRunning || mPhotos.isEmpty()) {
            return;
        }
        final File file = mPhotos.get(mPhotoIndex % mPhotos.size());
        mPhotoIndex++;
        final int w = mFront.getWidth() > 0 ? mFront.getWidth() : 1280;
        final int h = mFront.getHeight() > 0 ? mFront.getHeight() : 800;
        final int generation = mGeneration;
        mExecutor.execute(() -> {
            Bitmap bitmap = decode(file, w, h);
            mHandler.post(() -> {
                if (generation != mGeneration || !mRunning) {
                    return;
                }
                if (bitmap != null) {
                    crossfade(bitmap);
                    mHandler.postDelayed(mAdvancePhoto, mPrefs.photoIntervalSeconds() * 1000L);
                } else {
                    // Unreadable file; move on quickly.
                    mHandler.postDelayed(mAdvancePhoto, 1000L);
                }
            });
        });
    }

    private void crossfade(Bitmap bitmap) {
        ImageView incoming = mBack;
        ImageView outgoing = mFront;
        incoming.setImageBitmap(bitmap);
        incoming.animate().alpha(1f).setDuration(FADE_MS).start();
        outgoing.animate().alpha(0f).setDuration(FADE_MS)
                .withEndAction(() -> outgoing.setImageDrawable(null)).start();
        mFront = incoming;
        mBack = outgoing;
    }

    private static void scan(File dir, String[] extensions, List<File> out, int depth) {
        File[] files = dir.listFiles();
        if (files == null || depth > 3) {
            return;
        }
        for (File f : files) {
            if (out.size() >= MAX_FILES) {
                return;
            }
            if (f.isDirectory()) {
                scan(f, extensions, out, depth + 1);
                continue;
            }
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (name.startsWith(".")) {
                continue;
            }
            for (String ext : extensions) {
                if (name.endsWith(ext)) {
                    out.add(f);
                    break;
                }
            }
        }
    }

    /** Decodes a photo at roughly screen size, upright, in a memory-light format. */
    private static Bitmap decode(File file, int reqW, int reqH) {
        String path = file.getPath();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int rotation = exifRotation(path);
        int srcW = rotation % 180 == 0 ? bounds.outWidth : bounds.outHeight;
        int srcH = rotation % 180 == 0 ? bounds.outHeight : bounds.outWidth;
        int sample = 1;
        while (srcW / (sample * 2) >= reqW && srcH / (sample * 2) >= reqH) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeFile(path, opts);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "Out of memory decoding " + path);
            return null;
        }
        if (bitmap == null || rotation == 0) {
            return bitmap;
        }
        Matrix m = new Matrix();
        m.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                m, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }

    private static int exifRotation(String path) {
        try {
            switch (new ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }
}
