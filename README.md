# RobotDogRelay (Android Studio 專案)

## 使用說明
1. 解壓縮後使用 Android Studio 開啟 `RobotDogRelay` 資料夾，等待 Gradle 同步完成。
2. 將手機安裝並開啟 App，輸入樹莓派（Freenove Robot Dog）的區網 IP 後點擊 **「啟動服務」**。
3. 本機畫面下方的 WebView 會自動載入遙控器介面；同區網的其他電腦或平板也可透過瀏覽器開啟 `http://<手機IP>:8080` 進行遠端控制。
4. 若影像畫面無法顯示，可將 `RobotRelayServer.kt` 中的 `ByteOrder.LITTLE_ENDIAN` 調整為 `ByteOrder.BIG_ENDIAN` 後重新編譯。
