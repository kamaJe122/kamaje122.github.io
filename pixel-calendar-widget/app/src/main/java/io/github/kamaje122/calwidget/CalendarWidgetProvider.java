package io.github.kamaje122.calwidget;

import android.Manifest;
import android.accounts.Account;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.JulianFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 5×2 桌面小工具：左邊月曆（含週數）、右邊選取日的行程。
 *
 * 資料來源是系統的行事曆同步資料（CalendarContract），所以手機上所有已登入、
 * 且在 Google 日曆中設為顯示的帳號 / 日曆都會自動出現，不需要另外登入。
 */
public class CalendarWidgetProvider extends AppWidgetProvider {

    static final String ACTION_SELECT = "io.github.kamaje122.calwidget.SELECT";
    static final String ACTION_TODAY = "io.github.kamaje122.calwidget.TODAY";
    static final String ACTION_TICK = "io.github.kamaje122.calwidget.TICK";

    private static final String PREFS = "widget";
    private static final String KEY_SELECTED = "selectedEpochDay";
    private static final String KEY_SELECTED_ON = "selectedOnEpochDay";
    private static final String KEY_SHOW_ALL_DAY = "showAllDay";
    private static final String KEY_DAYS = "daysToShow";
    private static final String KEY_LAST_SYNC = "lastSyncRequest";

    /** 使用者碰小工具或打開 App 時：距離上次催同步超過這段時間才再催一次。 */
    static final long SYNC_ON_TOUCH_MS = 20_000L;
    /** 背景定時（每 30 分鐘 / 每整點）催同步的最短間隔。 */
    static final long SYNC_BACKGROUND_MS = 25 * 60_000L;
    private static final String KEY_HIDDEN_CALENDARS = "hiddenCalendars";

    private static final String[] WEEKDAYS = {"週一", "週二", "週三", "週四", "週五", "週六", "週日"};

