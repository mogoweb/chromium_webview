/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.mogoweb.browser;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.webkit.WebStorage;

/**
 * This is a series of unit tests for the WebStorageSizeManager class.
 *
 */
@MediumTest
public class WebStorageSizeManagerUnitTests extends AndroidTestCase {
    // Used for testing the out-of-space callbacks.
    private long mNewQuota;
    // Callback functor that sets a new quota in case of out-of-space scenarios.
    private class MockQuotaUpdater implements WebStorage.QuotaUpdater {
        public void updateQuota(long newQuota) {
            mNewQuota = newQuota;
        }
    }

    // Mock the DiskInfo.
    private class MockDiskInfo implements WebStorageSizeManager.DiskInfo {
        private long mFreeSize;
        private long mTotalSize;

        public long getFreeSpaceSizeBytes() {
            return mFreeSize;
        }

        public long getTotalSizeBytes() {
            return mTotalSize;
        }

        public void setFreeSpaceSizeBytes(long freeSize) {
            mFreeSize = freeSize;
        }

        public void setTotalSizeBytes(long totalSize) {
            mTotalSize = totalSize;
        }
    }

    // Mock the AppCacheInfo
    public class MockAppCacheInfo implements WebStorageSizeManager.AppCacheInfo {
        private long mAppCacheSize;

        public long getAppCacheSizeBytes() {
            return mAppCacheSize;
        }

        public void setAppCacheSizeBytes(long appCacheSize) {
            mAppCacheSize = appCacheSize;
        }
    }

