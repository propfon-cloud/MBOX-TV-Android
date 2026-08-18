MBOX TV V3 - Android TV Browser / iframe
Version 1.2.0 (versionCode 4)

WHAT IS NEW
- Iframe mode is ON by default.
- The iframe shell is loaded with the TV server origin using loadDataWithBaseURL().
- PHP cookies/session are preserved in WebView.
- The shell tries to hide the legacy Play button and start HTML5 video automatically.
- Direct WebView mode remains available by unchecking "iframe режим".
- Hold BACK for 3 seconds -> PIN -> control menu:
  * Settings
  * Restart player
  * Exit to Android
- Exit to Android stops/destroys WebView, closes the app activities and kills the MBOX TV process.
- A manual-exit guard prevents an immediate return to MBOX TV if it had been selected as Home/Launcher.
- The manual-exit guard is cleared after a real TV Box reboot/update.

RECOMMENDED TV URL
http://192.168.1.117/tv/

IMPORTANT
On Android TV, localhost/127.0.0.1 means the Android box itself. Use the LAN IP of the TV server.

BUILD
The existing GitHub Actions workflow builds the direct debug APK after these files are uploaded/committed.
