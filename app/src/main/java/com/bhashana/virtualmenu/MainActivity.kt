package com.bhashana.virtualmenu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bhashana.virtualmenu.ui.theme.VirtualBackTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this.application)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        enableEdgeToEdge()
        setContent {
            VirtualBackTheme {
                // Use a Surface to set the app background to the theme surface color
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.surface,          // explicit
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Greeting()

                                Spacer(Modifier.height(16.dp))

                                val context = LocalContext.current

                                // Primary button picks up dynamic color automatically
                                Button(onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Cannot open accessibility settings",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }) {
                                    Text("Open Accessibility Settings")
                                }

                                Spacer(Modifier.height(16.dp))

                                // Use an outlined style to vary emphasis
                                OutlinedButton(onClick = {
                                    if (!Settings.canDrawOverlays(context)) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            "package:${context.packageName}".toUri()
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Overlay permission already granted",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }) {
                                    Text("Grant Overlay Permission")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

}

@Composable
fun Greeting(modifier: Modifier = Modifier) {
    Text(
        text = """
            |1. Enable Accessibility
            |2. Enable Accessibility Button
            |3. Grant Overlay Permission
            |4. Enjoy! Nothing else to do here.
        """.trimMargin(),
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VirtualBackTheme {
        Greeting()
    }
}