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

package com.marv42.ebt.newnote;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.FragmentActivity;

import com.marv42.ebt.newnote.scanning.OcrHandler;
import com.marv42.ebt.newnote.scanning.TextProcessor;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import dagger.android.support.DaggerFragment;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.provider.MediaStore.ACTION_IMAGE_CAPTURE;
import static android.widget.Toast.LENGTH_LONG;
import static androidx.core.content.FileProvider.getUriForFile;
import static androidx.core.content.PermissionChecker.PERMISSION_GRANTED;
import static androidx.core.content.PermissionChecker.checkSelfPermission;
import static com.marv42.ebt.newnote.EbtNewNote.CAMERA_PERMISSION_REQUEST_CODE;
import static com.marv42.ebt.newnote.EbtNewNote.FRAGMENT_TYPE;
import static com.marv42.ebt.newnote.EbtNewNote.IMAGE_CAPTURE_REQUEST_CODE;
import static com.marv42.ebt.newnote.EbtNewNote.LOCATION_PERMISSION_REQUEST_CODE;
import static com.marv42.ebt.newnote.EbtNewNote.NOTIFICATION_OCR_CHANNEL_ID;
import static com.marv42.ebt.newnote.EbtNewNote.OCR_NOTIFICATION_ID;
import static com.marv42.ebt.newnote.ErrorMessage.ERROR;
import static java.io.File.createTempFile;

