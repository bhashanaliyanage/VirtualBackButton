package com.bhashana.virtualmenu

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bhashana.virtualmenu.ui.theme.VirtualBackTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this.application)

        enableEdgeToEdge()
        setContent {
            VirtualBackTheme {
                // Use a Surface to set the app background to the theme surface color
                Surface(color = MaterialTheme.colorScheme.surface) {
                    MainContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

}

@Composable
fun SoftGlowBackground(
    modifier: Modifier = Modifier,
    bg: Color = Color(0xFF222222), // #222222
    glowColor: Color = Color.White.copy(alpha = 0.12f), // tweak intensity
    centerBias: Pair<Float, Float> = 0.7f to 0.3f,      // x,y as fraction of size
    radiusFactor: Float = 0.65f                          // fraction of min(size)
) {
    Box(
        modifier = modifier
            .background(bg)
            .drawWithCache {
                // compute once per size/color change
                val center = Offset(size.width * centerBias.first,
                    size.height * centerBias.second)
                val radius = size.minDimension * radiusFactor
                val brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = radius
                )
                onDrawBehind {
                    drawRect(bg)
                    drawCircle(brush = brush, radius = radius, center = center)
                }
            }
    )
}

@Composable
private fun MainContent() {
    SoftGlowBackground(
        modifier = Modifier.fillMaxSize(),
        glowColor = Color.White.copy(alpha = 0.075f),  // subtler
        centerBias = 0f to 0.4f,                  // move highlight
        radiusFactor = 0.9f
    )
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
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
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "Cannot open accessibility settings",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }, Modifier.width(200.dp)
                ) {
                    Text(
                        "Open Accessibility Settings",
                        textAlign = TextAlign.Center
                    )
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
                }, Modifier.width(200.dp)) {
                    Text(
                        "Grant Overlay Permission",
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
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

@Preview(showSystemUi = true)
@Composable
fun GreetingPreview() {
    VirtualBackTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            MainContent()
        }
    }
}

@Preview(
    name = "Dark Mode",
    showSystemUi = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun GreetingPreviewDark() {
    VirtualBackTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            MainContent()
        }
    }
}