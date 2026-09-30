package com.bss.companion

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.bss.companion.ble.BssBleManager
import com.bss.companion.ui.BssApp
import com.bss.companion.ui.BssViewModel

/**
 * Activity duy nhất của app (single-activity + Compose).
 *
 * Nhiệm vụ duy nhất: xin quyền BLE rồi giao toàn bộ phần còn lại cho Compose + ViewModel.
 * Không chứa logic BLE hay logic hiển thị - đúng tinh thần MVVM.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: BssViewModel by viewModels()

    /** Hộp thoại xin quyền BLE; kết quả trả về là map<tên quyền, đã cấp hay chưa>. */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        viewModel.updatePermissionState(grants.values.all { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasBlePermissions()) {
            viewModel.updatePermissionState(true)
        } else {
            requestBlePermissions()
        }

        setContent {
            BssApp(
                viewModel = viewModel,
                onRequestPermission = ::requestBlePermissions,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Người dùng có thể bật/tắt quyền trong Settings -> đồng bộ lại mỗi lần quay về app.
        viewModel.updatePermissionState(hasBlePermissions())
    }

    private fun requestBlePermissions() {
        // Danh sách này đã tự chọn đúng theo phiên bản Android:
        //  - API 31+: BLUETOOTH_SCAN + BLUETOOTH_CONNECT
        //  - API ≤30: ACCESS_FINE_LOCATION (bắt buộc để quét được BLE)
        permissionLauncher.launch(BssBleManager.REQUIRED_PERMISSIONS)
    }

    private fun hasBlePermissions(): Boolean = BssBleManager.REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}
