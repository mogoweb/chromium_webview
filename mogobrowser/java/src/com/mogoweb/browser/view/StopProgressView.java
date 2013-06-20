
package com.mogoweb.browser.view;

import com.mogoweb.browser.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ProgressBar;


public class StopProgressView extends ProgressBar {

    Drawable mOverlayDrawable;
    Drawable mProgressDrawable;
    int mWidth;
    int mHeight;

    /**
     * @param context
     * @param attrs
     * @param defStyle
     * @param styleRes
     */
    public StopProgressView(Context context, AttributeSet attrs, int defStyle, int styleRes) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    /**
     * @param context
     * @param attrs
     * @param defStyle
     */
    public StopProgressView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    /**
     * @param context
     * @param attrs
     */
    public StopProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    /**
     * @param context
     */
    public StopProgressView(Context context) {
        super(context);
        init(null);
    }

    private void init(AttributeSet attrs) {
        mProgressDrawable = getIndeterminateDrawable();
        setImageDrawable(getContext().getResources()
                .getDrawable(R.drawable.ic_stop_holo_dark));
    }

    public void hideProgress() {
        setIndeterminateDrawable(null);
    }

    public void showProgress() {
        setIndeterminateDrawable(mProgressDrawable);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mWidth = (right - left) * 2 / 3;
        mHeight = (bottom - top) * 2 / 3;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mOverlayDrawable != null) {
            int l = (getWidth() - mWidth) / 2;
            int t = (getHeight() - mHeight) / 2;
            mOverlayDrawable.setBounds(l, t, l + mWidth, t + mHeight);
            mOverlayDrawable.draw(canvas);
        }
    }

    public Drawable getDrawable() {
        return mOverlayDrawable;
    }

    public void setImageDrawable(Drawable d) {
        mOverlayDrawable = d;
    }

}
