package hu.grafit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import hu.grafit.ui.GrafitViewModel
import hu.grafit.ui.GrafitApp

class MainActivity : ComponentActivity() {
    private val model: GrafitViewModel by viewModels()
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("tool")) recreate()
    }
    override fun onResume() {
        super.onResume()
        hu.grafit.notifications.SchoolNotifications.schedule(this)
        hu.grafit.notifications.LessonLiveNotification.requestUpdate(this)
        model.resumeLocalData()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GrafitApp(model) }
    }
}
