package dev.barna.calm

import android.Manifest
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private lateinit var runner: CalmLauncherRunner
    private val launcherStateViewModel: LauncherStateViewModel by viewModels()
    private val calendarPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (::runner.isInitialized) runner.onCalendarPermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runner = CalmLauncherRunner(
            activity = this,
            launcherStateViewModel = launcherStateViewModel,
            requestCalendarPermission = { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
        )
        runner.onCreate()
    }

    override fun onResume() {
        super.onResume()
        runner.onResume()
    }

    override fun onPause() {
        runner.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        runner.onDestroy()
        super.onDestroy()
    }
}
