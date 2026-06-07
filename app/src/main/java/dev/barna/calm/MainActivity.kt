package dev.barna.calm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private lateinit var runner: CalmLauncherRunner
    private val launcherStateViewModel: LauncherStateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runner = CalmLauncherRunner(this, launcherStateViewModel)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        runner.onRequestPermissionsResult(requestCode)
    }
}
