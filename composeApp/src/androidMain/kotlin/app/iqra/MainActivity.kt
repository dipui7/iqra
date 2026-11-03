package app.iqra

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private var micResult: ((Boolean) -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micResult?.invoke(granted)
        micResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContext = applicationContext
        requestMicPermission = { onResult ->
            micResult = onResult
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        enableEdgeToEdge()
        setContent { App() }
    }

    override fun onStop() {
        super.onStop()
        stopAudio()
        stopRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        requestMicPermission = null
    }
}
