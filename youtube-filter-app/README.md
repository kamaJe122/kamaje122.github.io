# 淨化 YouTube（YtFilter）

給 Pixel 10 Pro XL（Android 17）用的個人化 YouTube App：在影片出現在首頁**之前**就把不想看的擋掉。

```
 YouTube Data API  ──►  ① 關鍵字  ──►  ② 頻道黑名單  ──►  ③ AI 封面認人  ──►  首頁
 (熱門/訂閱/搜尋)        (≈0.1ms)        (≈0ms)            (≈20–40ms, 手機上跑)
                          │                │                    │
                          └────────── 被擋的影片 + 原因 ◄────────┘  （首頁上「🛡️ 已擋下 N 支」可查看）
```

## 專案結構

| 模組 | 內容 | 能否在沒有 Android SDK 的電腦測試 |
|---|---|---|
| `core/` | 純 Kotlin 過濾邏輯：`KeywordFilter`、`FaceMatcher`、`FilterPipeline` | ✅ `./gradlew :core:test` |
| `app/` | Android App（Jetpack Compose）：API、登入、ML Kit + LiteRT、畫面、播放器 | 需要 Android Studio |

## 第一次設定（約 15 分鐘）

1. **Android Studio** 開啟 `youtube-filter-app/` 資料夾。
2. **API 金鑰**：到 [Google Cloud Console](https://console.cloud.google.com/) → 建立專案 → 啟用「YouTube Data API v3」→ 憑證 → 建立 API 金鑰。
   在 `youtube-filter-app/local.properties` 加一行：
   ```
   YOUTUBE_API_KEY=你的金鑰
   ```
3. **看訂閱（選用）**：同一個專案 → OAuth 同意畫面（測試使用者加入自己的 Gmail）→ 憑證 → 建立 OAuth 用戶端 ID，類型選「Android」，
   套件名稱 `app.ytfilter`，SHA-1 用 `./gradlew signingReport` 取得的 debug SHA-1。
4. **AI 認人模型（選用）**：下載 MobileFaceNet 的 `.tflite`（輸入 112×112、輸出 128/192 維），改名為 `mobilefacenet.tflite` 放到 `app/src/main/assets/`。
   沒放也能用，只是第 ③ 關會停用。
5. 手機開啟開發者模式 + USB 偵錯 → 按 ▶ Run。

## 使用方式

- **關鍵字**：右上角 🛡️ → 輸入關鍵字。不分大小寫、全形半形（`ＩＰＨＯＮＥ` = `iphone`）。`re:` 開頭可寫正規表達式。
- **封鎖頻道**：首頁長按影片 → 封鎖頻道。
- **封鎖某個人**：🛡️ → 輸入名字 → 選 3~5 張他的清楚照片。只會儲存臉部特徵數字，照片本身不存也不上傳。
  擋太多就把靈敏度往右調，漏擋就往左調。
- 改規則只會重新過濾已載入的影片，**不會**多扣 API 額度。

## 已知限制

- 官方 API 沒有「為你推薦」首頁，所以首頁是 熱門 / 訂閱 / 搜尋。
- 每日免費額度 10,000 點：搜尋一次 100 點，其他動作約 1 點。
- 播放使用官方 IFrame 播放器（API 條款要求），廣告照常會出現。
- 登入 token 約一小時過期，到期後會提示重新登入。
