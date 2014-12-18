// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.content.browser;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;

import org.chromium.base.CalledByNative;
import org.chromium.base.CollectionUtil;
import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Android implementation of the device motion and orientation APIs.
 */
@JNINamespace("content")
class DeviceSensors implements SensorEventListener {

    private static final String TAG = "DeviceMotionAndOrientation";

    // These fields are lazily initialized by getHandler().
    private Thread mThread;
    private Handler mHandler;

    // A reference to the application context in order to acquire the SensorService.
    private final Context mAppContext;

    // The lock to access the mHandler.
    private final Object mHandlerLock = new Object();

    // Non-zero if and only if we're listening for events.
    // To avoid race conditions on the C++ side, access must be synchronized.
    private long mNativePtr;

    // The lock to access the mNativePtr.
    private final Object mNativePtrLock = new Object();

    // Holds a shortened version of the rotation vector for compatibility purposes.
    private float[] mTruncatedRotationVector;

    // Lazily initialized when registering for notifications.
    private SensorManagerProxy mSensorManagerProxy;

    // The only instance of that class and its associated lock.
    private static DeviceSensors sSingleton;
    private static Object sSingletonLock = new Object();

    /**
     * constants for using in JNI calls, also see
     * content/browser/device_sensors/sensor_manager_android.cc
     */
    static final int DEVICE_ORIENTATION = 0;
    static final int DEVICE_MOTION = 1;

    static final Set<Integer> DEVICE_ORIENTATION_SENSORS = CollectionUtil.newHashSet(
            Sensor.TYPE_ROTATION_VECTOR);

    static final Set<Integer> DEVICE_MOTION_SENSORS = CollectionUtil.newHashSet(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_GYROSCOPE);

    @VisibleForTesting
    final Set<Integer> mActiveSensors = new HashSet<Integer>();
    boolean mDeviceMotionIsActive = false;
    boolean mDeviceOrientationIsActive = false;

    protected DeviceSensors(Context context) {
        mAppContext = context.getApplicationContext();
    }

    /**
     * Start listening for sensor events. If this object is already listening
     * for events, the old callback is unregistered first.
     *
     * @param nativePtr Value to pass to nativeGotOrientation() for each event.
     * @param rateInMilliseconds Requested callback rate in milliseconds. The
     *            actual rate may be higher. Unwanted events should be ignored.
     * @param eventType Type of event to listen to, can be either DEVICE_ORIENTATION or
     *                  DEVICE_MOTION.
     * @return True on success.
     */
    @CalledByNative
    public boolean start(long nativePtr, int eventType, int rateInMilliseconds) {
        boolean success = false;
        synchronized (mNativePtrLock) {
            switch (eventType) {
                case DEVICE_ORIENTATION:
                    success = registerSensors(DEVICE_ORIENTATION_SENSORS, rateInMilliseconds,
                            true);
                    break;
                case DEVICE_MOTION:
                    // note: device motion spec does not require all sensors to be available
                    success = registerSensors(DEVICE_MOTION_SENSORS, rateInMilliseconds, false);
                    break;
                default:
                    Log.e(TAG, "Unknown event type: " + eventType);
                    return false;
            }
            if (success) {
                mNativePtr = nativePtr;
                setEventTypeActive(eventType, true);
            }
            return success;
        }
    }

    @CalledByNative
    public int getNumberActiveDeviceMotionSensors() {
        Set<Integer> deviceMotionSensors = new HashSet<Integer>(DEVICE_MOTION_SENSORS);
        deviceMotionSensors.removeAll(mActiveSensors);
        return DEVICE_MOTION_SENSORS.size() - deviceMotionSensors.size();
    }

