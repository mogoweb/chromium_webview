// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;

import org.chromium.chrome.R;

/**
 * Util class for bookmarks.
 */
public class BookmarkUtils {

    // There is no public string defining this intent so if Home changes the value, we
    // have to update this string.
    private static final String INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";
    private static final int DEFAULT_RGB_VALUE = 145;
    private static final String TAG = "BookmarkUtils";
    public static final String REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB =
            "REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB";
    private static final int INSET_DIMENSION_FOR_TOUCHICON = 1;
    private static final int TOUCHICON_BORDER_RADII = 10;

    /**
     * Creates an intent that will add a shortcut to the home screen.
     * @param context Context used to create the intent.
     * @param shortcutIntent Intent to fire when the shortcut is activated.
     * @param title Title of the bookmark.
     * @param favicon Bookmark favicon.
     * @param rValue Red component of the dominant favicon color.
     * @param gValue Green component of the dominant favicon color.
     * @param bValue Blue component of the dominant favicon color.
     * @return Intent for the shortcut.
     */
    public static Intent createAddToHomeIntent(Context context, Intent shortcutIntent, String title,
            Bitmap favicon, int rValue, int gValue, int bValue) {
        Intent i = new Intent(INSTALL_SHORTCUT);
        i.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        i.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
        i.putExtra(Intent.EXTRA_SHORTCUT_ICON, createIcon(context, favicon, rValue,
                gValue, bValue));
        return i;
    }

    /**
     * Creates an intent that will add a shortcut to the home screen.
     * @param context Context used to create the intent.
     * @param url Url of the bookmark.
     * @param title Title of the bookmark.
     * @param favicon Bookmark favicon.
     * @param rValue Red component of the dominant favicon color.
     * @param gValue Green component of the dominant favicon color.
     * @param bValue Blue component of the dominant favicon color.
     * @return Intent for the shortcut.
     */
    public static Intent createAddToHomeIntent(Context context, String url, String title,
            Bitmap favicon, int rValue, int gValue, int bValue) {
        Intent shortcutIntent = createShortcutIntent(context, url);
        return createAddToHomeIntent(
                context, shortcutIntent, title, favicon, rValue, gValue, bValue);
    }

    /**
     * Shortcut intent for icon on homescreen.
     * @param context Context used to create the intent.
     * @param url Url of the bookmark.
     * @return Intent for onclick action of the shortcut.
     */
    public static Intent createShortcutIntent(Context context, String url) {
        Intent shortcutIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        shortcutIntent.putExtra(REUSE_URL_MATCHING_TAB_ELSE_NEW_TAB, true);
        return shortcutIntent;
    }

    /**
     * Creates an icon to be associated with this bookmark. If available, the touch icon
     * will be used, else we draw our own.
     * @param context Context used to create the intent.
     * @param favicon Bookmark favicon bitmap.
     * @param rValue Red component of the dominant favicon color.
     * @param gValue Green component of the dominant favicon color.
     * @param bValue Blue component of the dominant favicon color.
     * @return Bitmap Either the touch-icon or the newly created favicon.
     */
    private static Bitmap createIcon(Context context, Bitmap favicon, int rValue,
            int gValue, int bValue) {
        Bitmap bitmap = null;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final int iconSize = am.getLauncherLargeIconSize();
        final int iconDensity = am.getLauncherLargeIconDensity();
        try {
            bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            if (favicon == null) {
                favicon = getBitmapFromResourceId(context, R.drawable.globe_favicon, iconDensity);
                rValue = gValue = bValue = DEFAULT_RGB_VALUE;
            }
            final int smallestSide = iconSize;
            if (favicon.getWidth() >= smallestSide / 2 && favicon.getHeight() >= smallestSide / 2) {
                drawTouchIconToCanvas(context, favicon, canvas);
            } else {
                drawWidgetBackgroundToCanvas(context, canvas, iconDensity,
                        Color.rgb(rValue, gValue, bValue));
                drawFaviconToCanvas(context, favicon, canvas);
            }
            canvas.setBitmap(null);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "OutOfMemoryError while trying to draw bitmap on canvas.");
        }
        return bitmap;
    }

    private static Bitmap getBitmapFromResourceId(Context context, int id, int density) {
        Drawable drawable = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            drawable = context.getResources().getDrawableForDensity(id, density);
        } else {
            drawable = context.getResources().getDrawable(id);
        }

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bd = (BitmapDrawable) drawable;
            return bd.getBitmap();
        }
        assert false : "The drawable was not a bitmap drawable as expected";
        return null;
    }

    /**
     * Use touch-icon or higher-resolution favicon and round the corners.
     * @param context    Context used to get resources.
     * @param touchIcon  Touch icon bitmap.
     * @param canvas     Canvas that holds the touch icon.
     */
    private static void drawTouchIconToCanvas(
            Context context, Bitmap touchIcon, Canvas canvas) {
        Rect iconBounds = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
        Rect src = new Rect(0, 0, touchIcon.getWidth(), touchIcon.getHeight());
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(touchIcon, src, iconBounds, paint);
        // Convert dp to px.
        int borderRadii = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                TOUCHICON_BORDER_RADII, context.getResources().getDisplayMetrics());
        Path path = new Path();
        path.setFillType(Path.FillType.INVERSE_WINDING);
        RectF rect = new RectF(iconBounds);
        rect.inset(INSET_DIMENSION_FOR_TOUCHICON, INSET_DIMENSION_FOR_TOUCHICON);
        path.addRoundRect(rect, borderRadii, borderRadii, Path.Direction.CW);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawPath(path, paint);
    }

    /**
     * Draw the favicon with dominant color.
     * @param context Context used to create the intent.
     * @param favicon favicon bitmap.
     * @param canvas Canvas that holds the favicon.
     */
    private static void drawFaviconToCanvas(Context context, Bitmap favicon, Canvas canvas) {
        Rect iconBounds = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
        int faviconSize = iconBounds.width() / 3;
        Bitmap scaledFavicon = Bitmap.createScaledBitmap(favicon, faviconSize, faviconSize, true);
        canvas.drawBitmap(scaledFavicon,
                iconBounds.exactCenterX() - scaledFavicon.getWidth() / 2.0f,
                iconBounds.exactCenterY() - scaledFavicon.getHeight() / 2.0f, null);
    }

    /**
     * Draw document icon to canvas.
     * @param context     Context used to get bitmap resources.
     * @param canvas      Canvas that holds the document icon.
     * @param iconDensity Density information to get bitmap resources.
     * @param color       Color for the document icon's folding and the bottom strip.
     */
    private static void drawWidgetBackgroundToCanvas(
            Context context, Canvas canvas, int iconDensity, int color) {
        Rect iconBounds = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
        Bitmap bookmark_widget_bg =
                getBitmapFromResourceId(context, R.mipmap.bookmark_widget_bg, iconDensity);
        Bitmap bookmark_widget_bg_highlights = getBitmapFromResourceId(context,
                R.mipmap.bookmark_widget_bg_highlights, iconDensity);
        if (bookmark_widget_bg == null || bookmark_widget_bg_highlights == null) {
            Log.w(TAG, "Can't load R.mipmap.bookmark_widget_bg or " +
                    "R.mipmap.bookmark_widget_bg_highlights.");
            return;
        }
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bookmark_widget_bg, null, iconBounds, paint);

        // The following color filter will convert bookmark_widget_bg_highlights' white color to
        // the 'color' variable when it is painted to 'canvas'.
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bookmark_widget_bg_highlights, null, iconBounds, paint);
    }
}
