package io.github.floatmemo.android;

/** Shared bounds math, independent of Android so display changes can be unit tested. */
public final class WindowGeometry {
    public final int x, y, width, height;

    private WindowGeometry(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    public static WindowGeometry fit(int x, int y, int width, int height, int screenWidth, int screenHeight) {
        int sw = Math.max(1, screenWidth), sh = Math.max(1, screenHeight);
        int w = Math.min(sw, Math.max(Math.min(220, sw), width));
        int h = Math.min(sh, Math.max(Math.min(180, sh), height));
        return new WindowGeometry(Math.max(0, Math.min(x, sw - w)),
            Math.max(0, Math.min(y, sh - h)), w, h);
    }
}
