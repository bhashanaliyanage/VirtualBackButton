package com.bhashana.virtualback

import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bhashana.virtualback.ui.theme.VirtualBackTheme

class MainActivity : ComponentActivity() {

    private var lastKeyboardVisible = false
    private var lastKeyboardHeight = 0

    companion object {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - r.bottom

            val isKeyboardVisible = keypadHeight > screenHeight * 0.15
            val intent = Intent("FLOATING_BUTTON_KEYBOARD_VISIBILITY")
            intent.setPackage(packageName)
            intent.putExtra("keyboardVisible", isKeyboardVisible)
            sendBroadcast(intent)

            Log.d("MainActivity", "Screen height: $screenHeight")
            Log.d("MainActivity", "Keypad height: $keypadHeight")
            Log.d("MainActivity", "Keyboard Visible: $isKeyboardVisible")
        }

        if (!isAccessibilityServiceEnabled()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Please enable 'Virtual Back Button' Accessibility Service",
                Toast.LENGTH_LONG
            ).show()
        }

        KeyboardVisibilityDetector(this) { isKeyboardVisible ->
            Log.d("MainActivity.onCreate", "Keyboard visible: $isKeyboardVisible")

            // Save the latest state
            lastKeyboardVisible = isKeyboardVisible
            lastKeyboardHeight = calculateKeyboardHeight()

            val intent = Intent("FLOATING_BUTTON_KEYBOARD_VISIBILITY")
            intent.setPackage(packageName)
            intent.putExtra("keyboardVisible", isKeyboardVisible)
            intent.putExtra("keyboardHeight", lastKeyboardHeight)
            sendBroadcast(intent)
        }.start()

        enableEdgeToEdge()
        setContent {
            VirtualBackTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Re-send latest keyboard visibility status after service is likely ready
        if (isAccessibilityServiceEnabled()) {
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent("FLOATING_BUTTON_KEYBOARD_VISIBILITY")
                intent.setPackage(packageName)
                intent.putExtra("keyboardVisible", lastKeyboardVisible)
                intent.putExtra("keyboardHeight", lastKeyboardHeight)
                sendBroadcast(intent)
                Log.d("MainActivity", "Rebroadcasting keyboard state: $lastKeyboardVisible, $lastKeyboardHeight")
            }, 1000)
        }
    }

    private fun calculateKeyboardHeight(): Int {
        val rootView = findViewById<View>(android.R.id.content)
        val r = Rect()
        rootView.getWindowVisibleDisplayFrame(r)
        val screenHeight = rootView.rootView.height
        return screenHeight - r.bottom
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, FloatingBackService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices
            .split(":")
            .map { ComponentName.unflattenFromString(it) }
            .any { it == expectedComponent }
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VirtualBackTheme {
        Greeting("Android")
    }
}