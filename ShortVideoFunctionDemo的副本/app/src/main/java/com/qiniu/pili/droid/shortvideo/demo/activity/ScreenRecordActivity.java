package com.qiniu.pili.droid.shortvideo.demo.activity;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.qiniu.pili.droid.shortvideo.PLErrorCode;
import com.qiniu.pili.droid.shortvideo.PLMicrophoneSetting;
import com.qiniu.pili.droid.shortvideo.PLScreenRecordStateListener;
import com.qiniu.pili.droid.shortvideo.PLScreenRecorder;
import com.qiniu.pili.droid.shortvideo.PLScreenRecorderSetting;
import com.qiniu.pili.droid.shortvideo.demo.R;
import com.qiniu.pili.droid.shortvideo.demo.utils.Config;
import com.qiniu.pili.droid.shortvideo.demo.utils.PermissionChecker;
import com.qiniu.pili.droid.shortvideo.demo.utils.MediaStoreUtils;
import com.qiniu.pili.droid.shortvideo.demo.utils.ToastUtils;

import java.io.File;

import static android.app.Notification.VISIBILITY_PRIVATE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

public class ScreenRecordActivity extends AppCompatActivity implements PLScreenRecordStateListener {
    private static final String TAG = "ScreenRecordActivity";

    private final int NOTIFICATION_ID = 1010100;

    private PLScreenRecorder mScreenRecorder;
    private TextView mTipTextView;

    private void requestScreenRecord() {
        if (mScreenRecorder == null) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            int dpi = metrics.densityDpi;
            mScreenRecorder = new PLScreenRecorder(this);
            mScreenRecorder.setRecordStateListener(this);
            PLScreenRecorderSetting screenSetting = new PLScreenRecorderSetting();
            screenSetting.setRecordFile(Config.SCREEN_RECORD_FILE_PATH)
                            .setInputAudioEnabled(false)
                            .setSize(width, height)
                            .setDpi(dpi)
                            .setFps(60);
            PLMicrophoneSetting microphoneSetting = new PLMicrophoneSetting();
            mScreenRecorder.prepare(screenSetting, microphoneSetting);
        }
        mScreenRecorder.setNotification(NOTIFICATION_ID, createNotification());
        mScreenRecorder.requestScreenRecord();
    }

    private void stopScreenRecord() {
        if (mScreenRecorder != null && mScreenRecorder.isRecording()) {
            mScreenRecorder.stop();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_record);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(false);
        }

        setTitle(R.string.title_screen_record);

        mTipTextView = (TextView) findViewById(R.id.tip);
        FloatingActionButton fab_rec = (FloatingActionButton) findViewById(R.id.fab_rec);
        fab_rec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PermissionChecker checker = new PermissionChecker(ScreenRecordActivity.this);
                boolean isPermissionOK = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || checker.checkPermission();
                if (!isPermissionOK) {
                    ToastUtils.showShortToast(view.getContext(), "相关权限申请失败 !!!");
                    return;
                }

                if (mScreenRecorder != null && mScreenRecorder.isRecording()) {
                    stopScreenRecord();
                } else {
                    requestScreenRecord();
                    Snackbar.make(view, "正在申请录屏权限……", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                }
            }
        });

        FloatingActionButton fab_play = (FloatingActionButton) findViewById(R.id.fab_play);
        fab_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mScreenRecorder != null && mScreenRecorder.isRecording()) {
                    Snackbar.make(view, "正在录屏，不能播放！", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    return;
                }

                PlaybackActivity.start(ScreenRecordActivity.this, Config.SCREEN_RECORD_FILE_PATH);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mScreenRecorder != null) {
            mScreenRecorder.stop();
            mScreenRecorder = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PLScreenRecorder.REQUEST_CODE) {
            if (data == null) {
                String tip = "录屏申请启动失败！";
                Toast.makeText(this, tip, Toast.LENGTH_SHORT).show();
                ((TextView) findViewById(R.id.tip)).setText(tip);
                mScreenRecorder.stop();
                mScreenRecorder = null;
                return;
            }

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mScreenRecorder.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void updateTip(String tip) {
        mTipTextView.setText(tip);
        Toast.makeText(this, tip, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onReady() {
        mScreenRecorder.start();
        updateTip("录屏初始化成功！");
        Toast.makeText(this, "正在进行录屏...", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    @Override
    public void onError(int code) {
        Log.e(TAG, "onError: code = " + code);
        if (code == PLErrorCode.ERROR_UNSUPPORTED_ANDROID_VERSION) {
            final String tip = "录屏只支持 Android 5.0 以上系统";
            runOnUiThread(() -> updateTip(tip));
        }
    }

    @Override
    public void onRecordStarted() {
        runOnUiThread(() -> {
            String tip = "正在录屏……";
            updateTip(tip);
        });
    }

    @Override
    public void onRecordStopped() {
        MediaStoreUtils.storeVideo(ScreenRecordActivity.this, new File(Config.SCREEN_RECORD_FILE_PATH), "video/mp4");
        runOnUiThread(() -> {
            String tip = "已经停止录屏！";
            updateTip(tip);
        });
    }

    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this.getApplicationContext(), "screenRecorder");
        } else {
            builder = new Notification.Builder(this.getApplicationContext());
        }
        Intent intent = new Intent(this, ScreenRecordActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT);
        builder.setSmallIcon(R.drawable.qiniu_logo)
                .setContentTitle("七牛推流")
                .setContentText("正在录屏ing")
                .setContentIntent(pendingIntent)
                .setShowWhen(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(VISIBILITY_PRIVATE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("screenRecorder", "screenRecorder", NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
        return builder.build();
    }

}