public class SubmitFragment extends DaggerFragment implements OcrHandler.Callback,
        SharedPreferences.OnSharedPreferenceChangeListener /*, LifecycleOwner*/ {

    public interface Callback {
        void onSubmitFragmentAdded();
    }

    @Inject
    ThisApp mApp;
    @Inject
    SharedPreferences mSharedPreferences;
    @Inject
    ApiCaller mApiCaller;
    @Inject
    SubmissionResults mSubmissionResults;
    @Inject
    SharedPreferencesHandler mSharedPreferencesHandler;
    @Inject
    EncryptedPreferenceDataStore mDataStore;

    private static final int TIME_THRESHOLD_DELETE_OLD_PICS_MS = 1000 * 60 * 60 * 24; // one day
    private static final CharSequence CLIPBOARD_LABEL = "overwritten EBT data";
    private static final int VIBRATION_MS = 150;

    private File mPhotoFile;
    private String mCurrentPhotoPath;

    private Unbinder mUnbinder;
    @BindView(R.id.edit_text_country) EditText mCountryText;
    @BindView(R.id.edit_text_city) EditText mCityText;
    @BindView(R.id.edit_text_zip) EditText mPostalCodeText;
    @BindView(R.id.radio_group_1) RadioGroup mRadioGroup1;
    @BindView(R.id.radio_group_2) RadioGroup mRadioGroup2;
    private boolean mRadioChangingDone;
    @BindView(R.id.radio_5) RadioButton m5EurRadio;
    @BindView(R.id.radio_10) RadioButton m10EurRadio;
    @BindView(R.id.radio_20) RadioButton m20EurRadio;
    @BindView(R.id.radio_50) RadioButton m50EurRadio;
    @BindView(R.id.radio_100) RadioButton m100EurRadio;
    @BindView(R.id.radio_200) RadioButton m200EurRadio;
    @BindView(R.id.radio_500) RadioButton m500EurRadio;
    @BindView(R.id.edit_text_printer) EditText mShortCodeText;
    @BindView(R.id.edit_text_serial) EditText mSerialText;
    @BindView(R.id.edit_text_comment) AutoCompleteTextView mCommentText;

    private LocationTextWatcher mLocationTextWatcher;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.submit, container, false);
        mUnbinder = ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        FragmentActivity activity = getActivity();
        if (activity == null)
            throw new IllegalStateException("No activity");
        LoginChecker.checkLoginInfo(activity);
        checkOcrResult();
        ((Callback) activity).onSubmitFragmentAdded();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRadioGroup1.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId != -1 && mRadioChangingDone) {
                mRadioChangingDone = false;
                mRadioGroup2.clearCheck();
                mSharedPreferencesHandler.set(R.string.pref_denomination_key, getDenomination());
            }
            mRadioChangingDone = true;
        });
        mRadioGroup2.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId != -1 && mRadioChangingDone) {
                mRadioChangingDone = false;
                mRadioGroup1.clearCheck();
                mSharedPreferencesHandler.set(R.string.pref_denomination_key, getDenomination());
            }
            mRadioChangingDone = true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        setViewValuesFromPreferences();
        mLocationTextWatcher = new LocationTextWatcher();
        mCountryText.addTextChangedListener(mLocationTextWatcher);
        mCityText.addTextChangedListener(mLocationTextWatcher);
        mPostalCodeText.addTextChangedListener(mLocationTextWatcher);
        mCountryText.addTextChangedListener(new SavePreferencesTextWatcher(getString(R.string.pref_country_key)));
        mCityText.addTextChangedListener(new SavePreferencesTextWatcher(getString(R.string.pref_city_key)));
        mPostalCodeText.addTextChangedListener(new SavePreferencesTextWatcher(getString(R.string.pref_postal_code_key)));
        mShortCodeText.addTextChangedListener(new SavePreferencesTextWatcher(getString(R.string.pref_short_code_key)));
        mSerialText.addTextChangedListener(new SavePreferencesTextWatcher(getString(R.string.pref_serial_number_key)));
        mCommentText.addTextChangedListener(new SavePreferencesTextWatcher(getString(R.string.pref_comment_key)));
        executeCommentSuggestion();
    }

    @Override
    public void onPause() {
        mCountryText.removeTextChangedListener(mLocationTextWatcher);
        mCityText.removeTextChangedListener(mLocationTextWatcher);
        mPostalCodeText.removeTextChangedListener(mLocationTextWatcher);
        mLocationTextWatcher = null;
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        mUnbinder.unbind();
        mSharedPreferencesHandler = null;
        super.onDestroyView();
    }

    @OnClick(R.id.submit_button)
    void submitValues() {
        Toast.makeText(getActivity(), getString(R.string.submitting), LENGTH_LONG).show();
        new NoteDataSubmitter(mApp, mApiCaller, mSubmissionResults, mDataStore).execute(new NoteData(
                mCountryText.getText().toString(),
                mCityText.getText().toString(),
                mPostalCodeText.getText().toString(),
                getDenomination(),
                getFixedShortCode().toUpperCase(),
                mSerialText.getText().toString().replaceAll("\\W+", "").toUpperCase(),
                mCommentText.getText().toString()));
        mShortCodeText.setText("");
        mSerialText.setText("");
    }

    private String getFixedShortCode() {
        String fixedShortCode = mShortCodeText.getText().toString().replaceAll("\\s+", "");
        if (fixedShortCode.length() == 4)
            return fixedShortCode.substring(0, 1) + "00" + fixedShortCode.substring(1);
        else if (fixedShortCode.length() == 5)
            return fixedShortCode.substring(0, 1) + "0" + fixedShortCode.substring(1);
        return fixedShortCode;
    }

    @OnClick(R.id.location_button)
    void checkLocationSetting() {
        Activity activity = getActivity();
        if (checkSelfPermission(mApp, ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED &&
                checkSelfPermission(mApp, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED &&
                activity != null) {
            ActivityCompat.requestPermissions(activity,
                    new String[] { ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION },
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        if (activity == null)
            throw new IllegalStateException("No activity");
        LocationManager locationManager = (LocationManager)
                activity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null && !locationManager.isLocationEnabled()) {
            Toast.makeText(activity, getString(R.string.location_not_enabled), LENGTH_LONG).show();
            mApp.startLocationProviderChangedReceiver();
            return;
        }
        if (checkSelfPermission(mApp, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED)
            Toast.makeText(activity, getString(R.string.location_no_gps), LENGTH_LONG).show();
        mApp.startLocationTask();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (sharedPreferences == mSharedPreferences)
            setEditTextFromSharedPreferences(key);
    }

    private void setEditTextFromSharedPreferences(String key) {
        String newValue = mSharedPreferences.getString(key, "");
        EditText editText = getEditText(key);
        if (editText != null && !TextUtils.isEmpty(newValue) &&
                newValue != null && !newValue.equals(editText.getText().toString()))
            editText.setText(newValue);
    }

    private EditText getEditText(String prefRes) {
        if (prefRes.equals(mApp.getString(R.string.pref_country_key)))
            return mCountryText;
        if (prefRes.equals(mApp.getString(R.string.pref_city_key)))
            return mCityText;
        if (prefRes.equals(mApp.getString(R.string.pref_postal_code_key)))
            return mPostalCodeText;
        if (prefRes.equals(mApp.getString(R.string.pref_short_code_key)))
            return mShortCodeText;
        if (prefRes.equals(mApp.getString(R.string.pref_serial_number_key)))
            return mSerialText;
        if (prefRes.equals(mApp.getString(R.string.pref_comment_key)))
            return mCommentText;
        return null;
    }

    void setViewValuesFromPreferences() {
        String country = mSharedPreferencesHandler.get(R.string.pref_country_key, "");
        if (! TextUtils.equals(country, mCountryText.getText()))
            mCountryText.setText(country);
        String city = mSharedPreferencesHandler.get(R.string.pref_city_key, "");
        if (! TextUtils.equals(city, mCityText.getText()))
            mCityText.setText(city);
        String postalCode = mSharedPreferencesHandler.get(R.string.pref_postal_code_key, "");
        if (! TextUtils.equals(postalCode, mPostalCodeText.getText()))
            mPostalCodeText.setText(postalCode);
        setDenomination(mSharedPreferencesHandler.get(R.string.pref_denomination_key, getString(R.string.eur5)));
        mShortCodeText.setText(mSharedPreferencesHandler.get(R.string.pref_short_code_key, ""));
        mSerialText.setText(mSharedPreferencesHandler.get(R.string.pref_serial_number_key, ""));
        String comment = mSharedPreferencesHandler.get(R.string.pref_comment_key, "");
        String additionalComment = mDataStore.get(R.string.pref_settings_comment_key, "");
        if (comment.endsWith(additionalComment))
            mCommentText.setText(comment.substring(0, comment.length() - additionalComment.length()));
        else
            mCommentText.setText(comment);
    }

    @NonNull
    private String getDenomination() {
        if (m5EurRadio.isChecked())
            return getString(R.string.eur5);
        if (m10EurRadio.isChecked())
            return getString(R.string.eur10);
        if (m20EurRadio.isChecked())
            return getString(R.string.eur20);
        if (m50EurRadio.isChecked())
            return getString(R.string.eur50);
        if (m100EurRadio.isChecked())
            return getString(R.string.eur100);
        if (m200EurRadio.isChecked())
            return getString(R.string.eur200);
        if (m500EurRadio.isChecked())
            return getString(R.string.eur500);
        return "";
    }

    private void setDenomination(String denomination) {
        if (denomination.equals(getString(R.string.eur5)))
            m5EurRadio.setChecked(true);
        if (denomination.equals(getString(R.string.eur10)))
            m10EurRadio.setChecked(true);
        if (denomination.equals(getString(R.string.eur20)))
            m20EurRadio.setChecked(true);
        if (denomination.equals(getString(R.string.eur50)))
            m50EurRadio.setChecked(true);
        if (denomination.equals(getString(R.string.eur100)))
            m100EurRadio.setChecked(true);
        if (denomination.equals(getString(R.string.eur200)))
            m200EurRadio.setChecked(true);
        if (denomination.equals(getString(R.string.eur500)))
            m500EurRadio.setChecked(true);
    }

    @OnClick(R.id.photo_button)
    void takePhoto() {
        Activity activity = getActivity();
        if (activity == null)
            throw new IllegalStateException("No activity");
        Intent intent = new Intent(ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(activity.getPackageManager()) == null) {
            Toast.makeText(activity, getString(R.string.no_camera_activity), LENGTH_LONG).show();
            return;
        }
        if (checkSelfPermission(mApp, CAMERA) != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[] { CAMERA },
                    CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }
        if (!TextUtils.isEmpty(mCurrentPhotoPath)) {
            // TODO enable multiple OCR runs at the same time
            Toast.makeText(activity, getString(R.string.ocr_executing), LENGTH_LONG).show();
            return;
        }
        if (TextUtils.isEmpty(mDataStore.get(R.string.pref_settings_ocr_key, ""))) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.ocr_no_service_key)
                    .setMessage(mApp.getString(R.string.settings_ocr_summary) + " " +
                            mApp.getString(R.string.get_ocr_key))
                    .setPositiveButton(getString(R.string.ok),
                            (dialog, which) -> {
                                startActivity(new Intent(getActivity().getApplicationContext(),
                                        SettingsActivity.class));
                                dialog.dismiss();
                            })
                    .show();
            return;
        }
        try {
            mPhotoFile = createImageFile();
        } catch (IOException ex) {
            Toast.makeText(activity, getString(R.string.error_creating_file) + ": "
                    + ex.getMessage(), LENGTH_LONG).show();
            return;
        }
        mCurrentPhotoPath = mPhotoFile.getAbsolutePath();
        Uri photoUri = getUriForFile(activity, activity.getPackageName(), mPhotoFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        activity.startActivityForResult(intent, IMAGE_CAPTURE_REQUEST_CODE);
    }

    private File createImageFile() throws IOException {
        Activity activity = getActivity();
        if (activity == null)
            throw new IllegalStateException("No activity");
        File tempFolder = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        for (File file: tempFolder.listFiles())
            if (Calendar.getInstance().getTimeInMillis() - file.lastModified() > TIME_THRESHOLD_DELETE_OLD_PICS_MS)
                file.delete();
        return createTempFile("bill_", ".png", tempFolder);
    }

    @Override
    public void onOcrResult(String result) {
        mCurrentPhotoPath = "";
        if (isVisible()) // des is immer visible, mir soll's recht sei
            presentOcrResult(result);
        else {
            mSharedPreferencesHandler.set(R.string.pref_ocr_result, result);
            Intent intent = new Intent(mApp, EbtNewNote.class);
            intent.putExtra(FRAGMENT_TYPE, SubmitFragment.class.getSimpleName());
            PendingIntent contentIntent = PendingIntent.getActivity(mApp, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationManager notificationManager =
                    (NotificationManager) mApp.getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager == null)
                return;
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_OCR_CHANNEL_ID,
                    "OCR Result Notification Channel", IMPORTANCE_DEFAULT);
//            notificationChannel.setDescription("Channel description");
//            notificationChannel.enableLights(true);
//            notificationChannel.setLightColor(Color.RED);
//            notificationChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
//            notificationChannel.enableVibration(true);
            notificationManager.createNotificationChannel(notificationChannel);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mApp, NOTIFICATION_OCR_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_ebt)
                    .setContentTitle(mApp.getString(R.string.ocr_result))
                    .setContentText(mApp.getString(R.string.ocr_result_description))
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent);
            notificationManager.notify(OCR_NOTIFICATION_ID, builder.build());
        }
    }

    @NonNull
    String getPhotoPath() {
        return mCurrentPhotoPath;
    }

    void resetPhotoPath() {
        mCurrentPhotoPath = "";
    }

    void setCommentsAdapter(String[] suggestions) {
        Activity activity = getActivity();
        if (activity != null)
            mCommentText.setAdapter(new ArrayAdapter<>(activity,
                    android.R.layout.simple_dropdown_item_1line, suggestions));
    }

    private void checkOcrResult() {
        String ocrResult = mSharedPreferencesHandler.get(R.string.pref_ocr_result, "");
        if (!TextUtils.isEmpty(ocrResult))
            presentOcrResult(ocrResult);
    }

    private void presentOcrResult(String ocrResult) {
        Vibrator v = (Vibrator) mApp.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null)
            v.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE));
        Activity activity = getActivity();
        if (ocrResult.equals(TextProcessor.EMPTY))
            showDialog(activity, getString(R.string.ocr_dialog_empty));
        else if (ocrResult.startsWith(ERROR))
            showDialog(activity, new ErrorMessage(activity).getErrorMessage(ocrResult));
        else {
            if (ocrResult.length() < 9) {
                putToClipboard(mShortCodeText.getText());
                mShortCodeText.setText(ocrResult);
            } else {
                putToClipboard(mSerialText.getText());
                mSerialText.setText(ocrResult);
            }
            Toast.makeText(activity, getString(R.string.ocr_return), LENGTH_LONG).show();
        }
        mSharedPreferencesHandler.set(R.string.pref_ocr_result, "");
    }

    private static void showDialog(Activity activity, String message) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.ocr_dialog_title)
                .setMessage(message)
                .show();
    }

    private void putToClipboard(Editable text) {
        ClipboardManager manager = (ClipboardManager) mApp.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null)
            return;
        String s = text.toString();
        if (! s.isEmpty()) {
            ClipData data = ClipData.newPlainText(CLIPBOARD_LABEL, s);
            manager.setPrimaryClip(data);
        }
    }

    private class SavePreferencesTextWatcher implements TextWatcher {
        private String mPreferenceKey;

        SavePreferencesTextWatcher(String preference) {
            mPreferenceKey = preference;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) { }

        @Override
        public void afterTextChanged(Editable s) {
            mSharedPreferencesHandler.set(mPreferenceKey, s.toString());
        }
    }

    private class LocationTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) { }

        @Override
        public void afterTextChanged(Editable s) {
            mCommentText.setText("");
            executeCommentSuggestion();
        }
    }

    private void executeCommentSuggestion() {
        if (! mSharedPreferencesHandler.get(R.string.pref_login_values_ok_key, false))
            return;
        new CommentSuggestion(mApiCaller, (EbtNewNote) getActivity(), mDataStore)
                .execute(new LocationValues(
                        mCountryText.getText().toString(),
                        mCityText.getText().toString(),
                        mPostalCodeText.getText().toString()));
    }
}
