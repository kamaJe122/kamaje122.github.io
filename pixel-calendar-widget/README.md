# 月曆小工具（Android 桌面 5×2）

左邊月曆（含週數）、右邊當日行程的桌面小工具，版面參考 Notion Calendar 的 iOS 小工具。

- 直接讀取手機的行事曆同步資料（`CalendarContract`），所有已登入的 Google 帳號都會自動出現，顏色與 Google 日曆一致，不需要另外登入。
- 只要求一個權限：`READ_CALENDAR`（唯讀）。沒有網路權限。
- 最低支援 Android 12（API 31）。

## 操作

| 點哪裡 | 會發生什麼 |
| --- | --- |
| 任一日期 | 右邊改顯示那一天的行程 |
| 灰色日期 | 跳到上／下個月 |
| 左上角月份 | 回到今天 |
| 行程 | 在行事曆 App 開啟該行程 |
| ＋ | 在行事曆 App 新增行程 |

## 建置

```
./gradlew assembleRelease
```

推送後 GitHub Actions 會自動建置；合併到 `main` 後 APK 會出現在 Release `calendar-widget-latest`。

`keystore/sideload.jks` 是公開的側載用簽章（密碼 `android`），只為了讓每次建出來的 APK 可以互相覆蓋更新，不適合拿來上架。
