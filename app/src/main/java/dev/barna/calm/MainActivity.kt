package dev.barna.calm

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private lateinit var runner: CalmLauncherRunner
    private val launcherStateViewModel: LauncherStateViewModel by viewModels()
    private val calendarPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (::runner.isInitialized) runner.onCalendarPermissionResult()
    }
    private val contactsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (::runner.isInitialized) runner.onContactsPermissionResult()
    }
    private val widgetBindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (::runner.isInitialized) runner.onWidgetBindResult(result.resultCode, result.data)
    }
    private val widgetConfigureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (::runner.isInitialized) runner.onWidgetConfigureResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runner = CalmLauncherRunner(
            activity = this,
            launcherStateViewModel = launcherStateViewModel,
            requestCalendarPermission = { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
            requestContactsPermission = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
            requestWidgetBind = { intent: Intent -> widgetBindLauncher.launch(intent) },
            requestWidgetConfigure = { intent: Intent -> widgetConfigureLauncher.launch(intent) },
        )
        runner.restoreInstanceState(savedInstanceState)
        // Back dismisses the expanded card (returning to the current page); otherwise it is a no-op,
        // as expected for a home launcher, instead of finishing and reopening on the overview page.
        onBackPressedDispatcher.addCallback(this) { runner.onBackPressed() }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::runner.isInitialized) {
            runner.onSaveInstanceState(outState)
        }
    }
}
