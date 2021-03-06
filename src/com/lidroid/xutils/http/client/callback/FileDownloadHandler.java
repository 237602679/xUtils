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
package com.lidroid.xutils.http.client.callback;

import android.text.TextUtils;
import org.apache.http.HttpEntity;

import java.io.*;

public class FileDownloadHandler {

    public Object handleEntity(HttpEntity entity, RequestCallBackHandler callback, String target, boolean isResume) throws IOException {
        if (entity == null || TextUtils.isEmpty(target)) {
            return null;
        }

        File targetFile = new File(target);

        if (!targetFile.exists()) {
            targetFile.createNewFile();
        }

        long current = 0;
        FileOutputStream fileOutputStream = null;
        if (isResume) {
            current = targetFile.length();
            fileOutputStream = new FileOutputStream(target, true);
        } else {
            fileOutputStream = new FileOutputStream(target);
        }

        long total = entity.getContentLength() + current;

        if (callback != null && !callback.updateProgress(total, current, true)) {
            return null;
        }

        InputStream ins = null;
        try {
            ins = entity.getContent();
            BufferedInputStream bis = null;
            if (ins instanceof BufferedInputStream) {
                bis = (BufferedInputStream) ins;
            } else {
                bis = new BufferedInputStream(ins);
            }

            byte[] tmp = new byte[4096];
            int len;
            while ((len = bis.read(tmp)) != -1) {
                fileOutputStream.write(tmp, 0, len);
                current += len;
                if (callback != null) {
                    if (!callback.updateProgress(total, current, false)) {
                        throw new IOException("stop");
                    }
                }
            }
            fileOutputStream.flush();
            if (callback != null) {
                callback.updateProgress(total, current, true);
            }
        } finally {
            if (ins != null) {
                try {
                    ins.close();
                } catch (Exception e) {
                }
            }
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (Exception e) {
                }
            }
        }

        return targetFile;
    }

}
