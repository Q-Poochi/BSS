// Build script gốc (root). Không khai báo dependency ở đây,
// chỉ khai báo plugin dùng chung cho các module con.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
