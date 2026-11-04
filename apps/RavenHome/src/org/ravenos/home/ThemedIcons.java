package org.ravenos.home;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

/**
 * Redraws any app's icon in the RavenOS look, so every icon on All apps matches whatever is
 * installed later: a dark glass tile with a faint amber glow, and the app's mark on it in a warm
 * white-to-amber gradient.
 *
 * <p>An adaptive icon whose foreground is a simple symbol gives the mark directly. Anything
 * else (full-colour artwork, older square icons) is turned into a two-tone picture in the same
 * colours, dark where the original is dark and bright where it is bright, so its shape still
 * reads.
 */
final class ThemedIcons {
    /** Above this share of the visible area, a foreground is artwork rather than a symbol. */
    private static final float MAX_SYMBOL_COVERAGE = 0.42f;
    /** The darkest shade inside a symbol: deep amber-brown, still visible on the tile. */
    private static final int SHADE = 0xFF7A4A12;

    /**
     * Built-in apps whose own icons don't reduce well to a symbol get a hand-drawn one; every
     * other app, including ones installed later, is redrawn automatically.
     */
    private static final java.util.Map<String, Integer> GLYPHS = new java.util.HashMap<>();

    static {
        GLYPHS.put("com.android.documentsui", R.drawable.glyph_folder);
        GLYPHS.put("com.android.camera2", R.drawable.glyph_camera);
    }

    private final Context mContext;
    private final PackageManager mPm;
    private final int mSize;
    private final int mTop;
    private final int mBottom;
    private final int mGlow;
    private final int mTileTop;
    private final int mTileBottom;
    private final int mStroke;
    private final LruCache<String, Bitmap> mCache = new LruCache<>(128);

    ThemedIcons(Context context, int sizePx) {
        mContext = context;
        mPm = context.getPackageManager();
        mSize = sizePx;
        mTop = 0xFFFFF4E6;
        mBottom = context.getColor(R.color.raven_accent) | 0xFF000000;
        mGlow = (mBottom & 0xFFFFFF) | 0x38000000;
        mTileTop = 0xFF24242A;
        mTileBottom = 0xFF121215;
        mStroke = 0x2EFFFFFF;
    }

    /** The themed icon for an activity, from the cache when the app hasn't changed. */
    Bitmap get(ActivityInfo info) {
        String key = info.packageName + "/" + info.name + "@" + updateTime(info.packageName);
        Bitmap cached = mCache.get(key);
        if (cached != null) {
            return cached;
        }
        Integer glyph = GLYPHS.get(info.packageName);
        Bitmap themed = glyph != null ? themeGlyph(mContext.getDrawable(glyph))
                : theme(info.loadIcon(mPm));
        mCache.put(key, themed);
        return themed;
    }

