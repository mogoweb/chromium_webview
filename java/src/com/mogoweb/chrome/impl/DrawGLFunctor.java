// Copyright (c) 2014 mogoweb. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.mogoweb.chrome.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.chromium.content.common.CleanupReference;

import android.graphics.Canvas;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;

// Simple Java abstraction and wrapper for the native DrawGLFunctor flow.
// An instance of this class can be constructed, bound to a single view context (i.e. AwContennts)
// and then drawn and detached from the view tree any number of times (using requestDrawGL and
// detach respectively). Then when finished with, it can be explicitly released by calling
// destroy() or will clean itself up as required via finalizer / CleanupReference.
public class DrawGLFunctor {

    private static final String TAG = DrawGLFunctor.class.getSimpleName();

    private static final String HARDWARE_CANVAS_CLASS = "android.view.HardwareCanvas";
    private static final String VIEW_ROOT_IMPL_CLASS = "android.view.ViewRootImpl";

    // Pointer to native side instance
    private CleanupReference mCleanupReference;
    private DestroyRunnable mDestroyRunnable;
    Class<?> mClassTypeOfHardwareCanvas;
    Class<?> mClassTypeOfViewRootImpl;
    Method mGetViewRootImplMethod;
    Method mCallDrawGLFunction;
    Method mAttachFunctor;
    Method mDetachFunctor;

    public DrawGLFunctor(int viewContext) {
        mDestroyRunnable = new DestroyRunnable(nativeCreateGLFunctor(viewContext));
        mCleanupReference = new CleanupReference(this, mDestroyRunnable);

        try {
            mClassTypeOfHardwareCanvas = Class.forName(HARDWARE_CANVAS_CLASS);
            mClassTypeOfViewRootImpl = Class.forName(VIEW_ROOT_IMPL_CLASS);
            // in Android ICS, getViewRootImpl is not public and attachFunctor/detachFunctor not available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mGetViewRootImplMethod = View.class.getMethod("getViewRootImpl", new Class[]{});
                mAttachFunctor = mClassTypeOfViewRootImpl.getMethod("attachFunctor", new Class[]{int.class});
                mDetachFunctor = mClassTypeOfViewRootImpl.getMethod("detachFunctor", new Class[]{int.class});
            }
            mCallDrawGLFunction = mClassTypeOfHardwareCanvas.getMethod("callDrawGLFunction", new Class[]{int.class});
        } catch (ClassNotFoundException exception) {
            Log.e(TAG, "ClassNotFoundException occured: " + exception.getMessage());
        } catch (NoSuchMethodException exception) {
            Log.e(TAG, "NoSuchMethodException occured: " + exception.getMessage());
        }
    }

    public void destroy() {
        if (mCleanupReference != null) {
            mCleanupReference.cleanupNow();
            mCleanupReference = null;
            mDestroyRunnable = null;
        }
    }

    public void detach() {
        mDestroyRunnable.detachNativeFunctor();
    }

    public boolean requestDrawGL(Canvas canvas, View view) {
        if (mDestroyRunnable.mNativeDrawGLFunctor == 0) {
            throw new RuntimeException("requested DrawGL on already destroyed DrawGLFunctor");
        }
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                View root = view.getRootView();
                mDestroyRunnable.mViewRootImpl = root != null ? (ViewParent)root.getParent() : null;
            } else {
                mDestroyRunnable.mViewRootImpl = (ViewParent)mGetViewRootImplMethod.invoke(view, new Object[]{});
            }
            if (canvas != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    Boolean i = (Boolean)mCallDrawGLFunction.invoke(canvas, new Object[]{Integer.valueOf(mDestroyRunnable.mNativeDrawGLFunctor)});
                    boolean ret =  i.booleanValue();
                    if (!ret) {
                        Log.e(TAG, "callDrawGLFunction error: " + ret);
                        return false;
                    }
                } else {
                    Integer i = (Integer)mCallDrawGLFunction.invoke(canvas, new Object[]{Integer.valueOf(mDestroyRunnable.mNativeDrawGLFunctor)});
                    int ret =  i.intValue();
                    if (ret != 0) {
                        Log.e(TAG, "callDrawGLFunction error: " + ret);
                        return false;
                    }
                }
            } else {
                if (mDestroyRunnable.mViewRootImpl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                    mAttachFunctor.invoke(mDestroyRunnable.mViewRootImpl, new Object[]{Integer.valueOf(mDestroyRunnable.mNativeDrawGLFunctor)});
            }
        } catch (IllegalAccessException exception) {
            Log.e(TAG, "IllegalAccessException:" + exception.getMessage());
            exception.printStackTrace();
        } catch (IllegalArgumentException exception) {
            Log.e(TAG, "IllegalArgumentException:" + exception.getMessage());
            exception.printStackTrace();
        } catch (InvocationTargetException exception) {
            Log.e(TAG, "InvocationTargetException:" + exception.getMessage());
            exception.printStackTrace();
        }
        return true;

    }

    public static void setChromiumAwDrawGLFunction(int functionPointer) {
        nativeSetChromiumAwDrawGLFunction(functionPointer);
    }

    // Holds the core resources of the class, everything required to correctly cleanup.
    // IMPORTANT: this class must not hold any reference back to the outer DrawGLFunctor
    // instance, as that will defeat GC of that object.
    private class DestroyRunnable implements Runnable {
        ViewParent mViewRootImpl;
        int mNativeDrawGLFunctor;
        DestroyRunnable(int nativeDrawGLFunctor) {
            mNativeDrawGLFunctor = nativeDrawGLFunctor;
        }

        // Called when the outer DrawGLFunctor instance has been GC'ed, i.e this is its finalizer.
        @Override
        public void run() {
            detachNativeFunctor();
            nativeDestroyGLFunctor(mNativeDrawGLFunctor);
            mNativeDrawGLFunctor = 0;
        }

        void detachNativeFunctor() {
            if (mNativeDrawGLFunctor != 0 && mViewRootImpl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                try {
                    mDetachFunctor.invoke(mViewRootImpl, new Object[]{Integer.valueOf(mNativeDrawGLFunctor)});
                } catch (IllegalAccessException exception) {
                    Log.e(TAG, "illegalAccessException:" + exception.getMessage());
                    exception.printStackTrace();
                } catch (IllegalArgumentException exception) {
                    Log.e(TAG, "IllegalArgumentException:" + exception.getMessage());
                    exception.printStackTrace();
                } catch (InvocationTargetException exception) {
                    Log.e(TAG, "InvocationTargetException:" + exception.getMessage());
                    exception.printStackTrace();
                }
            }
            mViewRootImpl = null;
        }
    }

    private static native int nativeCreateGLFunctor(int viewContext);
    private static native void nativeDestroyGLFunctor(int functor);
    private static native void nativeSetChromiumAwDrawGLFunction(int functionPointer);
}
