# 家用端開發交接

更新日期：2026-11-03（台灣時間）。目標是僅靠 GitHub 原始碼與文件接續開發，不依賴公司電腦的 Codex 對話。

## 先選基準

| 分支／標籤 | 狀態 | 用途 |
|---|---|---|
| `main`、`v0.4.2a` | 已發行 APK 對應版本，versionCode 22 | 重現目前發行版、排查既有問題 |
| `development/v0.4.3` | 2026-10-09 留存的後續原始碼，versionCode 23，未正式發行 | 接續既有修正與線上更新開發 |
| `v0.2.9` | 原有歷史開源版 | 歷史參考 |

家用端可直接貼給 Codex：

> 請 clone https://github.com/20220604kk/kba-auto-shampoo，先讀 AGENTS.md、docs/HANDOFF.md、docs/V0.4.2a_VERIFICATION.md。正式發行基準為 main 的 v0.4.2a；我要接續尚未發行的 v0.4.3，請切到 development/v0.4.3，先執行規則自測和 Gradle 檢查，再核對下方三項用戶回報與更新器。不要直接發布 APK。

```powershell
git clone https://github.com/20220604kk/kba-auto-shampoo.git
cd kba-auto-shampoo
git switch development/v0.4.3
```

家用端已有 clone 時，先確認工作目錄無未保存修改，再 `git fetch origin --tags`；不要直接覆蓋本機修改。

## v0.4.2a 實作範圍

這是原生 Java／Android Views 專案，沒有 Kotlin、Compose 或獨立前端。大部分 UI 集中在 `MainActivity`；資料存於 SharedPreferences，`DrawData` 同步公開清單並保存使用者狀態。`DrawAccessibilityService` 負責任務、LINE 操作與懸浮視窗；`VisualCouponReader` 使用中文 OCR 作輔助。

已具備篩選／排序、三組喜好方案、開抽健檢、續跑、速度模式、同步差異摘要、結果分類與離線模擬。v0.4.2a 的主要新增是 KBA2 讀取，仍支援舊 JSON KBA。

正式同步和正式抽選均檢查 KBA；測試模式免 KBA。KBA2 解碼後仍驗證 ECDSA、日期及 minVersionCode／maxVersionCode。`res/raw/license_public.pem` 是可公開的驗證公鑰。

## v0.4.3 已留下的修改

以下是從本機原始碼和原對話確認的進度，尚未正式發行：

1. `DrawData` 狀態 revision、切分頁／狀態變動重新讀取資料。
2. 篩選不清掉畫面外選取，避免補抽時選取資料遺失。
3. 404／找不到頁面辨識，列為失效連結、需確認。
4. 任務完成卡：歡呼貓、處理筆數、實際耗時；圓角掃描報告。
5. `AppUpdateManager`：每 12 小時及手動檢查 GitHub 最新 Release，透過 DownloadManager 下載，再驗證套件名稱、較高版本碼及相同簽章，交由 Android 安裝。

v0.4.3 當時有 debug APK 與編譯紀錄，沒有正式簽章發行 APK。本次保留原實作，不視為已完成裝置驗收，也沒有發布 v0.4.3 Release。

## 必須再驗證的用戶回報

- 選定縣市後操作尚未開抽的店家，回任務頁漏顯示；只在全部縣市看得到，無法依原篩選補抽。
- 部分抽選後中止掃描，即使全部商品／地區仍只顯示少數門市，曾需重開 APP／重新授權。
- 清單偶爾需切到另一頁再切回才出現。

以相同資料逐一重現，確認 v0.4.3 的刷新與選取修正有效。再驗證 404、完成卡、報告版面及更新器的成功／下載失敗／錯簽章／舊版本流程。至少涵蓋 Android 9 與近期 Android、MuMu 或 BlueStacks；這些是待辦，不是已通過結果。

v0.4.3 更新器目前選取 Release 的第一個 `.apk` 資產；發行時應只放一份對應 APK，並確認發布流程與版本比較。上線前仍需完整下載及安裝驗收。

## 建置與測試

安裝 JDK 17、SDK Platform 35、Build Tools 35.0.0，設定 `JAVA_HOME`、`ANDROID_HOME`。執行：

```powershell
.\scripts\test-rules.ps1
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

14 組自測涵蓋按鈕安全、頁面訊號、時間篩選、手動貼文解析、模擬資料、品項、排序、結果規則、任務範圍、店家群組、同步差異、測試清單、直接交接與等待策略。沒有裝置操作；KBA 實際匯入、更新器及 LINE 互動需另外驗收。

GitHub Actions 執行規則自測和 Gradle 檢查。Release 建置未內嵌 signingConfig，輸出 unsigned APK；不要把它當成可發送給群友的包。

## 公司端另外保管的內容

- APK 原簽章 keystore：未放 GitHub。signer 識別見版本驗證文件；家用端若要覆蓋發行，需透過私密管道移交並核對指紋。
- KBA 發行私鑰與「頻道小精靈」發卡後台：獨立專案，未包含於此倉庫。不能靠公鑰產生有效卡，也不能把 Discord Token 或 `.env` 貼入公開庫。
- 原紀錄顯示 2026-10-07 已切換後台為 KBA2；這是歷史狀態，本次未操作或驗證目前後台／Discord 排程。
- 舊對話、截圖與快取不需帶到家用端；開發所需版本、架構與下一步已整理於本文件。

重新發布前需確認 KBA 版本範圍是否接受新 versionCode。簽章更換會影響原用戶覆蓋更新；不要直接用家用端新生成的 debug key 發給原用戶。
