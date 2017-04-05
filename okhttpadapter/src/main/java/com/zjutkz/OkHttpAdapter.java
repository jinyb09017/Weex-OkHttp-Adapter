package com.zjutkz;

import com.taobao.weex.adapter.IWXHttpAdapter;
import com.taobao.weex.common.WXRequest;
import com.taobao.weex.common.WXResponse;
import com.zjutkz.progress.IncrementaRequestBody;
import com.zjutkz.progress.IncrementalResponseBody;
import com.zjutkz.progress.listener.RequestListener;
import com.zjutkz.progress.listener.ResponseListener;
import com.zjutkz.utils.Assert;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by kangzhe on 16/9/22.
 */
public class OkHttpAdapter implements IWXHttpAdapter {

    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";

    public static final int REQUEST_FAILURE = -100;

    @Override
    public void sendRequest(final WXRequest request, final OnHttpListener listener) {
        if (listener != null) {
            listener.onHttpStart();
        }

        RequestListener requestListener = new RequestListener() {
            @Override
            public void onRequest(long consumed, long total, boolean done) {
                if (Assert.checkNull(listener)) {
                    listener.onHttpUploadProgress((int) (consumed));
                }
            }
        };

        final ResponseListener responseListener = new ResponseListener() {
            @Override
            public void onResponse(long consumed, long total, boolean done) {
                if (Assert.checkNull(listener)) {
                    listener.onHttpResponseProgress((int) (consumed));
                }
            }
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Response originalResponse = chain.proceed(chain.request());
                        return originalResponse.newBuilder()
                                .body(new IncrementalResponseBody(originalResponse.body(), responseListener))
                                .build();
                    }
                })
                .build();
        Request okHttpRequest;
        if (METHOD_GET.equalsIgnoreCase(request.method)) {
            okHttpRequest = new Request.Builder()
                    .url(request.url)
                    .get()
                    .build();
        } else if (METHOD_POST.equalsIgnoreCase(request.method)) {
            okHttpRequest = new Request.Builder()
                    .url(request.url)
                    .post(new IncrementaRequestBody(RequestBody.create(MediaType.parse(request.body), request.body), requestListener))
                    .build();
        } else if (!TextUtils.isEmpty(request.method)) {
            okHttpRequest = new Request.Builder()
                    .url(request.url)
                    .method(request.method, new IncrementaRequestBody(RequestBody.create(MediaType.parse(request.body), request.body), requestListener))
                    .build();
        } else {
            okHttpRequest = new Request.Builder()
                    .get()
                    .url(request.url)
                    .build();
        }
        client.newCall(okHttpRequest).enqueue(CommonCallBack(listener));
    }

    private boolean requestSuccess(int statusCode) {
        return statusCode >= 200 && statusCode <= 299;
    }

    private Callback CommonCallBack(final OnHttpListener listener) {
        return new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (Assert.checkNull(listener)) {
                    WXResponse wxResponse = new WXResponse();
                    wxResponse.errorCode = String.valueOf(REQUEST_FAILURE);
                    wxResponse.statusCode = String.valueOf(REQUEST_FAILURE);
                    wxResponse.errorMsg = e.getMessage();
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (Assert.checkNull(listener)) {

                    WXResponse wxResponse = new WXResponse();
                    wxResponse.statusCode = String.valueOf(response.code());
                    if (requestSuccess(Integer.parseInt(wxResponse.statusCode))) {
                        wxResponse.originalData = response.body().bytes();
                    } else {
                        wxResponse.errorCode = String.valueOf(response.code());
                        wxResponse.errorMsg = response.body().string();
                    }

                    listener.onHttpFinish(wxResponse);
                }
            }
        };
    }
}
