package io.github.kamaje122.calwidget;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CalendarContract;

/**
 * 小工具清單項目的點擊中繼站。
 *
 * 清單裡每一列共用同一個 PendingIntent 範本，而系統規定這種範本必須指向明確的元件，
 * 所以先開這個沒有畫面的 Activity，再由它轉去行事曆 App。
 */
public class OpenActivity extends Activity {

    static final String EXTRA_EVENT_ID = "eventId";
    static final String EXTRA_BEGIN = "begin";
    static final String EXTRA_END = "end";
    static final String EXTRA_DAY_MILLIS = "dayMillis";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        Intent out;
        long eventId = in.getLongExtra(EXTRA_EVENT_ID, -1);
        if (eventId >= 0) {
            out = new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI, eventId))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                            in.getLongExtra(EXTRA_BEGIN, 0))
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,
                            in.getLongExtra(EXTRA_END, 0));
        } else {
            out = new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(
                    CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build(),
                    in.getLongExtra(EXTRA_DAY_MILLIS, System.currentTimeMillis())));
        }
        try {
            startActivity(out.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
            // 手機上沒有能開啟行事曆的 App
        }
        finish();
    }
}
