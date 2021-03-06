/*
 * Copyright (c) 2013. wyouflf (wyouflf@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lidroid.xutils.bitmap.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.lidroid.xutils.bitmap.BitmapDisplayConfig;
import com.lidroid.xutils.bitmap.download.Downloader;
import com.lidroid.xutils.util.LogUtils;
import com.lidroid.xutils.util.core.LruDiskCache;

import java.io.*;

public class BitmapDownloadProcess {
    private boolean isOriginalDiskCacheReadied = false;
    private int originalDiskCacheSize;

    private LruDiskCache mOriginalDiskCache;//原始图片的路径，不进行任何的压缩操作
    private final Object mOriginalDiskCacheLock = new Object();
    private static final int ORIGINAL_DISK_CACHE_INDEX = 0;

    private File mOriginalCacheDir;
    private Downloader downloader;

    private boolean neverCalculate = false;

    public BitmapDownloadProcess(Downloader downloader, String diskCachePath, int originalDiskCacheSize) {
        this.mOriginalCacheDir = new File(diskCachePath + "/original");
        this.downloader = downloader;
        this.originalDiskCacheSize = originalDiskCacheSize;
    }

    public void setDownloader(Downloader downloader) {
        this.downloader = downloader;
    }

    public void setOriginalDiskCacheSize(int originalDiskCacheSize) {
        this.originalDiskCacheSize = originalDiskCacheSize;
        if (mOriginalDiskCache != null) {
            mOriginalDiskCache.setMaxSize(originalDiskCacheSize);
        }
    }

    public void neverCalculate(boolean neverCalculate) {
        this.neverCalculate = neverCalculate;
    }

    public Bitmap downloadBitmap(String uri, BitmapDisplayConfig config) {
        FileDescriptor fileDescriptor = null;
        FileInputStream fileInputStream = null;
        LruDiskCache.Snapshot snapshot;
        synchronized (mOriginalDiskCacheLock) {
            // Wait for disk cache to initialize
            while (!isOriginalDiskCacheReadied) {
                try {
                    mOriginalDiskCacheLock.wait();
                } catch (InterruptedException e) {
                }
            }

            if (mOriginalDiskCache != null) {
                try {
                    snapshot = mOriginalDiskCache.get(uri);
                    if (snapshot == null) {
                        LruDiskCache.Editor editor = mOriginalDiskCache.edit(uri);
                        if (editor != null) {
                            if (downloader.downloadToLocalStreamByUri(uri, editor.newOutputStream(ORIGINAL_DISK_CACHE_INDEX))) {
                                editor.commit();
                            } else {
                                editor.abort();
                            }
                        }
                        snapshot = mOriginalDiskCache.get(uri);
                    }
                    if (snapshot != null) {
                        fileInputStream = (FileInputStream) snapshot.getInputStream(ORIGINAL_DISK_CACHE_INDEX);
                        fileDescriptor = fileInputStream.getFD();
                    }
                } catch (Exception e) {
                    LogUtils.e(e.getMessage(), e);
                }
            }
        }

        Bitmap bitmap = null;
        if (fileDescriptor != null) {
            try {
                if (neverCalculate) {
                    bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                } else {
                    bitmap = BitmapDecoder.decodeSampledBitmapFromDescriptor(fileDescriptor, config.getBitmapWidth(), config.getBitmapHeight());
                }
            } catch (Exception e) {
                LogUtils.e(e.getMessage(), e);
            }
        }

        if (fileInputStream != null) {
            try {
                fileInputStream.close();
            } catch (IOException e) {
            }
        }

        return bitmap;
    }

    public Bitmap getBitmapFromDiskCache(String uri) {
        synchronized (mOriginalDiskCacheLock) {
            while (!isOriginalDiskCacheReadied) {
                try {
                    mOriginalDiskCacheLock.wait();
                } catch (InterruptedException e) {
                }
            }
            if (mOriginalDiskCache != null) {
                InputStream inputStream = null;
                try {
                    final LruDiskCache.Snapshot snapshot = mOriginalDiskCache.get(uri);
                    if (snapshot != null) {
                        inputStream = snapshot.getInputStream(ORIGINAL_DISK_CACHE_INDEX);
                        if (inputStream != null) {
                            final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            return bitmap;
                        }
                    }
                } catch (final IOException e) {
                    LogUtils.e(e.getMessage(), e);
                } finally {
                    try {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    } catch (IOException e) {
                    }
                }
            }
            return null;
        }
    }

    public void initOriginalDiskCache() {
        if (!mOriginalCacheDir.exists()) {
            mOriginalCacheDir.mkdirs();
        }
        synchronized (mOriginalDiskCacheLock) {
            if (BitmapCommonUtils.getAvailableSpace(mOriginalCacheDir) > originalDiskCacheSize) {
                try {
                    mOriginalDiskCache = LruDiskCache.open(mOriginalCacheDir, 1, 1, originalDiskCacheSize);
                } catch (IOException e) {
                    mOriginalDiskCache = null;
                }
            }
            isOriginalDiskCacheReadied = true;
            mOriginalDiskCacheLock.notifyAll();
        }
    }

    public void clearOriginalDiskCache() {
        synchronized (mOriginalDiskCacheLock) {
            if (mOriginalDiskCache != null && !mOriginalDiskCache.isClosed()) {
                try {
                    mOriginalDiskCache.delete();
                } catch (IOException e) {
                    LogUtils.e(e.getMessage(), e);
                }
                mOriginalDiskCache = null;
                isOriginalDiskCacheReadied = false;
            }
        }
        initOriginalDiskCache();
    }

    public void clearOriginalDiskCache(String uri) {
        synchronized (mOriginalDiskCacheLock) {
            if (mOriginalDiskCache != null && !mOriginalDiskCache.isClosed()) {
                try {
                    mOriginalDiskCache.remove(uri);
                } catch (IOException e) {
                    LogUtils.e(e.getMessage(), e);
                }
            }
        }
    }

    public void flushOriginalDiskCache() {
        synchronized (mOriginalDiskCacheLock) {
            if (mOriginalDiskCache != null) {
                try {
                    mOriginalDiskCache.flush();
                } catch (IOException e) {
                    LogUtils.e(e.getMessage(), e);
                }
            }
        }
    }

    public void closeOriginalDiskCache() {
        synchronized (mOriginalDiskCacheLock) {
            if (mOriginalDiskCache != null) {
                try {
                    if (!mOriginalDiskCache.isClosed()) {
                        mOriginalDiskCache.close();
                        mOriginalDiskCache = null;
                    }
                } catch (IOException e) {
                    LogUtils.e(e.getMessage(), e);
                }
            }
        }
    }

}