    private long updateTime(String pkg) {
        try {
            PackageInfo pi = mPm.getPackageInfo(pkg, 0);
            return pi.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    /** A tile with a hand-drawn symbol on it in the gradient. */
    private Bitmap themeGlyph(Drawable glyph) {
        return theme(null, glyph);
    }

    Bitmap theme(Drawable icon) {
        return theme(icon, null);
    }

    private Bitmap theme(Drawable icon, Drawable glyph) {
        Bitmap out = Bitmap.createBitmap(mSize, mSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        RectF tile = new RectF(0, 0, mSize, mSize);
        float radius = mSize * 0.28f;

        // The glass tile, lit faintly amber from the top left.
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, 0, 0, mSize, mTileTop, mTileBottom,
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(tile, radius, radius, fill);
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(mSize * 0.25f, mSize * 0.2f, mSize * 0.9f,
                mGlow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(tile, radius, radius, glow);

        if (glyph != null) {
            Bitmap mask = Bitmap.createBitmap(mSize, mSize, Bitmap.Config.ARGB_8888);
            glyph.setBounds(0, 0, mSize, mSize);
            glyph.draw(new Canvas(mask));
            drawGradient(canvas, mask);
            drawEdge(canvas, radius);
            return out;
        }
        Bitmap toned = contrastSymbol(icon);
        Bitmap mark = toned == null ? symbol(icon) : null;
        if (toned != null) {
            drawFitted(canvas, toned);
        } else if (mark != null) {
            drawGradient(canvas, mark);
        } else {
            drawTwoTone(canvas, icon, radius);
        }

        drawEdge(canvas, radius);
        return out;
    }

    /** The tile's hairline glass edge. */
    private void drawEdge(Canvas canvas, float radius) {
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        float width = Math.max(1f, mSize / 96f);
        stroke.setStrokeWidth(width);
        stroke.setColor(mStroke);
        RectF edge = new RectF(width / 2, width / 2, mSize - width / 2, mSize - width / 2);
        canvas.drawRoundRect(edge, radius, radius, stroke);
    }

    /**
     * The foreground of an adaptive icon as an alpha mask, cropped to the part a launcher
     * shows, or null when it isn't a simple symbol.
     */
    private Bitmap symbol(Drawable icon) {
        if (!(icon instanceof AdaptiveIconDrawable)) {
            return null;
        }
        Drawable fg = ((AdaptiveIconDrawable) icon).getForeground();
        if (fg == null) {
            return null;
        }
        // The foreground layer is 108 units with the visible 72 in the middle.
        int layer = Math.round(mSize * 1.5f);
        Bitmap full = Bitmap.createBitmap(layer, layer, Bitmap.Config.ARGB_8888);
        fg.setBounds(0, 0, layer, layer);
        fg.draw(new Canvas(full));
        int inset = (layer - mSize) / 2;
        Bitmap mask = Bitmap.createBitmap(full, inset, inset, mSize, mSize);
        full.recycle();

        int[] px = new int[mSize * mSize];
        mask.getPixels(px, 0, mSize, 0, 0, mSize, mSize);
        long covered = 0;
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            if (a > 128) {
                covered++;
            }
            // Faint shadows and glows around a symbol aren't part of it.
            px[i] = (a < 72 ? 0 : a) << 24;
        }
        if (covered == 0 || covered > px.length * MAX_SYMBOL_COVERAGE) {
            mask.recycle();
            return null;
        }
        mask.setPixels(px, 0, mSize, 0, 0, mSize, mSize);
        return mask;
    }

    /**
     * The parts of an icon that stand out from its background colour, as an alpha mask: the
     * folder on Files, the lens on Camera. Null when nothing clearly stands out.
     */
    private Bitmap contrastSymbol(Drawable icon) {
        Bitmap src = Bitmap.createBitmap(mSize, mSize, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, mSize, mSize);
        icon.draw(new Canvas(src));
        int[] px = new int[mSize * mSize];
        src.getPixels(px, 0, mSize, 0, 0, mSize, mSize);

        // The background colour: the median of what's just inside the icon's edge.
        int ring = Math.max(2, mSize / 12);
        java.util.List<Integer> r = new java.util.ArrayList<>();
        java.util.List<Integer> g = new java.util.ArrayList<>();
        java.util.List<Integer> b = new java.util.ArrayList<>();
        for (int y = 0; y < mSize; y++) {
            for (int x = 0; x < mSize; x++) {
                int p = px[y * mSize + x];
                if ((p >>> 24) < 220) {
                    continue;
                }
                // Inside the shape, near its edge: some transparent pixel within `ring`.
                int ox = Math.min(x, mSize - 1 - x);
                int oy = Math.min(y, mSize - 1 - y);
                boolean nearEdge = ox < ring * 2 || oy < ring * 2
                        || nearTransparent(px, x, y, ring);
                if (nearEdge) {
                    r.add((p >> 16) & 0xFF);
                    g.add((p >> 8) & 0xFF);
                    b.add(p & 0xFF);
                }
            }
        }
        if (r.size() < 8) {
            src.recycle();
            return null;
        }
        int[] orig = px.clone();
        float[] lum = new float[px.length];
        // Against the colour at the edge first; if nearly everything stands out from that (a
        // big coloured disc filling the icon), against the icon's main colour instead.
        boolean found = mask(orig, px, lum, median(r), median(g), median(b));
        if (!found) {
            java.util.List<Integer> ar = new java.util.ArrayList<>();
            java.util.List<Integer> ag = new java.util.ArrayList<>();
            java.util.List<Integer> ab = new java.util.ArrayList<>();
            for (int p : orig) {
                if ((p >>> 24) >= 220) {
                    ar.add((p >> 16) & 0xFF);
                    ag.add((p >> 8) & 0xFF);
                    ab.add(p & 0xFF);
                }
            }
            found = !ar.isEmpty() && mask(orig, px, lum, median(ar), median(ag), median(ab));
        }
        if (!found) {
            src.recycle();
            return null;
        }

        // Shade the symbol by its own light and dark parts in the amber palette, so detail
        // inside it (a lens, the hole in a gear) survives. A one-colour symbol gets the
        // white-to-amber gradient instead.
        java.util.List<Float> levels = new java.util.ArrayList<>();
        for (int i = 0; i < px.length; i++) {
            if ((px[i] >>> 24) > 128) {
                levels.add(lum[i]);
            }
        }
        java.util.Collections.sort(levels);
        float lo = levels.get(levels.size() / 10);
        float hi = levels.get(levels.size() * 9 / 10);
        if (hi - lo < 0.12f) {
            src.setPixels(px, 0, mSize, 0, 0, mSize, mSize);
            return tint(src);
        }
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            if (a == 0) {
                continue;
            }
            float t = Math.max(0f, Math.min(1f, (lum[i] - lo) / (hi - lo)));
            int c = t < 0.5f ? blend(SHADE, mBottom, t * 2) : blend(mBottom, mTop, (t - 0.5f) * 2);
            px[i] = (a << 24) | (c & 0xFFFFFF);
        }
        src.setPixels(px, 0, mSize, 0, 0, mSize, mSize);
        return src;
    }

    /**
     * Fills {@code out} with an alpha mask of the pixels that differ from the given background
     * colour, and {@code lum} with their brightness. False if that picks out too little or
     * nearly everything.
     */
    private static boolean mask(int[] src, int[] out, float[] lum, int br, int bg, int bb) {
        long covered = 0;
        long inside = 0;
        for (int i = 0; i < src.length; i++) {
            int p = src[i];
            int a = p >>> 24;
            if (a < 128) {
                out[i] = 0;
                continue;
            }
            inside++;
            float dr = ((p >> 16) & 0xFF) - br;
            float dg = ((p >> 8) & 0xFF) - bg;
            float db = (p & 0xFF) - bb;
            float d = (float) Math.sqrt(dr * dr + dg * dg + db * db) / 441f;
            float m = Math.max(0f, Math.min(1f, (d - 0.10f) / 0.18f));
            if (m > 0.5f) {
                covered++;
            }
            lum[i] = luma(p);
            out[i] = Math.round(m * a) << 24;
        }
        return inside > 0 && covered >= inside * 0.03f && covered <= inside * 0.7f;
    }

    private static boolean nearTransparent(int[] px, int x, int y, int ring) {
        int size = (int) Math.sqrt(px.length);
        for (int[] d : new int[][] {{ring, 0}, {-ring, 0}, {0, ring}, {0, -ring}}) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx < 0 || ny < 0 || nx >= size || ny >= size
                    || (px[ny * size + nx] >>> 24) < 128) {
                return true;
            }
        }
        return false;
    }

