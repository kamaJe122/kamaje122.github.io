/**
 * CopyKing — Gemini 代理（貼到你的 Google Apps Script 專案裡）
 *
 * 金鑰只存在 GAS 的「指令碼屬性」，不會出現在前端網頁。
 *
 * 設定方式：
 *   1. Apps Script 編輯器 → 左側齒輪「專案設定」→ 最下方「指令碼屬性」→ 新增：
 *        GEMINI_API_KEY = 你重新產生的新金鑰
 *        GEMINI_MODEL   = gemini-2.5-flash   （選填，不填就用這個預設值）
 *   2. 在你現有的 doPost(e) 裡，解析完 JSON 之後加上這一行：
 *        if (data.action === 'aiTags') return handleAiTags(data);
 *   3. 「部署」→「管理部署作業」→ 編輯 → 版本選「新版本」→ 部署
 *      （網址不會變，前端不用改 GAS_URL）
 */

function handleAiTags(data) {
  var content = String(data.content || '').slice(0, 2000);
  var tags = '一般';

  try {
    var props = PropertiesService.getScriptProperties();
    var apiKey = props.getProperty('GEMINI_API_KEY');
    var model = props.getProperty('GEMINI_MODEL') || 'gemini-2.5-flash';
    if (apiKey && content) {
      var url = 'https://generativelanguage.googleapis.com/v1beta/models/' + model + ':generateContent';
      var res = UrlFetchApp.fetch(url, {
        method: 'post',
        contentType: 'application/json',
        headers: { 'x-goog-api-key': apiKey },
        muteHttpExceptions: true,
        payload: JSON.stringify({
          contents: [{ parts: [{ text: 'Generate 1-3 tags for: "' + content + '" in Traditional Chinese, comma separated. Reply with the tags only.' }] }]
        })
      });
      var json = JSON.parse(res.getContentText());
      var t = json.candidates && json.candidates[0] && json.candidates[0].content &&
              json.candidates[0].content.parts && json.candidates[0].content.parts[0].text;
      if (t) tags = t.replace(/\n/g, '').trim();
    }
  } catch (err) {
    console.error('handleAiTags error: ' + err);
  }

  return ContentService
    .createTextOutput(JSON.stringify({ tags: tags }))
    .setMimeType(ContentService.MimeType.JSON);
}
