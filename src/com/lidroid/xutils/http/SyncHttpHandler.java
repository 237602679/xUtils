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
package com.lidroid.xutils.http;

import android.text.TextUtils;
import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.client.HttpGetCache;
import com.lidroid.xutils.http.client.HttpRequest;
import com.lidroid.xutils.http.client.ResponseStream;
import com.lidroid.xutils.http.client.callback.DefaultDownloadRedirectHandler;
import com.lidroid.xutils.http.client.callback.DownloadRedirectHandler;
import org.apache.http.*;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.UnknownHostException;

public class SyncHttpHandler {

    private final AbstractHttpClient client;
    private final HttpContext context;

    private int retriedTimes = 0;

    private String charset; // 文本返回内容的默认charset

    private DownloadRedirectHandler downloadRedirectHandler;

    public void setDownloadRedirectHandler(DownloadRedirectHandler downloadRedirectHandler) {
        this.downloadRedirectHandler = downloadRedirectHandler;
    }

    public SyncHttpHandler(AbstractHttpClient client, HttpContext context, String charset) {
        this.client = client;
        this.context = context;
        this.charset = charset;
    }

    private String _getRequestUrl; // if not get method, it will be null.
    private long expiry = HttpGetCache.getDefaultExpiryTime();

    public void setExpiry(long expiry) {
        this.expiry = expiry;
    }

    public ResponseStream sendRequest(HttpRequestBase request) throws HttpException {

        boolean retry = true;
        HttpRequestRetryHandler retryHandler = client.getHttpRequestRetryHandler();
        while (retry) {
            IOException exception = null;
            try {
                if (request.getMethod().equals(HttpRequest.HttpMethod.GET.toString())) {
                    _getRequestUrl = request.getURI().toString();
                } else {
                    _getRequestUrl = null;
                }
                if (_getRequestUrl != null) {
                    String result = HttpUtils.sHttpGetCache.get(_getRequestUrl);
                    if (result != null) { // 未过期的返回字符串的get请求直接返回结果
                        return new ResponseStream(result);
                    }
                }
                HttpResponse response = client.execute(request, context);
                return handleResponse(response);
            } catch (UnknownHostException e) {
                exception = e;
                retry = retryHandler.retryRequest(exception, ++retriedTimes, context);
            } catch (IOException e) {
                exception = e;
                retry = retryHandler.retryRequest(exception, ++retriedTimes, context);
            } catch (NullPointerException e) {
                exception = new IOException(e);
                retry = retryHandler.retryRequest(exception, ++retriedTimes, context);
            } catch (Exception e) {
                exception = new IOException(e);
                retry = retryHandler.retryRequest(exception, ++retriedTimes, context);
            } finally {
                if (!retry && exception != null) {
                    throw new HttpException(exception);
                }
            }
        }
        return null;
    }

    private ResponseStream handleResponse(HttpResponse response) throws HttpException, IOException {
        if (response == null) return null;
        StatusLine status = response.getStatusLine();
        if (status.getStatusCode() < 300) {

            // 自适应charset
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                Header header = entity.getContentType();
                if (header != null) {
                    HeaderElement[] values = header.getElements();
                    if (values != null && values.length > 0) {
                        NameValuePair param = values[0].getParameterByName("charset");
                        if (param != null) {
                            charset = TextUtils.isEmpty(param.getValue()) ? charset : param.getValue();
                        }
                    }
                }
            }

            return new ResponseStream(response, charset, _getRequestUrl, expiry);
        } else if (status.getStatusCode() == 302) {
            if (downloadRedirectHandler == null) {
                downloadRedirectHandler = new DefaultDownloadRedirectHandler();
            }
            HttpRequestBase request = downloadRedirectHandler.getDirectRequest(response);
            if (request != null) {
                return this.sendRequest(request);
            }
        } else {
            throw new HttpException(status.getStatusCode() + ": " + status.getReasonPhrase());
        }
        return null;
    }
}