    /**
     * Stop listening to sensors for a given event type. Ensures that sensors are not disabled
     * if they are still in use by a different event type.
     *
     * @param eventType Type of event to listen to, can be either DEVICE_ORIENTATION or
     *                  DEVICE_MOTION.
     * We strictly guarantee that the corresponding native*() methods will not be called
     * after this method returns.
     */
    @CalledByNative
    public void stop(int eventType) {
        Set<Integer> sensorsToRemainActive = new HashSet<Integer>();
        synchronized (mNativePtrLock) {
            switch (eventType) {
                case DEVICE_ORIENTATION:
                    if (mDeviceMotionIsActive) {
                        sensorsToRemainActive.addAll(DEVICE_MOTION_SENSORS);
                    }
                    break;
                case DEVICE_MOTION:
                    if (mDeviceOrientationIsActive) {
                        sensorsToRemainActive.addAll(DEVICE_ORIENTATION_SENSORS);
                    }
                    break;
                default:
                    Log.e(TAG, "Unknown event type: " + eventType);
                    return;
            }

            Set<Integer> sensorsToDeactivate = new HashSet<Integer>(mActiveSensors);
            sensorsToDeactivate.removeAll(sensorsToRemainActive);
            unregisterSensors(sensorsToDeactivate);
            setEventTypeActive(eventType, false);
            if (mActiveSensors.isEmpty()) {
                mNativePtr = 0;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        sensorChanged(event.sensor.getType(), event.values);
    }

    @VisibleForTesting
    void sensorChanged(int type, float[] values) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
                if (mDeviceMotionIsActive) {
                    gotAccelerationIncludingGravity(values[0], values[1], values[2]);
                }
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                if (mDeviceMotionIsActive) {
                    gotAcceleration(values[0], values[1], values[2]);
                }
                break;
            case Sensor.TYPE_GYROSCOPE:
                if (mDeviceMotionIsActive) {
                    gotRotationRate(values[0], values[1], values[2]);
                }
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                if (mDeviceOrientationIsActive) {
                    if (values.length > 4) {
                        // On some Samsung devices SensorManager.getRotationMatrixFromVector
                        // appears to throw an exception if rotation vector has length > 4.
                        // For the purposes of this class the first 4 values of the
                        // rotation vector are sufficient (see crbug.com/335298 for details).
                        if (mTruncatedRotationVector == null) {
                            mTruncatedRotationVector = new float[4];
                        }
                        System.arraycopy(values, 0, mTruncatedRotationVector, 0, 4);
                        getOrientationFromRotationVector(mTruncatedRotationVector);
                    } else {
                        getOrientationFromRotationVector(values);
                    }
                }
                break;
            default:
                // Unexpected
                return;
        }
    }

    /**
     * Returns orientation angles from a rotation matrix, such that the angles are according
     * to spec {@link http://dev.w3.org/geo/api/spec-source-orientation.html}.
     * <p>
     * It is assumed the rotation matrix transforms a 3D column vector from device coordinate system
     * to the world's coordinate system, as e.g. computed by {@see SensorManager.getRotationMatrix}.
     * <p>
     * In particular we compute the decomposition of a given rotation matrix R such that <br>
     * R = Rz(alpha) * Rx(beta) * Ry(gamma), <br>
     * where Rz, Rx and Ry are rotation matrices around Z, X and Y axes in the world coordinate
     * reference frame respectively. The reference frame consists of three orthogonal axes X, Y, Z
     * where X points East, Y points north and Z points upwards perpendicular to the ground plane.
     * The computed angles alpha, beta and gamma are in radians and clockwise-positive when viewed
     * along the positive direction of the corresponding axis. Except for the special case when the
     * beta angle is +-pi/2 these angles uniquely define the orientation of a mobile device in 3D
     * space. The alpha-beta-gamma representation resembles the yaw-pitch-roll convention used in
     * vehicle dynamics, however it does not exactly match it. One of the differences is that the
     * 'pitch' angle beta is allowed to be within [-pi, pi). A mobile device with pitch angle
     * greater than pi/2 could correspond to a user lying down and looking upward at the screen.
     *
     * <p>
     * Upon return the array values is filled with the result,
     * <ul>
     * <li>values[0]: rotation around the Z axis, alpha in [0, 2*pi)</li>
     * <li>values[1]: rotation around the X axis, beta in [-pi, pi)</li>
     * <li>values[2]: rotation around the Y axis, gamma in [-pi/2, pi/2)</li>
     * </ul>
     * <p>
     *
     * @param R
     *        a 3x3 rotation matrix {@see SensorManager.getRotationMatrix}.
     *
     * @param values
     *        an array of 3 doubles to hold the result.
     *
     * @return the array values passed as argument.
     */
    @VisibleForTesting
    public static double[] computeDeviceOrientationFromRotationMatrix(float[] R, double[] values) {
        /*
         * 3x3 (length=9) case:
         *   /  R[ 0]   R[ 1]   R[ 2]  \
         *   |  R[ 3]   R[ 4]   R[ 5]  |
         *   \  R[ 6]   R[ 7]   R[ 8]  /
         *
         */
        if (R.length != 9)
            return values;

        if (R[8] > 0) {  // cos(beta) > 0
            values[0] = Math.atan2(-R[1], R[4]);
            values[1] = Math.asin(R[7]);           // beta (-pi/2, pi/2)
            values[2] = Math.atan2(-R[6], R[8]);   // gamma (-pi/2, pi/2)
        } else if (R[8] < 0) {  // cos(beta) < 0
            values[0] = Math.atan2(R[1], -R[4]);
            values[1] = -Math.asin(R[7]);
            values[1] += (values[1] >= 0) ? -Math.PI : Math.PI; // beta [-pi,-pi/2) U (pi/2,pi)
            values[2] = Math.atan2(R[6], -R[8]);   // gamma (-pi/2, pi/2)
        } else { // R[8] == 0
            if (R[6] > 0) {  // cos(gamma) == 0, cos(beta) > 0
                values[0] = Math.atan2(-R[1], R[4]);
                values[1] = Math.asin(R[7]);       // beta [-pi/2, pi/2]
                values[2] = -Math.PI / 2;          // gamma = -pi/2
            } else if (R[6] < 0) { // cos(gamma) == 0, cos(beta) < 0
                values[0] = Math.atan2(R[1], -R[4]);
                values[1] = -Math.asin(R[7]);
                values[1] += (values[1] >= 0) ? -Math.PI : Math.PI; // beta [-pi,-pi/2) U (pi/2,pi)
                values[2] = -Math.PI / 2;          // gamma = -pi/2
            } else { // R[6] == 0, cos(beta) == 0
                // gimbal lock discontinuity
                values[0] = Math.atan2(R[3], R[0]);
                values[1] = (R[7] > 0) ? Math.PI / 2 : -Math.PI / 2;  // beta = +-pi/2
                values[2] = 0;                                        // gamma = 0
            }
        }

        // alpha is in [-pi, pi], make sure it is in [0, 2*pi).
        if (values[0] < 0)
            values[0] += 2 * Math.PI; // alpha [0, 2*pi)

        return values;
    }

