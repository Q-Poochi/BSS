# Add project specific ProGuard rules here.
# Bật minify khi build release thì giữ lại class dùng cho GATT callback.
-keep class com.bss.companion.ble.** { *; }
-dontwarn org.jetbrains.annotations.**
