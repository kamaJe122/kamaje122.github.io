package io.github.kamaje122.calwidget;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

/** 權限、把小工具釘到桌面，以及小工具的顯示設定。 */
public class MainActivity extends Activity {

    private static final int REQUEST_CALENDAR = 1;
    private static final String[] CALENDAR_PERMISSIONS = {
            Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR};

    private TextView status;
    private Button grant;
    private LinearLayout calendars;
    private boolean askedOnce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        grant = findViewById(R.id.btn_grant);
        calendars = findViewById(R.id.calendars);

        grant.setOnClickListener(v -> {
            if (askedOnce && !shouldShowRequestPermissionRationale(
                    Manifest.permission.READ_CALENDAR)) {
                // 已被永久拒絕，系統不會再跳視窗，直接帶去設定頁
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            } else {
                requestPermissions(CALENDAR_PERMISSIONS, REQUEST_CALENDAR);
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

        TextView daysValue = findViewById(R.id.days_value);
        daysValue.setText(getString(R.string.days_value,
                CalendarWidgetProvider.daysToShow(this)));
        View.OnClickListener changeDays = v -> {
            int delta = v.getId() == R.id.btn_days_plus ? 1 : -1;
            CalendarWidgetProvider.setDaysToShow(this,
                    CalendarWidgetProvider.daysToShow(this) + delta);
            daysValue.setText(getString(R.string.days_value,
                    CalendarWidgetProvider.daysToShow(this)));
            CalendarWidgetProvider.updateAll(this);
        };
        findViewById(R.id.btn_days_minus).setOnClickListener(changeDays);
        findViewById(R.id.btn_days_plus).setOnClickListener(changeDays);

        Switch allDay = findViewById(R.id.switch_all_day);
        allDay.setChecked(CalendarWidgetProvider.showAllDay(this));
        allDay.setOnCheckedChangeListener((button, checked) -> {
            CalendarWidgetProvider.setShowAllDay(this, checked);
            CalendarWidgetProvider.updateAll(this);
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
        showCalendars(granted);
        CalendarWidgetProvider.updateAll(this);
    }

    /** 列出手機上同步到的所有帳號與日曆，讓使用者確認並勾選要顯示哪些。 */
    private void showCalendars(boolean granted) {
        calendars.removeAllViews();
        if (!granted) {
            calendars.addView(note(getString(R.string.calendars_need_permission)));
            return;
        }

        Set<String> hidden = CalendarWidgetProvider.hiddenCalendars(this);
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.SYNC_EVENTS,
                CalendarContract.Calendars.ACCOUNT_TYPE};
        int accounts = 0;
        int total = 0;
        int shown = 0;
        TextView summary = note("");
        calendars.addView(summary);

        try (Cursor c = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                projection, null, null,
                CalendarContract.Calendars.ACCOUNT_NAME + " COLLATE NOCASE ASC, "
                        + CalendarContract.Calendars.IS_PRIMARY + " DESC, "
                        + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
                        + " COLLATE NOCASE ASC")) {
            String lastAccount = null;
            while (c != null && c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1);
                String account = c.getString(2);
                int color = c.getInt(3) | 0xFF000000;
                // 系統資料庫裡這個日曆是否「有同步行程」且「標記為顯示」
                boolean available = c.getInt(4) != 0 && c.getInt(5) != 0;
                String accountType = c.getString(6);
                if (TextUtils.isEmpty(name)) {
                    name = getString(R.string.untitled);
                }
                final String calendarName = name;
                if (account == null) {
                    account = "";
                }

                if (!account.equals(lastAccount)) {
                    lastAccount = account;
                    accounts++;
                    TextView header = new TextView(this);
                    header.setText(account);
                    header.setTextSize(15);
                    header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
                    header.setPadding(0, dp(14), 0, dp(2));
                    calendars.addView(header);
                }

                String label = available ? name : getString(R.string.calendar_not_synced, name);
                SpannableString text = new SpannableString("● " + label);
                text.setSpan(new ForegroundColorSpan(color), 0, 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                CheckBox box = new CheckBox(this);
                box.setText(text);
                box.setTextSize(15);
                box.setMinHeight(dp(44));
                boolean checked = available && !hidden.contains(String.valueOf(id));
                box.setChecked(checked);
                final String accountName = account;
                box.setOnCheckedChangeListener((button, isChecked) -> {
                    if (isChecked && !available) {
                        boolean ok = enableSync(id, accountName, accountType);
                        Toast.makeText(this, getString(
                                ok ? R.string.sync_requested : R.string.sync_failed,
                                calendarName), Toast.LENGTH_LONG).show();
                    }
                    CalendarWidgetProvider.setCalendarHidden(this, id, !isChecked);
                    CalendarWidgetProvider.updateAll(this);
                    // 等這次點擊處理完再重畫清單，更新上方的統計數字
                    calendars.post(() -> showCalendars(true));
                });
                calendars.addView(box);

                total++;
                if (checked) {
                    shown++;
                }
            }
        } catch (SecurityException ignored) {
            // 權限在查詢途中被收回：當作沒有日曆
        }

        if (total == 0) {
            summary.setText(R.string.calendars_none);
        } else {
            summary.setText(getString(R.string.calendar_summary, accounts, total, shown));
        }
    }

    /**
     * 打開某個日曆在系統資料庫裡的「同步行程」與「顯示」開關，並請該帳號立刻同步一次。
     * 只改這兩個開關，不碰任何行程內容。
     */
    private boolean enableSync(long calendarId, String accountName, String accountType) {
        if (checkSelfPermission(Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(CALENDAR_PERMISSIONS, REQUEST_CALENDAR);
            return false;
        }
        try {
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
            values.put(CalendarContract.Calendars.VISIBLE, 1);
            int rows = getContentResolver().update(ContentUris.withAppendedId(
                    CalendarContract.Calendars.CONTENT_URI, calendarId), values, null, null);
            if (rows == 0) {
                return false;
            }
            if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(accountType)) {
                Bundle extras = new Bundle();
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                ContentResolver.requestSync(new Account(accountName, accountType),
                        CalendarContract.AUTHORITY, extras);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private TextView note(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