    private void getOrientationFromRotationVector(float[] rotationVector) {
        float[] deviceRotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deviceRotationMatrix, rotationVector);

        double[] rotationAngles = new double[3];
        computeDeviceOrientationFromRotationMatrix(deviceRotationMatrix, rotationAngles);

        gotOrientation(Math.toDegrees(rotationAngles[0]),
                       Math.toDegrees(rotationAngles[1]),
                       Math.toDegrees(rotationAngles[2]));
    }

    private SensorManagerProxy getSensorManagerProxy() {
        if (mSensorManagerProxy != null) {
            return mSensorManagerProxy;
        }

        SensorManager sensorManager = ThreadUtils.runOnUiThreadBlockingNoException(
                new Callable<SensorManager>() {
            @Override
            public SensorManager call() {
                return (SensorManager) mAppContext.getSystemService(Context.SENSOR_SERVICE);
            }
        });

        if (sensorManager != null) {
            mSensorManagerProxy = new SensorManagerProxyImpl(sensorManager);
        }
        return mSensorManagerProxy;
    }

    @VisibleForTesting
    void setSensorManagerProxy(SensorManagerProxy sensorManagerProxy) {
        mSensorManagerProxy = sensorManagerProxy;
    }

    private void setEventTypeActive(int eventType, boolean value) {
        switch (eventType) {
            case DEVICE_ORIENTATION:
                mDeviceOrientationIsActive = value;
                return;
            case DEVICE_MOTION:
                mDeviceMotionIsActive = value;
                return;
        }
    }

    /**
     * @param sensorTypes List of sensors to activate.
     * @param rateInMilliseconds Intended delay (in milliseconds) between sensor readings.
     * @param failOnMissingSensor If true the method returns true only if all sensors could be
     *                            activated. When false the method return true if at least one
     *                            sensor in sensorTypes could be activated.
     */
    private boolean registerSensors(Set<Integer> sensorTypes, int rateInMilliseconds,
            boolean failOnMissingSensor) {
        Set<Integer> sensorsToActivate = new HashSet<Integer>(sensorTypes);
        sensorsToActivate.removeAll(mActiveSensors);
        boolean success = false;

        for (Integer sensorType : sensorsToActivate) {
            boolean result = registerForSensorType(sensorType, rateInMilliseconds);
            if (!result && failOnMissingSensor) {
                // restore the previous state upon failure
                unregisterSensors(sensorsToActivate);
                return false;
            }
            if (result) {
                mActiveSensors.add(sensorType);
                success = true;
            }
        }
        return success;
    }

    private void unregisterSensors(Iterable<Integer> sensorTypes) {
        for (Integer sensorType : sensorTypes) {
            if (mActiveSensors.contains(sensorType)) {
                getSensorManagerProxy().unregisterListener(this, sensorType);
                mActiveSensors.remove(sensorType);
            }
        }
    }

    private boolean registerForSensorType(int type, int rateInMilliseconds) {
        SensorManagerProxy sensorManager = getSensorManagerProxy();
        if (sensorManager == null) {
            return false;
        }
        final int rateInMicroseconds = 1000 * rateInMilliseconds;
        return sensorManager.registerListener(this, type, rateInMicroseconds, getHandler());
    }

    protected void gotOrientation(double alpha, double beta, double gamma) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
                nativeGotOrientation(mNativePtr, alpha, beta, gamma);
            }
        }
    }

    protected void gotAcceleration(double x, double y, double z) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
                nativeGotAcceleration(mNativePtr, x, y, z);
            }
        }
    }

    protected void gotAccelerationIncludingGravity(double x, double y, double z) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
                nativeGotAccelerationIncludingGravity(mNativePtr, x, y, z);
            }
        }
    }

    protected void gotRotationRate(double alpha, double beta, double gamma) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
                nativeGotRotationRate(mNativePtr, alpha, beta, gamma);
            }
        }
    }

    private Handler getHandler() {
        // TODO(timvolodine): Remove the mHandlerLock when sure that getHandler is not called
        // from multiple threads. This will be the case when device motion and device orientation
        // use the same polling thread (also see crbug/234282).
        synchronized (mHandlerLock) {
            if (mHandler == null) {
                HandlerThread thread = new HandlerThread("DeviceMotionAndOrientation");
                thread.start();
                mHandler = new Handler(thread.getLooper());  // blocks on thread start
            }
            return mHandler;
        }
    }

    @CalledByNative
    static DeviceSensors getInstance(Context appContext) {
        synchronized (sSingletonLock) {
            if (sSingleton == null) {
                sSingleton = new DeviceSensors(appContext);
            }
            return sSingleton;
        }
    }

    /**
     * Native JNI calls,
     * see content/browser/device_sensors/sensor_manager_android.cc
     */

    /**
     * Orientation of the device with respect to its reference frame.
     */
    private native void nativeGotOrientation(
            long nativeSensorManagerAndroid,
            double alpha, double beta, double gamma);

    /**
     * Linear acceleration without gravity of the device with respect to its body frame.
     */
    private native void nativeGotAcceleration(
            long nativeSensorManagerAndroid,
            double x, double y, double z);

    /**
     * Acceleration including gravity of the device with respect to its body frame.
     */
    private native void nativeGotAccelerationIncludingGravity(
            long nativeSensorManagerAndroid,
            double x, double y, double z);

    /**
     * Rotation rate of the device with respect to its body frame.
     */
    private native void nativeGotRotationRate(
            long nativeSensorManagerAndroid,
            double alpha, double beta, double gamma);

    /**
     * Need the an interface for SensorManager for testing.
     */
    interface SensorManagerProxy {
        public boolean registerListener(SensorEventListener listener, int sensorType, int rate,
                Handler handler);
        public void unregisterListener(SensorEventListener listener, int sensorType);
    }

    static class SensorManagerProxyImpl implements SensorManagerProxy {
        private final SensorManager mSensorManager;

        SensorManagerProxyImpl(SensorManager sensorManager) {
            mSensorManager = sensorManager;
        }

        @Override
        public boolean registerListener(SensorEventListener listener, int sensorType, int rate,
                Handler handler) {
            List<Sensor> sensors = mSensorManager.getSensorList(sensorType);
            if (sensors.isEmpty()) {
                return false;
            }
            return mSensorManager.registerListener(listener, sensors.get(0), rate, handler);
        }

        @Override
        public void unregisterListener(SensorEventListener listener, int sensorType) {
            List<Sensor> sensors = mSensorManager.getSensorList(sensorType);
            if (!sensors.isEmpty()) {
                mSensorManager.unregisterListener(listener, sensors.get(0));
            }
        }
    }

}