    private MockQuotaUpdater mQuotaUpdater = new MockQuotaUpdater();
    private final MockDiskInfo mDiskInfo = new MockDiskInfo();
    private final MockAppCacheInfo mAppCacheInfo = new MockAppCacheInfo();
    // Utility for making size computations easier to read.
    private long bytes(double megabytes) {
        return (new Double(megabytes * 1024 * 1024)).longValue();
    }
    /**
     * Test the onExceededDatabaseQuota and onReachedMaxAppCacheSize callbacks
     */
    public void testCallbacks() {
        long totalUsedQuota = 0;
        final long quotaIncrease = WebStorageSizeManager.QUOTA_INCREASE_STEP;  // 1MB

        // We have 75 MB total, 24MB free so the global limit will be 12 MB.
        mDiskInfo.setTotalSizeBytes(bytes(75));
        mDiskInfo.setFreeSpaceSizeBytes(bytes(24));
        // We have an appcache file size of 0 MB.
        mAppCacheInfo.setAppCacheSizeBytes(0);
        // Create the manager.
        WebStorageSizeManager manager = new WebStorageSizeManager(getContext(), mDiskInfo,
                mAppCacheInfo);
        // We add origin 1.
        long origin1Quota = 0;
        long origin1EstimatedSize = bytes(3.5);
        manager.onExceededDatabaseQuota("1", "1", origin1Quota, origin1EstimatedSize, totalUsedQuota, mQuotaUpdater);
        assertEquals(origin1EstimatedSize, mNewQuota);
        origin1Quota = mNewQuota;
        totalUsedQuota += origin1Quota;

        // We add origin 2.
        long origin2Quota = 0;
        long origin2EstimatedSize = bytes(2.5);
        manager.onExceededDatabaseQuota("2", "2", origin2Quota, origin2EstimatedSize, totalUsedQuota, mQuotaUpdater);
        assertEquals(origin2EstimatedSize, mNewQuota);
        origin2Quota = mNewQuota;
        totalUsedQuota += origin2Quota;

        // Origin 1 runs out of space.
        manager.onExceededDatabaseQuota("1", "1", origin1Quota, 0, totalUsedQuota, mQuotaUpdater);
        assertEquals(origin1EstimatedSize + quotaIncrease, mNewQuota);
        totalUsedQuota -= origin1Quota;
        origin1Quota = mNewQuota;
        totalUsedQuota += origin1Quota;

        // Origin 2 runs out of space.
        manager.onExceededDatabaseQuota("2", "2", origin2Quota, 0, totalUsedQuota, mQuotaUpdater);
        assertEquals(origin2EstimatedSize + quotaIncrease, mNewQuota);
        totalUsedQuota -= origin2Quota;
        origin2Quota = mNewQuota;
        totalUsedQuota += origin2Quota;

        // We add origin 3. TotalUsedQuota is 8 (3.5 + 2.5 + 1 + 1). AppCacheMaxSize is 3 (12 / 4).
        // So we have 1 MB free.
        long origin3Quota = 0;
        long origin3EstimatedSize = bytes(5);
        manager.onExceededDatabaseQuota("3", "3", origin3Quota, origin3EstimatedSize, totalUsedQuota, mQuotaUpdater);
        assertEquals(0, mNewQuota);  // We cannot satisfy the estimatedSize
        origin3Quota = mNewQuota;
        totalUsedQuota += origin3Quota;

        // Origin 1 runs out of space again. It should increase it's quota to take the last 1MB.
        manager.onExceededDatabaseQuota("1", "1", origin1Quota, 0, totalUsedQuota, mQuotaUpdater);
        assertEquals(origin1Quota + quotaIncrease, mNewQuota);
        totalUsedQuota -= origin1Quota;
        origin1Quota = mNewQuota;
        totalUsedQuota += origin1Quota;

        // Origin 1 runs out of space again. It should inow fail to increase in size.
        manager.onExceededDatabaseQuota("1", "1", origin1Quota, 0, totalUsedQuota, mQuotaUpdater);
        assertEquals(origin1Quota, mNewQuota);

        // We try adding a new origin. Which will fail.
        manager.onExceededDatabaseQuota("4", "4", 0, bytes(1), totalUsedQuota, mQuotaUpdater);
        assertEquals(0, mNewQuota);

        // AppCache size increases to 2MB...
        mAppCacheInfo.setAppCacheSizeBytes(bytes(2));
        // ... and wants 2MB more. Fail.
        manager.onReachedMaxAppCacheSize(bytes(2), totalUsedQuota, mQuotaUpdater);
        assertEquals(0, mNewQuota);

        // The user nukes origin 2
        totalUsedQuota -= origin2Quota;
        origin2Quota = 0;
        // TotalUsedQuota is 5.5 (9 - 3.5). AppCacheMaxSize is 3. AppCacheSize is 2.
        // AppCache wants 1.5MB more
        manager.onReachedMaxAppCacheSize(bytes(1.5), totalUsedQuota, mQuotaUpdater);
        mAppCacheInfo.setAppCacheSizeBytes(mAppCacheInfo.getAppCacheSizeBytes() + bytes(2.5));
        assertEquals(mAppCacheInfo.getAppCacheSizeBytes(), mNewQuota - WebStorageSizeManager.APPCACHE_MAXSIZE_PADDING);

        // We try adding a new origin. This time we succeed.
        // TotalUsedQuota is 5.5. AppCacheMaxSize is 5.0. So we have 12 - 10.5 = 1.5 available.
        long origin4Quota = 0;
        long origin4EstimatedSize = bytes(1.5);
        manager.onExceededDatabaseQuota("4", "4", origin4Quota, origin4EstimatedSize, totalUsedQuota, mQuotaUpdater);
        assertEquals(bytes(1.5), mNewQuota);
        origin4Quota = mNewQuota;
        totalUsedQuota += origin4Quota;
    }
    /**
     * Test the application caches max size calculator.
     */
    public void testCalculateGlobalLimit() {
        long fileSystemSize = 78643200;  // 75 MB
        long freeSpaceSize = 25165824;  // 24 MB
        long maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(12582912, maxSize);  // 12MB

        fileSystemSize = 78643200;  // 75 MB
        freeSpaceSize = 60 * 1024 * 1024;  // 60MB
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(19922944, maxSize);  // 19MB

        fileSystemSize = 8589934592L;  // 8 GB
        freeSpaceSize = 4294967296L;  // 4 GB
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(536870912L, maxSize);  // 512 MB

        fileSystemSize = -14;
        freeSpaceSize = 21;
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);