    private static final int[] WEEK_IDS = {R.id.w0, R.id.w1, R.id.w2, R.id.w3, R.id.w4, R.id.w5};
    private static final int[] DAY_IDS = {
            R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6,
            R.id.d7, R.id.d8, R.id.d9, R.id.d10, R.id.d11, R.id.d12, R.id.d13,
            R.id.d14, R.id.d15, R.id.d16, R.id.d17, R.id.d18, R.id.d19, R.id.d20,
            R.id.d21, R.id.d22, R.id.d23, R.id.d24, R.id.d25, R.id.d26, R.id.d27,
            R.id.d28, R.id.d29, R.id.d30, R.id.d31, R.id.d32, R.id.d33, R.id.d34,
            R.id.d35, R.id.d36, R.id.d37, R.id.d38, R.id.d39, R.id.d40, R.id.d41};
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_SELECT.equals(action) && intent.getData() != null) {
            try {
                long day = Long.parseLong(intent.getData().getLastPathSegment());
                prefs(context).edit()
                        .putLong(KEY_SELECTED, day)
                        .putLong(KEY_SELECTED_ON, LocalDate.now().toEpochDay())
                        .apply();
            } catch (NumberFormatException ignored) {
                // 格式不對就當作沒點
            }
            requestSync(context, SYNC_ON_TOUCH_MS);
            updateAll(context);
        } else if (ACTION_TODAY.equals(action)) {
            prefs(context).edit().remove(KEY_SELECTED).remove(KEY_SELECTED_ON).apply();
            requestSync(context, SYNC_ON_TOUCH_MS);
            updateAll(context);
        } else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED.equals(action)) {
            super.onReceive(context, intent);
        } else if (action != null
                && !AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)
                && !AppWidgetManager.ACTION_APPWIDGET_DISABLED.equals(action)
                && !AppWidgetManager.ACTION_APPWIDGET_ENABLED.equals(action)) {
            // 行事曆變動、時間 / 時區變動、整點或跨日鬧鐘 → 重畫
            if (ACTION_TICK.equals(action)) {
                requestSync(context, SYNC_BACKGROUND_MS);
            }
            updateAll(context);
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        requestSync(context, SYNC_BACKGROUND_MS);
        updateAll(context);
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms != null) {
            alarms.cancel(tickIntent(context));
        }
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName me = new ComponentName(context, CalendarWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(me);
        if (ids.length == 0) {
            return;
        }
        manager.updateAppWidget(me, buildViews(context));
        scheduleNextTick(context);
    }

    /**
     * 請 Google 把雲端的行程同步進手機。同步完成、資料有變動時系統會發出
     * PROVIDER_CHANGED，小工具收到後自動重畫，所以這裡只負責「催」。
     *
     * 注意：不可以在收到 PROVIDER_CHANGED 時呼叫，否則會變成 同步 → 變動 → 再同步 的迴圈。
     */
    static void requestSync(Context context, long minIntervalMs) {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = prefs(context).getLong(KEY_LAST_SYNC, 0);
        if (now >= last && now - last < minIntervalMs) {
            return;
        }
        prefs(context).edit().putLong(KEY_LAST_SYNC, now).apply();

        // 找出有開啟同步的日曆屬於哪些帳號，每個帳號催一次
        Set<String> seen = new HashSet<>();
        String[] projection = {
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE};
        try (Cursor c = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI, projection,
                CalendarContract.Calendars.SYNC_EVENTS + " = 1", null, null)) {
            while (c != null && c.moveToNext()) {
                String name = c.getString(0);
                String type = c.getString(1);
                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(type)
                        || !seen.add(type + "/" + name)) {
                    continue;
                }
                Bundle extras = new Bundle();
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                ContentResolver.requestSync(new Account(name, type),
                        CalendarContract.AUTHORITY, extras);
            }
        } catch (RuntimeException ignored) {
            // 催不動就算了，下次再試；不影響小工具顯示現有資料
        }
    }

    // ───────────────────────── 設定（給 MainActivity 用） ─────────────────────────

    static final int MAX_DAYS = 14;

    /** 右邊清單要顯示幾天（從選取日起算）。 */
    static int daysToShow(Context context) {
        return Math.max(1, Math.min(MAX_DAYS, prefs(context).getInt(KEY_DAYS, 1)));
    }

    static void setDaysToShow(Context context, int days) {
        prefs(context).edit().putInt(KEY_DAYS, Math.max(1, Math.min(MAX_DAYS, days))).apply();
    }

    static boolean showAllDay(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_ALL_DAY, true);
    }

    static void setShowAllDay(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_SHOW_ALL_DAY, show).apply();
    }

    /** 使用者取消勾選的日曆 ID。 */
    static Set<String> hiddenCalendars(Context context) {
        return new HashSet<>(prefs(context)
                .getStringSet(KEY_HIDDEN_CALENDARS, Collections.emptySet()));
    }

    static void setCalendarHidden(Context context, long calendarId, boolean hidden) {
        Set<String> set = hiddenCalendars(context);
        if (hidden) {
            set.add(String.valueOf(calendarId));
        } else {
            set.remove(String.valueOf(calendarId));
        }
        prefs(context).edit().putStringSet(KEY_HIDDEN_CALENDARS, set).apply();
    }

    // ───────────────────────── 畫面 ─────────────────────────

    private static RemoteViews buildViews(Context context) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate selected = selectedDay(context, today);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_calendar);

        // 左上：月份 + 週數
        views.setTextViewText(R.id.month_title,
                context.getString(R.string.month_title, selected.getMonthValue()));
        views.setTextViewText(R.id.week_title,
                context.getString(R.string.week_title, weekNumber(selected)));
        views.setOnClickPendingIntent(R.id.month_header,
                broadcast(context, new Intent(context, CalendarWidgetProvider.class)
                        .setAction(ACTION_TODAY)));

        // 月曆：以週日為每列第一天，固定 6 列
        LocalDate first = selected.withDayOfMonth(1);
        LocalDate gridStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        for (int row = 0; row < 6; row++) {
            views.setTextViewText(WEEK_IDS[row],
                    String.valueOf(weekNumber(gridStart.plusDays(row * 7L))));
        }
        for (int i = 0; i < 42; i++) {
            LocalDate day = gridStart.plusDays(i);
            int id = DAY_IDS[i];
            views.setTextViewText(id, String.valueOf(day.getDayOfMonth()));

            int color;
            if (day.equals(selected)) {
                color = R.color.on_accent;
                views.setInt(id, "setBackgroundResource", R.drawable.day_selected);
            } else {
                views.setInt(id, "setBackgroundResource", 0);
                if (day.equals(today)) {
                    color = R.color.accent;
                } else if (day.getMonth() == selected.getMonth()) {
                    color = R.color.text_primary;
                } else {
                    color = R.color.text_dim;
                }
            }
            // 用「色彩資源」而不是寫死的顏色，深色 / 淺色模式切換時桌面會自己換色
            views.setColor(id, "setTextColor", color);

            Intent select = new Intent(context, CalendarWidgetProvider.class)
                    .setAction(ACTION_SELECT)
                    .setData(Uri.parse("calwidget://select/" + day.toEpochDay()));
            views.setOnClickPendingIntent(id, broadcast(context, select));
        }

        // 右上：星期 + 日期
        views.setTextViewText(R.id.weekday_title,
                WEEKDAYS[selected.getDayOfWeek().getValue() - 1]);
        views.setTextViewText(R.id.day_title,
                context.getString(R.string.day_title, selected.getDayOfMonth()));

        long dayStart = selected.atStartOfDay(zone).toInstant().toEpochMilli();
        PendingIntent openDay = activity(context, 1, new Intent(Intent.ACTION_VIEW,
                ContentUris.withAppendedId(
                        CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build(),
                        dayStart)));

        boolean granted = context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            views.setViewVisibility(R.id.agenda_list, View.GONE);
            views.setViewVisibility(R.id.empty, View.VISIBLE);
            views.setTextViewText(R.id.empty, context.getString(R.string.need_permission));
            PendingIntent openApp = activity(context, 2,
                    new Intent(context, MainActivity.class));
            views.setOnClickPendingIntent(R.id.agenda, openApp);
            views.setOnClickPendingIntent(R.id.day_header, openApp);
            views.setOnClickPendingIntent(R.id.empty, openApp);
        } else {
            views.setViewVisibility(R.id.empty, View.GONE);
            views.setViewVisibility(R.id.agenda_list, View.VISIBLE);
            views.setOnClickPendingIntent(R.id.day_header, openDay);
            views.setRemoteAdapter(R.id.agenda_list,
                    buildAgenda(context, selected, today, zone));

            // 清單每一列共用這個範本，各列再用 fill-in intent 補上自己的行程資料。
            // 範本必須可變（FLAG_MUTABLE），所以指向 App 內明確的 OpenActivity。
            PendingIntent template = PendingIntent.getActivity(context, 20,
                    new Intent(context, OpenActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            views.setPendingIntentTemplate(R.id.agenda_list, template);
        }

        // 右下「＋」：開啟行事曆 App 的新增畫面，預設帶入選取日的時間
        ZonedDateTime begin;
        if (selected.equals(today)) {
            begin = ZonedDateTime.now(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1);
        } else {
            begin = selected.atTime(LocalTime.of(9, 0)).atZone(zone);
        }
        long beginMillis = begin.toInstant().toEpochMilli();
        Intent insert = new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginMillis + 3_600_000L);
        views.setOnClickPendingIntent(R.id.add, activity(context, 3, insert));

        return views;
    }

    /** 組出右邊可滑動的清單：選取日的行程，接著是後面幾天（各有日期標題）。 */
    private static RemoteViews.RemoteCollectionItems buildAgenda(Context context,
            LocalDate selected, LocalDate today, ZoneId zone) {
        String pkg = context.getPackageName();
        RemoteViews.RemoteCollectionItems.Builder items =
                new RemoteViews.RemoteCollectionItems.Builder()
                        .setHasStableIds(false)
                        .setViewTypeCount(3);
        long nextId = 0;
        int days = daysToShow(context);

        for (int d = 0; d < days; d++) {
            LocalDate day = selected.plusDays(d);
            long dayMillis = day.atStartOfDay(zone).toInstant().toEpochMilli();
            Intent openDay = new Intent().putExtra(OpenActivity.EXTRA_DAY_MILLIS, dayMillis);

            if (d > 0) {
                // 第一天的標題已經固定在清單上方，之後每一天在清單裡各放一列標題
                RemoteViews header = new RemoteViews(pkg, R.layout.widget_day_item);
                header.setTextViewText(R.id.item_weekday,
                        WEEKDAYS[day.getDayOfWeek().getValue() - 1]);
                header.setTextViewText(R.id.item_day,
                        context.getString(R.string.day_title, day.getDayOfMonth()));
                header.setOnClickFillInIntent(R.id.item_root, openDay);
                items.addItem(nextId++, header);
            }

            boolean isToday = day.equals(today);
            List<Event> events = loadEvents(context, day, zone, isToday);
            if (events.isEmpty()) {
                RemoteViews empty = new RemoteViews(pkg, R.layout.widget_empty_item);
                empty.setTextViewText(R.id.item_root, context.getString(
                        isToday ? R.string.no_more_events : R.string.no_events));
                empty.setOnClickFillInIntent(R.id.item_root, openDay);
                items.addItem(nextId++, empty);
            }
            for (Event e : events) {
                RemoteViews row = new RemoteViews(pkg, R.layout.widget_event_item);
                row.setInt(R.id.item_bar, "setColorFilter", e.color | 0xFF000000);
                row.setTextViewText(R.id.item_title, e.title);
                row.setTextViewText(R.id.item_time, e.subtitle);
                row.setOnClickFillInIntent(R.id.item_root, new Intent()
                        .putExtra(OpenActivity.EXTRA_EVENT_ID, e.eventId)
                        .putExtra(OpenActivity.EXTRA_BEGIN, e.begin)
                        .putExtra(OpenActivity.EXTRA_END, e.end));
                items.addItem(nextId++, row);
            }
        }
        return items.build();
    }

    /**
     * 週數：每列是「週日～週六」，取該列週四所在的 ISO 週數。
     * 這樣 2025/10/26～11/1 是第 44 週、11/16～11/22 是第 47 週，與 Notion Calendar 相同。
     */
    static int weekNumber(LocalDate day) {
        LocalDate sunday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        return sunday.plusDays(4).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private static LocalDate selectedDay(Context context, LocalDate today) {
        SharedPreferences p = prefs(context);
        // 手動選的日期只在「當天」有效，跨日後自動回到今天
        if (p.contains(KEY_SELECTED) && p.getLong(KEY_SELECTED_ON, -1) == today.toEpochDay()) {
            return LocalDate.ofEpochDay(p.getLong(KEY_SELECTED, today.toEpochDay()));
        }
        return today;
    }

    // ───────────────────────── 行程 ─────────────────────────

    private static final class Event {
        long eventId;
        long begin;
        long end;
        boolean allDay;
        int color;
        String title;
        String subtitle;
    }

    private static List<Event> loadEvents(Context context, LocalDate day, ZoneId zone,
            boolean hideFinished) {
        List<Event> result = new ArrayList<>();
        long dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long julianDay = day.getLong(JulianFields.JULIAN_DAY);
        long now = System.currentTimeMillis();
        boolean showAllDay = showAllDay(context);
        Set<String> hiddenCalendars = hiddenCalendars(context);

        // 全天行程在資料庫裡是以 UTC 存的，所以前後多抓一天，再用「第幾天」精準過濾
        Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, dayStart - 86_400_000L);
        ContentUris.appendId(uri, dayEnd + 86_400_000L);

        String[] projection = {
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.START_DAY,
                CalendarContract.Instances.END_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.SELF_ATTENDEE_STATUS,
                CalendarContract.Instances.CALENDAR_ID};

        try (Cursor c = context.getContentResolver().query(uri.build(), projection,
                CalendarContract.Instances.VISIBLE + " = 1", null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (c == null) {
                return result;
            }
            while (c.moveToNext()) {
                if (c.getInt(9) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) {
                    continue; // 已婉拒的邀請不顯示
                }
                if (hiddenCalendars.contains(String.valueOf(c.getLong(10)))) {
                    continue; // 使用者在 App 裡取消勾選的日曆
                }
                Event e = new Event();
                e.eventId = c.getLong(0);
                e.begin = c.getLong(2);
                e.end = c.getLong(3);
                e.allDay = c.getInt(4) != 0;
                if (e.allDay) {
                    if (!showAllDay) {
                        continue;
                    }
                    if (c.getLong(5) > julianDay || c.getLong(6) < julianDay) {
                        continue;
                    }
                } else {
                    boolean startsToday = e.begin >= dayStart && e.begin < dayEnd;
                    boolean overlaps = e.begin < dayStart && e.end > dayStart;
                    if (!startsToday && !overlaps) {
                        continue;
                    }
                    if (hideFinished && e.end <= now && e.end > e.begin) {
                        continue; // 今天已經結束的行程讓位給接下來的
                    }
                }
                String title = c.getString(1);
                e.title = TextUtils.isEmpty(title) ? context.getString(R.string.untitled) : title;
                e.color = c.getInt(8);

                String when;
                if (e.allDay) {
                    when = context.getString(R.string.all_day);
                } else {
                    when = hm(e.begin, zone) + "–" + hm(e.end, zone);
                }
                String location = c.getString(7);
                e.subtitle = TextUtils.isEmpty(location) ? when : when + " · " + location.trim();
                result.add(e);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // 權限剛被收回或行事曆儲存空間不可用：當作沒有行程
        }

        // 全天行程排最前面，其餘依開始時間
        result.sort((a, b) -> {
            if (a.allDay != b.allDay) {
                return a.allDay ? -1 : 1;
            }
            return Long.compare(a.begin, b.begin);
        });
        return result;
    }

    private static String hm(long millis, ZoneId zone) {
        LocalTime t = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime();
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    // ───────────────────────── 小工具 ─────────────────────────

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static PendingIntent broadcast(Context context, Intent intent) {
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent activity(Context context, int requestCode, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent tickIntent(Context context) {
        return broadcast(context, new Intent(context, CalendarWidgetProvider.class)
                .setAction(ACTION_TICK));
    }

    /** 每個整點（含午夜跨日）重畫一次，讓「今天」與已結束的行程保持正確。 */
    private static void scheduleNextTick(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) {
            return;
        }
        ZonedDateTime next = ZonedDateTime.now()
                .withMinute(0).withSecond(0).withNano(0).plusHours(1);
        alarms.set(AlarmManager.RTC, next.toInstant().toEpochMilli() + 1_000L,
                tickIntent(context));
    }
}
