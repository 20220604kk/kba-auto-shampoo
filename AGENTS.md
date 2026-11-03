# 開發接手

先閱讀 `docs/HANDOFF.md`、`docs/V0.4.2a_VERIFICATION.md` 和 `README.md`。

- `main`／`v0.4.2a` 是已發行 APK 的原始碼基準；`development/v0.4.3` 保存後續尚未發行的原始碼。
- 保留 `tw.linedraw.quick` applicationId；不要只為交接修改版本碼。
- 正式發版必須使用比已發行版高的 versionCode，並另行確認原簽章憑證及 KBA 的版本範圍。
- 不提交私鑰、keystore、Discord Token、有效 KBA、裝置資料或建置快取。
- 不新增自動兌換／使用優惠券的操作。
- 檢查：Gradle `testDebugUnitTest lintDebug assembleDebug`，以及 `scripts/test-rules.ps1`。後者實際執行 14 組 `main()` 規則自測，不能只以 Gradle 測試任務成功代替。
- APP 介面／LINE 操作的變更仍需模擬器或實機驗收；編譯通過不能代表抽選流程通過。
