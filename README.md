# K爸全自動洗頭機

> 本分支是 **v0.4.3（versionCode 23）的未發行開發進度**。已發行 v0.4.2a 原始碼請使用 `main` 或 `v0.4.2a` 標籤；接手請先讀 [交接文件](docs/HANDOFF.md)。

協助整理每週 LINE 抽選任務的 Android 工具：同步公開清單、依縣市／品項／時間篩選、安排順序，透過輔助服務開啟 LINE 連結並記錄結果。

**目前發行基準：v0.4.2a（versionCode 22）。** `main` 已由歷史 v0.2.9 更新到指定 v0.4.2a APK 的對應原始碼；原本 v0.2.9 保留在既有標籤。

- [下載 v0.4.2a APK](https://github.com/20220604kk/kba-auto-shampoo/releases/tag/v0.4.2a)
- [家用端接手說明與待辦](docs/HANDOFF.md)
- [版本還原與驗證](docs/V0.4.2a_VERIFICATION.md)
- [更新紀錄](CHANGELOG.md)

尚未正式發行的 v0.4.3 原始碼保存在 `development/v0.4.3` 分支。請先讀交接文件，依目的選擇發行基準或後續開發進度。

## 已實作功能

- 公開抽選清單同步，以及新增、下架、時間變更和未變動摘要。
- 縣市、品項、開抽時間篩選，自訂縣市／品項優先順序。
- 最多三組具名喜好方案、任務選取、中斷後續跑提示。
- 開抽前權限健檢、懸浮進度、暫停／繼續／停止與 LINE 直式鎖定。
- 快速／平衡／相容速度模式、LINE 間直接交接與低階裝置模式。
- 中獎、未中、未抽、未開賣、未加好友、已結束與需確認等結果分類。
- 正式與測試紀錄分開保存、最近掃描報告、離線模擬測試。
- 舊版 JSON 簽章 KBA 與新版 KBA2 二進位卡；可從開啟或分享檔案直接匯入。

同步公開清單與正式抽選都需要有效 KBA；測試功能不需要。APP 僅包含驗證公鑰，發卡後台與私鑰不在此倉庫。v0.4.2a 尚未包含 APP 內線上更新器。

程式不自動點擊「使用優惠券」；兌換由使用者自行確認。

## 使用需求

- Android 9（API 28）以上，已安裝並登入 LINE。
- 自動流程需開啟輔助服務、顯示在其他 APP 上層及修改系統設定。
- 匯入有效 KBA 後，同步清單、篩選、選取任務，再開始抽選。建議先完成測試頁的離線模擬。

## 建置與檢查

需要 JDK 17、Android SDK Platform 35 及 Build Tools 35.0.0。設定 `JAVA_HOME` 和 `ANDROID_HOME`；也可透過 Android Studio 設定 SDK。倉庫附有 Gradle 8.13 Wrapper。

Windows PowerShell：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
.\scripts\test-rules.ps1
```

macOS／Linux：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
# 規則自測可使用 PowerShell 7：
pwsh ./scripts/test-rules.ps1
```

`testDebugUnitTest` 不會執行本專案以 `main()` 撰寫的自測；`test-rules.ps1` 才會實際執行全部 14 組規則測試，CI 也會執行。

輸出：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

Release 建置預設未簽章。自行建置的 debug APK 使用本機 debug 憑證，不保證能覆蓋已發行版；覆蓋更新必須沿用原簽章。原始 v0.4.2a 已簽章 APK 放在 GitHub Release，不提交到 Git 原始碼歷史。

## 程式結構

| 位置 | 職責 |
|---|---|
| `MainActivity.java` | 原生 Java 主介面、篩選、喜好方案、權限與 KBA 匯入 |
| `DrawData.java` / `DrawItem.java` | 公開清單解析、SharedPreferences、選取與結果 |
| `DrawAccessibilityService.java` | LINE 自動操作、佇列、懸浮控制與掃描 |
| `VisualCouponReader.java` | 畫面辨識及 ML Kit 中文 OCR |
| `CouponSafety.java` / `CouponPageSignals.java` | 可操作按鈕與頁面狀態規則 |
| `WeeklyLicense.java` / `res/raw/license_public.pem` | KBA／KBA2 解碼與 ECDSA、時間及版本驗證 |
| `SyncDiff.java` / `HandoffPolicy.java` / `WaitPolicy.java` | 同步摘要、直接交接與速度規則 |
| `assets/mock_coupon.html` / `MockCouponActivity.java` | 內建模擬頁與離線驗收 |
| `app/src/test/` | 14 組無裝置規則自測 |

## 隱私與授權

設定與結果存於裝置本機；同步來源為程式中標示的公開網站。倉庫不包含 Discord Token、KBA 私鑰、有效認證卡或 APK 簽章私鑰。輔助服務可讀取並操作畫面，請只安裝可信任的版本。

本工具與 LINE、Funbox 及清單來源作者沒有隸屬或代言關係。第三方頁面變動可能影響操作，請依各平台規範使用。

程式碼與自有素材採用 [MIT License](LICENSE)，第三方套件各依原授權。
