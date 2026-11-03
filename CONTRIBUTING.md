# 參與貢獻

歡迎透過 Issue 回報可重現的問題，或提出 Pull Request。

提交前請確認：

1. 不包含 LINE 帳號資料、Discord Token、私鑰、簽章檔或其他個人資訊。
2. 不加入自動點擊「使用優惠券」或其他不可逆操作。
3. 執行 `gradlew.bat testDebugUnitTest lintDebug assembleDebug` 及 `scripts/test-rules.ps1` 並確認成功。
4. 若修改畫面辨識文字，請描述測試的 Android／LINE 版本與畫面狀態。

`main` 以 v0.4.2a 為目前發行基準；後續 v0.4.3 原始碼位於 `development/v0.4.3`。修改前先閱讀 `docs/HANDOFF.md`，不要把開發中版本當成已驗收的發行版。
