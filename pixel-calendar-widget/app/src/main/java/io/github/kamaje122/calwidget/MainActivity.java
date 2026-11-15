package io.github.kamaje122.calwidget;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/** 只負責兩件事：要行事曆讀取權限、把小工具釘到桌面。 */
public class MainActivity extends Activity {

    private static final int REQUEST_CALENDAR = 1;

    private TextView status;
    private Button grant;
    private boolean askedOnce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        grant = findViewById(R.id.btn_grant);

        grant.setOnClickListener(v -> {
            if (askedOnce && !shouldShowRequestPermissionRationale(
                    Manifest.permission.READ_CALENDAR)) {
                // 已被永久拒絕，系統不會再跳視窗，直接帶去設定頁
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},
                        REQUEST_CALENDAR);
            }
        });

        findViewById(R.id.btn_pin).setOnClickListener(v -> {
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            if (manager.isRequestPinAppWidgetSupported()) {
                manager.requestPinAppWidget(
                        new ComponentName(this, CalendarWidgetProvider.class), null, null);
            } else {
                Toast.makeText(this, R.string.pin_unsupported, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        askedOnce = true;
        refresh();
    }

    private void refresh() {
        boolean granted = checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        status.setText(granted ? R.string.status_granted : R.string.status_denied);
        grant.setEnabled(!granted);
        if (!granted && askedOnce && !shouldShowRequestPermissionRationale(
                Manifest.permission.READ_CALENDAR)) {
            grant.setText(R.string.btn_open_settings);
        } else {
            grant.setText(R.string.btn_grant);
        }
        CalendarWidgetProvider.updateAll(this);
    }
}
