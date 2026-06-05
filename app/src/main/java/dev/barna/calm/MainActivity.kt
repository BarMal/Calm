package dev.barna.calm

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {
    private lateinit var runner: CalmLauncherRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runner = CalmLauncherRunner(this)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        runner.onRequestPermissionsResult(requestCode)
    }
}
