package com.bhashana.virtualmenu

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.bhashana.virtualmenu.ui.theme.VirtualBackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
private fun WallpaperBackground(
    modifier: Modifier = Modifier,
    dim: Float = 0.35f,         // overlay opacity to ensure contrast
    blurRadius: Dp = 12.dp      // set to 0.dp to disable blur
) {
    val context = LocalContext.current
    var image by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val wm = WallpaperManager.getInstance(context)
            val drawable =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    wm.getDrawable(WallpaperManager.FLAG_SYSTEM)
                } else {
                    wm.drawable
                }
            val bmp = (drawable as? BitmapDrawable)?.bitmap ?: return@withContext

            // Downscale to avoid huge bitmaps
            val targetW = 1280
            val scaled = if (bmp.width > targetW) {
                val scale = targetW.toFloat() / bmp.width
                bmp.scale(targetW, (bmp.height * scale).toInt())
            } else bmp

            image = scaled.asImageBitmap()
        }
    }

    Box(modifier.fillMaxSize()) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius)        // comment this out if you don't want blur
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim))
        )
    }
}

@Composable
private fun MainContent() {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
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