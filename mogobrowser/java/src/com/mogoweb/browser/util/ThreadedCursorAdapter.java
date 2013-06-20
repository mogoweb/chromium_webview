/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mogoweb.browser.util;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
//import android.os.SystemProperties;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.CursorAdapter;

import com.mogoweb.browser.R;

import java.lang.ref.WeakReference;

public abstract class ThreadedCursorAdapter<T> extends BaseAdapter {

    private static final String LOGTAG = "BookmarksThreadedAdapter";
    private static final boolean DEBUG = false;

    private Context mContext;
    private Object mCursorLock = new Object();
    private CursorAdapter mCursorAdapter;
    private T mLoadingObject;
    private Handler mLoadHandler;
    private Handler mHandler;
    private int mSize;
    private boolean mHasCursor;
    private long mGeneration;

    private class LoadContainer {
        WeakReference<View> view;
        int position;
        T bind_object;
        Adapter owner;
        boolean loaded;
        long generation;
    }

    public ThreadedCursorAdapter(Context context, Cursor c) {
        mContext = context;
        mHasCursor = (c != null);
        mCursorAdapter = new CursorAdapter(context, c, 0) {

            @Override
            public View newView(Context context, Cursor cursor, ViewGroup parent) {
                throw new IllegalStateException("not supported");
            }

            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                throw new IllegalStateException("not supported");
            }

            @Override
            public void notifyDataSetChanged() {
                super.notifyDataSetChanged();
                mSize = getCount();
                mGeneration++;
                ThreadedCursorAdapter.this.notifyDataSetChanged();
            }

            @Override
            public void notifyDataSetInvalidated() {
                super.notifyDataSetInvalidated();
                mSize = getCount();
                mGeneration++;
                ThreadedCursorAdapter.this.notifyDataSetInvalidated();
            }

        };
        mSize = mCursorAdapter.getCount();
        HandlerThread thread = new HandlerThread("threaded_adapter_" + this,
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mLoadHandler = new Handler(thread.getLooper()) {
            @SuppressWarnings("unchecked")
            @Override
            public void handleMessage(Message msg) {
                if (DEBUG) {
                    Log.d(LOGTAG, "loading: " + msg.what);
                }
                loadRowObject(msg.what, (LoadContainer) msg.obj);
            }
        };
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                @SuppressWarnings("unchecked")
                LoadContainer container = (LoadContainer) msg.obj;
                if (container == null) {
                    return;
                }
                View view = container.view.get();
                if (view == null
                        || container.owner != ThreadedCursorAdapter.this
                        || container.position != msg.what
                        || view.getWindowToken() == null
                        || container.generation != mGeneration) {
                    return;
                }
                container.loaded = true;
                bindView(view, container.bind_object);
            }
        };
    }

    @Override
    public int getCount() {
        return mSize;
    }

    @Override
    public Cursor getItem(int position) {
        return (Cursor) mCursorAdapter.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        synchronized (mCursorLock) {
            return getItemId(getItem(position));
        }
    }

    private void loadRowObject(int position, LoadContainer container) {
        if (container == null
                || container.position != position
                || container.owner != ThreadedCursorAdapter.this
                || container.view.get() == null) {
            return;
        }
        synchronized (mCursorLock) {
            Cursor c = (Cursor) mCursorAdapter.getItem(position);
            if (c == null || c.isClosed()) {
                return;
            }
            container.bind_object = getRowObject(c, container.bind_object);
        }
        mHandler.obtainMessage(position, container).sendToTarget();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = newView(mContext, parent);
        }
        @SuppressWarnings("unchecked")
        LoadContainer container = (LoadContainer) convertView.getTag(R.id.load_object);
        if (container == null) {
            container = new LoadContainer();
            container.view = new WeakReference<View>(convertView);
            convertView.setTag(R.id.load_object, container);
        }
        if (container.position == position
                && container.owner == this
                && container.loaded
                && container.generation == mGeneration) {
            bindView(convertView, container.bind_object);
        } else {
            bindView(convertView, cachedLoadObject());
            if (mHasCursor) {
                container.position = position;
                container.loaded = false;
                container.owner = this;
                container.generation = mGeneration;
                mLoadHandler.obtainMessage(position, container).sendToTarget();
            }
        }
        return convertView;
    }

    private T cachedLoadObject() {
        if (mLoadingObject == null) {
            mLoadingObject = getLoadingObject();
        }
        return mLoadingObject;
    }

    public void changeCursor(Cursor cursor) {
        mLoadHandler.removeCallbacksAndMessages(null);
        mHandler.removeCallbacksAndMessages(null);
        synchronized (mCursorLock) {
            mHasCursor = (cursor != null);
            mCursorAdapter.changeCursor(cursor);
        }
    }

    public abstract View newView(Context context, ViewGroup parent);
    public abstract void bindView(View view, T object);
    public abstract T getRowObject(Cursor c, T recycleObject);
    public abstract T getLoadingObject();
    protected abstract long getItemId(Cursor c);
}