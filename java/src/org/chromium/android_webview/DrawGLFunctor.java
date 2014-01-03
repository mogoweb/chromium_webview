/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2014 mogoweb
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
package org.chromium.android_webview;

import android.graphics.Canvas;
import android.util.Log;

import java.lang.reflect.*;

import org.chromium.content.common.CleanupReference;

// Simple Java abstraction and wrapper for the native DrawGLFunctor flow.
// An instance of this class can be constructed, bound to a single view context (i.e. AwContennts)
// and then drawn and detached from the view tree any number of times (using requestDrawGL and
// detach respectively). Then when finished with, it can be explicitly released by calling
// destroy() or will clean itself up as required via finalizer / CleanupReference.
public class DrawGLFunctor {
    private static final String TAG = DrawGLFunctor.class.getSimpleName();

    private CleanupReference mCleanupReference;
    private DestroyRunnable mDestroyRunnable;

    // Reflection cache
    private Class<?> mHardwareCanvasClass;
    private Method mCallDrawGLFunction;

    public DrawGLFunctor(int viewContext) {
        mDestroyRunnable = new DestroyRunnable(nativeCreateGLFunctor(viewContext));
        mCleanupReference = new CleanupReference(this, mDestroyRunnable);

        try {
            mHardwareCanvasClass = Class.forName("android.view.HardwareCanvas");
            mCallDrawGLFunction =
                    mHardwareCanvasClass.getDeclaredMethod("callDrawGLFunction", int.class);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Constructor: Could not find android.view.HardwareCanvas. " +
                    "The tab switcher will not show.");
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Constructor: android.view.HardwareCanvas does not have " +
                    "callDrawGLFunction. The tab switcher will not show.");
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
    }

    public boolean requestDrawGL(Canvas canvas) {
        // if (mDestroyRunnable.mNativeDrawGLFunctor == 0) {
        //     throw new RuntimeException("requested DrawGL on already destroyed DrawGLFunctor");
        // }

        // if (canvas != null && mHardwareCanvasClass.isInstance(canvas)) {
        //     try {
        //         Object hardwareCanvas = mHardwareCanvasClass.cast(canvas);
        //         Object r = mCallDrawGLFunction.invoke(hardwareCanvas, mDestroyRunnable.mNativeDrawGLFunctor);
        //         if (((r instanceof Boolean) && ((Boolean) r).booleanValue()) ||
        //                 ((r instanceof Integer) && ((Integer) r).intValue() > 0)) {
        //             Log.e(TAG, "callDrawGLFunction error: ");
        //             return false;
        //         }
        //         return true;
        //     } catch (ExceptionInInitializerError e) {
        //         Log.e(TAG, "requestDrawGL: Could not initialize android.view.HardwareCanvas.", e);
        //     } catch (LinkageError e) {
        //         Log.e(TAG, "requestDrawGL: android.view.HardwareCanvas can not be linked.", e);
        //     } catch (ClassCastException e) {
        //         Log.e(TAG, "requestDrawGL: Could not cast result to Integer or Booleean", e);
        //     } catch (IllegalArgumentException e) {
        //         Log.e(TAG, "requestDrawGL: callDrawGLFunction called with illegal arguments.", e);
        //     } catch (IllegalAccessException e) {
        //         Log.e(TAG, "requestDrawGL: callDrawGLFunction IllegalAccessException.", e);
        //     } catch (InvocationTargetException e) {
        //         Log.e(TAG, "requestDrawGL: callDrawGLFunction InvocationTargetException.", e);
        //     }
        // }

        return false;
    }

    public static void setChromiumAwDrawGLFunction(int functionPointer) {
        nativeSetChromiumAwDrawGLFunction(functionPointer);
    }

    // Holds the core resources of the class, everything required to correctly cleanup.
    // IMPORTANT: this class must not hold any reference back to the outer DrawGLFunctor
    // instance, as that will defeat GC of that object.
    private static final class DestroyRunnable implements Runnable {
        int mNativeDrawGLFunctor;
        DestroyRunnable(int nativeDrawGLFunctor) {
            mNativeDrawGLFunctor = nativeDrawGLFunctor;
        }

        // Called when the outer DrawGLFunctor instance has been GC'ed, i.e this is its finalizer.
        @Override
        public void run() {
            nativeDestroyGLFunctor(mNativeDrawGLFunctor);
            mNativeDrawGLFunctor = 0;
        }
    }

    private static native int nativeCreateGLFunctor(int viewContext);
    private static native void nativeDestroyGLFunctor(int functor);
    private static native void nativeSetChromiumAwDrawGLFunction(int functionPointer);
}