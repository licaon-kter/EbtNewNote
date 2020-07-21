/*******************************************************************************
 * Copyright (c) 2010 marvin.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     marvin - initial API and implementation
 ******************************************************************************/

package com.marv42.ebt.newnote.scanning;

import android.app.Application;
import android.os.AsyncTask;

import androidx.annotation.NonNull;

import com.marv42.ebt.newnote.R;

import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.marv42.ebt.newnote.ApiCaller.ERROR;
import static com.marv42.ebt.newnote.JsonHelper.getJsonObject;
import static com.marv42.ebt.newnote.scanning.PictureConverter.convert;

public class OcrHandler extends AsyncTask<Void, Void, String> {
    public interface Callback {
        void onOcrResult(String result);
    }

    private static final String OCR_HOST = "https://api.ocr.space/parse/image";

    private Application mApp;
    private Callback mCallback;
    private String mPhotoPath;
    private String mApiKey;

    public OcrHandler(@NonNull Application app, @NonNull Callback callback, @NonNull String photoPath,
                      @NonNull String apiKey) {
        mApp = app;
        mCallback = callback;
        mPhotoPath = photoPath;
        mApiKey = apiKey;
    }

    @Override
    protected String doInBackground(Void... voids) {
        String base64Image = convert(mPhotoPath);
        FormBody.Builder formBodyBuilder = new FormBody.Builder();
        formBodyBuilder.add("apikey", mApiKey);
        formBodyBuilder.add("base64Image", "data:image/jpeg;base64," + base64Image);
        FormBody formBody = formBodyBuilder.build();

        Request request = new Request.Builder().url(OCR_HOST).post(formBody).build();
        Call call = new OkHttpClient().newCall(request);
        try (Response response = call.execute()) {
            if (!response.isSuccessful())
                return getJsonObject(ERROR, mApp.getString(R.string.http_error)
                        + " " + response.code()).toString();
            ResponseBody responseBody = response.body();
            if (responseBody == null)
                return getJsonObject(ERROR, mApp.getString(R.string.server_error)).toString();
            String body = responseBody.string();
            JSONObject json = getJsonObject(body);
            if (json == null || json.has("error"))
                return getJsonObject(ERROR, body).toString();
            return json.toString();
        } catch (SocketTimeoutException e) {
            return getJsonObject(ERROR, mApp.getString(R.string.error_no_connection)).toString();
        } catch (IOException e) {
            return getJsonObject(ERROR, mApp.getString(R.string.internal_error)).toString();
        }
    }

    @Override
    protected void onPostExecute(String result) {
        mCallback.onOcrResult(TextProcessor.getOcrResult(result, mApp));
    }
}