        fileSystemSize = 100;
        freeSpaceSize = 101;
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);

        fileSystemSize = 3774873; // ~4.2 MB
        freeSpaceSize = 2560000;  // ~2.4 MB
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(2097152, maxSize);  // 2 MB

        fileSystemSize = 4404019; // ~4.2 MB
        freeSpaceSize = 3774873;  // ~3.6 MB
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(2097152, maxSize);  // 2 MB

        fileSystemSize = 4404019; // ~4.2 MB
        freeSpaceSize = 4404019;  // ~4.2 MB
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(3145728, maxSize);  // 3 MB

        fileSystemSize = 1048576; // 1 MB
        freeSpaceSize = 1048575;  // 1 MB - 1 byte
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);

        fileSystemSize = 3774873; // ~3.6 MB
        freeSpaceSize = 2097151;  // 2 MB - 1 byte
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);

        fileSystemSize = 3774873; // ~3.6 MB
        freeSpaceSize = 2097151;  // 2 MB
        maxSize = WebStorageSizeManager.calculateGlobalLimit(fileSystemSize, freeSpaceSize);
        assertEquals(0, maxSize);
    }

    public void testManyDatabasesOnOneOrigin() {
        // This test ensures that if an origin creates more than one database, the quota that is
        // assigned to the origin after the second creation is enough to satisfy all databases
        // under that origin.
        // See b/2417477.

        long totalUsedQuota = 0;
        mDiskInfo.setTotalSizeBytes(bytes(100));
        mDiskInfo.setFreeSpaceSizeBytes(bytes(100));
        // This should give us a storage area of 13MB, with 3.25MB for appcache and 9.75MB for
        // databases.
        assertEquals(bytes(13), WebStorageSizeManager.calculateGlobalLimit(
                mDiskInfo.getTotalSizeBytes(), mDiskInfo.getFreeSpaceSizeBytes()));

        // We have an appcache file size of 0 MB.
        mAppCacheInfo.setAppCacheSizeBytes(0);

        // Create the manager.
        WebStorageSizeManager manager = new WebStorageSizeManager(getContext(), mDiskInfo,
                mAppCacheInfo);

        // We add an origin.
        long originQuota = 0;
        long database1EstimatedSize = bytes(2);
        manager.onExceededDatabaseQuota("1", "1", originQuota, database1EstimatedSize,
                totalUsedQuota, mQuotaUpdater);
        assertEquals(database1EstimatedSize, mNewQuota);
        originQuota = mNewQuota;
        totalUsedQuota = originQuota;

        // Now try to create a new database under the origin, by invoking onExceededDatabaseQuota
        // again. This time, request more space than the old quota + the quota increase step.
        long database2EstimatedSize = bytes(3.5);
        manager.onExceededDatabaseQuota("1", "2", originQuota, database2EstimatedSize,
                totalUsedQuota, mQuotaUpdater);
        assertEquals(database1EstimatedSize + database2EstimatedSize, mNewQuota);
        originQuota = mNewQuota;
        totalUsedQuota = originQuota;

        // Create another database, but this time use a size that will overflow the space on the
        // device. It should be denied.
        long database3EstimatedSize = bytes(50);
        manager.onExceededDatabaseQuota("1", "3", originQuota, database3EstimatedSize,
                totalUsedQuota, mQuotaUpdater);
        assertEquals(originQuota, mNewQuota);

        // Create another database. This time, request less than the old quota.
        long database4EstimatedSize = bytes(2);
        manager.onExceededDatabaseQuota("1", "4", originQuota, database4EstimatedSize,
                totalUsedQuota, mQuotaUpdater);
        assertEquals(database1EstimatedSize + database2EstimatedSize + database4EstimatedSize,
                mNewQuota);
        originQuota = mNewQuota;
        totalUsedQuota = originQuota;

        // Now have the first database overflow it's quota. We should get 1 more MB.
        manager.onExceededDatabaseQuota("1", "1", originQuota, 0, totalUsedQuota, mQuotaUpdater);
        assertEquals(database1EstimatedSize + database2EstimatedSize + database4EstimatedSize +
                bytes(1), mNewQuota);
        originQuota = mNewQuota;
        totalUsedQuota = originQuota;

        // Create a db under the origin that uses a quota less than the usual quota increase step.
        long database5EstimatedSize = bytes(0.5);
        manager.onExceededDatabaseQuota("1", "5", originQuota, database5EstimatedSize,
                totalUsedQuota, mQuotaUpdater);
        assertEquals(database1EstimatedSize + database2EstimatedSize + database4EstimatedSize +
                bytes(1) + database5EstimatedSize, mNewQuota);
    }
}
