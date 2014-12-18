// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

import android.app.PendingIntent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

/**
 * Utility class to use new APIs that were added after ICS (API level 14).
 */
public class ApiCompatibilityUtils {

    private static final String TAG = "ApiCompatibilityUtils";

    private ApiCompatibilityUtils() {
    }

    /**
     * Returns true if view's layout direction is right-to-left.
     *
     * @param view the View whose layout is being considered
     */
    public static boolean isLayoutRtl(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        } else {
            // All layouts are LTR before JB MR1.
            return false;
        }
    }

    /**
     * @see Configuration#getLayoutDirection()
     */
    public static int getLayoutDirection(Configuration configuration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return configuration.getLayoutDirection();
        } else {
            // All layouts are LTR before JB MR1.
            return View.LAYOUT_DIRECTION_LTR;
        }
    }

    /**
     * @return True if the running version of the Android supports printing.
     */
    public static boolean isPrintingSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    /**
     * @return True if the running version of the Android supports HTML clipboard.
     */
    public static boolean isHTMLClipboardSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    /**
     * @see android.view.View#setLayoutDirection(int)
     */
    public static void setLayoutDirection(View view, int layoutDirection) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            view.setLayoutDirection(layoutDirection);
        } else {
            // Do nothing. RTL layouts aren't supported before JB MR1.
        }
    }

    /**
     * @see android.view.View#setTextDirection(int)
     */
    public static void setTextAlignment(View view, int textAlignment) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            view.setTextAlignment(textAlignment);
        } else {
            // Do nothing. RTL text isn't supported before JB MR1.
        }
    }

    /**
     * @see android.view.ViewGroup.MarginLayoutParams#setMarginEnd(int)
     */
    public static void setMarginEnd(MarginLayoutParams layoutParams, int end) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            layoutParams.setMarginEnd(end);
        } else {
            layoutParams.rightMargin = end;
        }
    }

    /**
     * @see android.view.ViewGroup.MarginLayoutParams#getMarginEnd()
     */
    public static int getMarginEnd(MarginLayoutParams layoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return layoutParams.getMarginEnd();
        } else {
            return layoutParams.rightMargin;
        }
    }

    /**
     * @see android.view.ViewGroup.MarginLayoutParams#setMarginStart(int)
     */
    public static void setMarginStart(MarginLayoutParams layoutParams, int start) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            layoutParams.setMarginStart(start);
        } else {
            layoutParams.leftMargin = start;
        }
    }

    /**
     * @see android.view.ViewGroup.MarginLayoutParams#getMarginStart()
     */
    public static int getMarginStart(MarginLayoutParams layoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return layoutParams.getMarginStart();
        } else {
            return layoutParams.leftMargin;
        }
    }

    /**
     * @see android.view.View#setPaddingRelative(int, int, int, int)
     */
    public static void setPaddingRelative(View view, int start, int top, int end, int bottom) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            view.setPaddingRelative(start, top, end, bottom);
        } else {
            // Before JB MR1, all layouts are left-to-right, so start == left, etc.
            view.setPadding(start, top, end, bottom);
        }
    }

    /**
     * @see android.view.View#getPaddingStart()
     */
    public static int getPaddingStart(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return view.getPaddingStart();
        } else {
            // Before JB MR1, all layouts are left-to-right, so start == left.
            return view.getPaddingLeft();
        }
    }

    /**
     * @see android.view.View#getPaddingEnd()
     */
    public static int getPaddingEnd(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return view.getPaddingEnd();
        } else {
            // Before JB MR1, all layouts are left-to-right, so end == right.
            return view.getPaddingRight();
        }
    }

    /**
     * @see android.widget.TextView#setCompoundDrawablesRelative(Drawable, Drawable, Drawable,
     *      Drawable)
     */
    public static void setCompoundDrawablesRelative(TextView textView, Drawable start, Drawable top,
            Drawable end, Drawable bottom) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // On JB MR1, due to a platform bug, setCompoundDrawablesRelative() is a no-op if the
            // view has ever been measured. As a workaround, use setCompoundDrawables() directly.
            // See: http://crbug.com/368196 and http://crbug.com/361709
            boolean isRtl = isLayoutRtl(textView);
            textView.setCompoundDrawables(isRtl ? end : start, top, isRtl ? start : end, bottom);
        } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
            textView.setCompoundDrawablesRelative(start, top, end, bottom);
        } else {
            textView.setCompoundDrawables(start, top, end, bottom);
        }
    }

    /**
     * @see android.widget.TextView#setCompoundDrawablesRelativeWithIntrinsicBounds(Drawable,
     *      Drawable, Drawable, Drawable)
     */
    public static void setCompoundDrawablesRelativeWithIntrinsicBounds(TextView textView,
            Drawable start, Drawable top, Drawable end, Drawable bottom) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // Work around the platform bug described in setCompoundDrawablesRelative() above.
            boolean isRtl = isLayoutRtl(textView);
            textView.setCompoundDrawablesWithIntrinsicBounds(isRtl ? end : start, top,
                    isRtl ? start : end, bottom);
        } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, end, bottom);
        } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(start, top, end, bottom);
        }
    }

    /**
     * @see android.widget.TextView#setCompoundDrawablesRelativeWithIntrinsicBounds(int, int, int,
     *      int)
     */
    public static void setCompoundDrawablesRelativeWithIntrinsicBounds(TextView textView,
            int start, int top, int end, int bottom) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // Work around the platform bug described in setCompoundDrawablesRelative() above.
            boolean isRtl = isLayoutRtl(textView);
            textView.setCompoundDrawablesWithIntrinsicBounds(isRtl ? end : start, top,
                    isRtl ? start : end, bottom);
        } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, end, bottom);
        } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(start, top, end, bottom);
        }
    }

    /**
     * @see android.view.View#postInvalidateOnAnimation()
     */
    public static void postInvalidateOnAnimation(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.postInvalidateOnAnimation();
        } else {
            view.postInvalidate();
        }
    }

    /**
     * @see android.widget.RemoteViews#setContentDescription(int, CharSequence)
     */
    public static void setContentDescriptionForRemoteView(RemoteViews remoteViews, int viewId,
            CharSequence contentDescription) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            remoteViews.setContentDescription(viewId, contentDescription);
        } else {
            // setContentDescription() is unavailable in earlier versions.
        }
    }

    // These methods have a new name, and the old name is deprecated.

    /**
     * @see android.view.View#setBackground(Drawable)
     */
    @SuppressWarnings("deprecation")
    public static void setBackgroundForView(View view, Drawable drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.setBackground(drawable);
        } else {
            view.setBackgroundDrawable(drawable);
        }
    }

    /**
     * @see android.view.ViewTreeObserver#removeOnGlobalLayoutListener()
     */
    @SuppressWarnings("deprecation")
    public static void removeOnGlobalLayoutListener(
            View view, ViewTreeObserver.OnGlobalLayoutListener listener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        } else {
            view.getViewTreeObserver().removeGlobalOnLayoutListener(listener);
        }
    }

    /**
     * @see android.widget.ImageView#setImageAlpha(int)
     */
    @SuppressWarnings("deprecation")
    public static void setImageAlpha(ImageView iv, int alpha) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            iv.setImageAlpha(alpha);
        } else {
            iv.setAlpha(alpha);
        }
    }

    /**
     * @see android.app.PendingIntent#getCreatorPackage()
     */
    @SuppressWarnings("deprecation")
    public static String getCreatorPackage(PendingIntent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return intent.getCreatorPackage();
        } else {
            return intent.getTargetPackage();
        }
    }

    public static boolean datePickerRequiresAccept() {
        // TODO(miguelg) use the final code for the L
        // https://crbug.com/399198
        return Build.VERSION.SDK_INT <  20; /* CUR_DEVELOPMENT */
    }
}