    private static int median(java.util.List<Integer> values) {
        java.util.Collections.sort(values);
        return values.get(values.size() / 2);
    }

    /**
     * Fills a mask with the warm white-to-amber gradient, scaled so its outline fills the same
     * share of every tile.
     */
    private void drawGradient(Canvas canvas, Bitmap mask) {
        Bitmap tinted = tint(mask);
        if (tinted != null) {
            drawFitted(canvas, tinted);
        }
    }

    /** A mask filled top to bottom with the warm white-to-amber gradient. */
    private Bitmap tint(Bitmap mask) {
        android.graphics.Rect box = bounds(mask);
        if (box == null) {
            mask.recycle();
            return null;
        }
        Bitmap tinted = Bitmap.createBitmap(mSize, mSize, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tinted);
        c.drawBitmap(mask, 0, 0, null);
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        paint.setShader(new LinearGradient(0, box.top, 0, box.bottom, mTop, mBottom,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, mSize, mSize, paint);
        mask.recycle();
        return tinted;
    }

    /** Draws a symbol centred, scaled so its outline fills the same share of every tile. */
    private void drawFitted(Canvas canvas, Bitmap tinted) {
        android.graphics.Rect box = bounds(tinted);
        if (box == null) {
            tinted.recycle();
            return;
        }
        float scale = mSize * 0.56f / Math.max(box.width(), box.height());
        canvas.save();
        canvas.translate(mSize / 2f, mSize / 2f);
        canvas.scale(scale, scale);
        canvas.translate(-box.exactCenterX(), -box.exactCenterY());
        Paint draw = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(tinted, 0, 0, draw);
        canvas.restore();
        tinted.recycle();
    }

    /** The box around a mask's visible pixels, or null if it has none. */
    private static android.graphics.Rect bounds(Bitmap mask) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        int[] px = new int[w * h];
        mask.getPixels(px, 0, w, 0, 0, w, h);
        int left = w;
        int top = h;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 40) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        return right < 0 ? null : new android.graphics.Rect(left, top, right + 1, bottom + 1);
    }

    /** The whole icon as a two-tone picture: dark glass in the shadows, warm amber light. */
    private void drawTwoTone(Canvas canvas, Drawable icon, float radius) {
        int inner = Math.round(mSize * 0.74f);
        int offset = (mSize - inner) / 2;
        Bitmap src = Bitmap.createBitmap(inner, inner, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, inner, inner);
        icon.draw(new Canvas(src));

        int[] px = new int[inner * inner];
        src.getPixels(px, 0, inner, 0, 0, inner, inner);
        // Stretch each icon's own brightness range so dark and light icons both use the
        // full range of the two tones.
        float min = 1f;
        float max = 0f;
        for (int p : px) {
            if ((p >>> 24) > 128) {
                float l = luma(p);
                min = Math.min(min, l);
                max = Math.max(max, l);
            }
        }
        float range = Math.max(0.15f, max - min);
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            if (a == 0) {
                continue;
            }
            float t = (luma(px[i]) - min) / range;
            t = Math.max(0f, Math.min(1f, t));
            // Dark parts stay faintly visible against the tile.
            int dark = 0xFF3A2F24;
            int c = blend(dark, blend(mBottom, mTop, t), (float) Math.pow(t, 0.8));
            px[i] = (a << 24) | (c & 0xFFFFFF);
        }
        src.setPixels(px, 0, inner, 0, 0, inner, inner);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.translate(offset, offset);
        float r = radius * inner / mSize;
        canvas.drawRoundRect(new RectF(0, 0, inner, inner), r, r, paint);
        canvas.restore();
    }

    private static float luma(int c) {
        return (0.2126f * ((c >> 16) & 0xFF) + 0.7152f * ((c >> 8) & 0xFF)
                + 0.0722f * (c & 0xFF)) / 255f;
    }

    private static int blend(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
