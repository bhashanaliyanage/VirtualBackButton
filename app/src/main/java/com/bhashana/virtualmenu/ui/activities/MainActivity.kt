package com.bhashana.virtualmenu.ui.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import com.bhashana.virtualmenu.BUTTON_TYPE_PREFS
import com.bhashana.virtualmenu.KEY_TRIGGER_MODE
import com.bhashana.virtualmenu.MenuContract
import com.bhashana.virtualmenu.R
import com.bhashana.virtualmenu.TriggerMode
import com.bhashana.virtualmenu.services.TriggerOverlayService
import com.bhashana.virtualmenu.ui.theme.VirtualBackTheme
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DynamicColors.applyToActivitiesIfAvailable(this.application)

        enableEdgeToEdge()

        // --- Debug log current prefs ---
        val prefs = getSharedPreferences(BUTTON_TYPE_PREFS, MODE_PRIVATE)
        val triggerMode = prefs.getString(KEY_TRIGGER_MODE, "accessibility")
        Log.d("MainActivity", "Current trigger mode preference = $triggerMode")

        setContent {
            VirtualBackTheme {
                // Use a Surface to set the app background to the theme surface color
                Surface(color = MaterialTheme.colorScheme.surface) {
                    // MainContent()
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

}

// --- local navigator just for the bottom area ---
sealed interface BottomPage {
    data object Cta : BottomPage
    data object Config : BottomPage
    data object OverlayPerm : BottomPage // page 1 of 2
}

@Composable
fun MainScreen() {
    // Replace `showConfig` with a small state machine for the BOTTOM area only
    var bottomPage by remember { mutableStateOf<BottomPage>(BottomPage.Cta) }

    // TODO: wire this to your TriggerModeSelector selection
    var selectedButtonTypeLabel by remember { mutableStateOf("Accessibility") }

    Box(Modifier.fillMaxSize()) {
        SoftGlowBackground(
            modifier = Modifier.fillMaxSize(),
            /*glowColor = Color.White.copy(alpha = 0.075f),*/
            centerBias = 0f to 0.4f,
            radiusFactor = 0.9f
        )
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // --- Top content (ALWAYS visible) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Button(
                        onClick = { /* TODO */ },
                        modifier = Modifier
                            .wrapContentWidth()
                            .align(Alignment.CenterEnd),
                        colors = buttonColors()
                    ) {
                        Text("Service Status: Unknown")
                    }
                }

                // --- Middle content (ALWAYS visible) ---
                if (bottomPage != BottomPage.OverlayPerm) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight(),
                            factory = { ctx ->
                                // Create container
                                FrameLayout(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )

                                    // Inflate the menu
                                    val menu = LayoutInflater.from(ctx).inflate(
                                        R.layout.floating_menu,
                                        this,
                                        true
                                    )

                                    // Apply the MaterialShapeDrawable background to the root view
                                    val shapeDrawable = MaterialShapeDrawable().apply {
                                        initializeElevationOverlay(ctx)
                                        setCornerSize(32f)
                                        fillColor = ColorStateList.valueOf(
                                            ColorUtils.setAlphaComponent(
                                                MaterialColors.getColor(
                                                    menu,
                                                    com.google.android.material.R.attr.colorSurface
                                                ),
                                                (0.9f * 255).toInt()
                                            )
                                        )
                                        elevation = ViewCompat.getElevation(menu)
                                    }

                                    // Assign background to the container (or to `menu` if you prefer)
                                    this.background = shapeDrawable

                                    tintIcon(
                                        menu,
                                        R.id.logo,
                                        com.google.android.material.R.attr.colorOnSurface
                                    )
                                }
                            }
                        )
                    }
                }

                // --- Bottom area (SWAPS content; no overlay) ---
                when (bottomPage) {
                    BottomPage.Cta -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Button(
                                onClick = { bottomPage = BottomPage.Config },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = buttonColors()
                            ) {
                                Box(
                                    modifier = Modifier.height(72.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Tap to Configure Button Type",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }

                    BottomPage.Config -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .wrapContentHeight()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ConfigurationLayout(
                                    onEnablePermissionsClick = {
                                        bottomPage = BottomPage.OverlayPerm
                                    }
                                )
                            }
                        }
                    }

                    BottomPage.OverlayPerm -> {
                        // This sits as the bottom section; top+middle remain visible
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            OverlayPermissionCard(
                                buttonTypeLabel = selectedButtonTypeLabel,
                                onBack = { bottomPage = BottomPage.Config }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tintIcon(view: View, iconId: Int, attr: Int) {
    val iv = view.findViewById<ImageView>(iconId)
    val color = MaterialColors.getColor(iv, attr)
    ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(color))
}


/**
 * Non-overlay "page 1 of 2" card that lives in the bottom area.
 * Rows:
 * 1) Back button + "Configure {button} Button"
 * 2) "1 of 2"
 * 3) "Screen Overlay Permission" + body text
 * 4) Note
 * 5) Primary CTA
 */
@Composable
private fun OverlayPermissionCard(
    buttonTypeLabel: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 20.dp, vertical = 38.dp)
    ) {
        // Row 1: Back + Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), // background
                    contentColor = if (MaterialTheme.colorScheme.onPrimary.luminance() > 0.5f) Color.Black else Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Configure $buttonTypeLabel Button",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 22.sp
            )
        }

        // Row 2: "1 of 2"
        Spacer(Modifier.height(48.dp))
        StepIndicator(currentStep = 1, totalSteps = 2)

        val bodyTypographyModifiers: Modifier = Modifier
            .padding(bottom = 12.dp, start = 64.dp, end = 64.dp)
            .align(alignment = Alignment.CenterHorizontally)

        // Row 3: Section title + body
        Spacer(Modifier.height(64.dp))
        Box(
            modifier = Modifier
                .padding(bottom = 12.dp, start = 32.dp, end = 32.dp)
                .align(alignment = Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Screen Overlay Permission",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 22.sp
            )
        }
        /*Spacer(Modifier.height(6.dp))*/
        Box(
            modifier = bodyTypographyModifiers
        ) {
            Text(
                text = "Axio needs permission to appear on top of other apps.",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }

        // Row 4: Note
        Spacer(Modifier.height(64.dp))
        Box(
            modifier = bodyTypographyModifiers
        ) {
            Text(
                text = "This allows the floating quick actions menu to stay visible while you use your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(64.dp))

        // Row 5: CTA
        Box(
            modifier = Modifier.align(alignment = Alignment.CenterHorizontally)
        ) {
            Button(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${ctx.packageName}".toUri()
                    )
                    ctx.startActivity(intent)
                },
                modifier = Modifier.wrapContentWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = buttonColors()
            ) {
                Text("Allow screen overlay permission")
            }
        }
    }
}

@Composable
fun StepIndicator(
    currentStep: Int,
    totalSteps: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        repeat(totalSteps) { index ->
            val stepNumber = index + 1
            val isActive = stepNumber == currentStep

            Surface(
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary
                else if (MaterialTheme.colorScheme.onPrimary.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stepNumber.toString(),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (stepNumber < totalSteps) {
                fun spacerWidth(): Int = 4

                Spacer(modifier = Modifier.width(spacerWidth().dp))
                HorizontalDivider(
                    modifier = Modifier
                        .width(48.dp)
                        .height(1.dp)
                        .align(Alignment.CenterVertically),
                    thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(spacerWidth().dp))
            }
        }
    }
}


@Composable
fun ConfigurationLayout(
    modifier: Modifier = Modifier,
    onEnablePermissionsClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(32.dp)
            )
            .wrapContentHeight()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(16.dp))
        // Title
        Text(
            text = "Choose button type",
            style = MaterialTheme.typography.titleLarge, fontSize = 28.sp,
            modifier = Modifier.padding(top = 24.dp, start = 24.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Trigger mode selector
        TriggerModeSelector(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp)
        )

        // Enable permissions button
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
        ) {
            Button(
                onClick = onEnablePermissionsClick,
                colors = buttonColors()
            ) {
                Text("Enable permissions")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Tip text
        Box(
            modifier = Modifier
                .padding(bottom = 12.dp, start = 32.dp, end = 32.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "*tip: we recommend using the accessibility button if it’s available on your device for faster, smoother and less battery usage".trimIndent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun buttonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
    contentColor = if (MaterialTheme.colorScheme.onPrimary.luminance() > 0.5f) Color.Black else Color.White
)

@Composable
fun TriggerModeSelector(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs =
        remember { context.getSharedPreferences(BUTTON_TYPE_PREFS, Context.MODE_PRIVATE) }

    // Read once when composed; default to Accessibility
    var selected by rememberSaveable {
        mutableStateOf(
            when (prefs.getString(KEY_TRIGGER_MODE, "accessibility")) {
                "overlay" -> TriggerMode.OVERLAY
                else -> TriggerMode.ACCESSIBILITY
            }
        )
    }

    // Persist whenever selection changes
    LaunchedEffect(selected) {
        val selection = if (selected == TriggerMode.OVERLAY) "overlay" else "accessibility"
        prefs.edit {
            putString(
                KEY_TRIGGER_MODE,
                selection
            )
        } // async, non-blocking
        Log.d("MainActivity", "Selection: $selection")
    }

    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy((-16).dp)
    ) {
        RadioRow(
            text = "Accessibility Button (Less battery usage)",
            selected = selected == TriggerMode.ACCESSIBILITY,
        ) { selected = TriggerMode.ACCESSIBILITY }

        RadioRow(
            text = "Floating overlay button (Regular)",
            selected = selected == TriggerMode.OVERLAY,
        ) { selected = TriggerMode.OVERLAY }
    }
}


@Composable
fun SoftGlowBackground(
    modifier: Modifier = Modifier,
    bg: Color = MaterialTheme.colorScheme.background,
    glowColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), // tweak intensity
    centerBias: Pair<Float, Float> = 0.7f to 0.3f,      // x,y as fraction of size
    radiusFactor: Float = 0.65f                          // fraction of min(size)
) {
    Box(
        modifier = modifier
            .background(bg)
            .drawWithCache {
                // compute once per size/color change
                val center = Offset(
                    size.width * centerBias.first,
                    size.height * centerBias.second
                )
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
private fun RadioRow(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text, modifier = Modifier.padding(start = 8.dp))
    }
}

// Helper to read current mode from prefs
private fun readTriggerMode(prefs: SharedPreferences): TriggerMode =
    when (prefs.getString(KEY_TRIGGER_MODE, "accessibility")) {
        "overlay" -> TriggerMode.OVERLAY
        else -> TriggerMode.ACCESSIBILITY
    }

@Composable
private fun MainContent() {
    val context = LocalContext.current
    val prefs =
        remember { context.getSharedPreferences(BUTTON_TYPE_PREFS, Context.MODE_PRIVATE) }

    // 1) Observe preference changes (incl. initial value)
    val triggerModeState = remember { mutableStateOf(readTriggerMode(prefs)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_TRIGGER_MODE) {
                triggerModeState.value = readTriggerMode(prefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // 2) React to preference: start/stop overlay service
    LaunchedEffect(triggerModeState.value) {
        when (triggerModeState.value) {
            TriggerMode.OVERLAY -> {
                if (Settings.canDrawOverlays(context)) {
                    val start = Intent(context, TriggerOverlayService::class.java)
                        .setAction(MenuContract.ACTION_START_OVERLAY)
                    ContextCompat.startForegroundService(context, start)
                    Log.d("MainContent", "Overlay service START requested")
                } else {
                    Toast.makeText(context, "Overlay permission required", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            TriggerMode.ACCESSIBILITY -> {
                // Tell the service to tear down and stop
                val stop = Intent(context, TriggerOverlayService::class.java)
                    .setAction(MenuContract.ACTION_STOP_OVERLAY)
                context.startService(stop) // safe: service will handle STOP action
                Log.d("MainContent", "Overlay service STOP requested")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                    Spacer(Modifier.height(20.dp))

                    TriggerModeSelector(
                        modifier = Modifier
                            .width(260.dp) // optional width to align with buttons
                    )

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

                    val overlaySelected = triggerModeState.value == TriggerMode.OVERLAY
                    Button(
                        onClick = {
                            if (!Settings.canDrawOverlays(context)) {
                                // send user to overlay permission screen
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    "package:${context.packageName}".toUri()
                                )
                                context.startActivity(intent)
                                return@Button
                            }
                            val start = Intent(context, TriggerOverlayService::class.java)
                                .setAction(MenuContract.ACTION_START_OVERLAY)

                            // Start (or re-start) foreground overlay
                            ContextCompat.startForegroundService(context, start)

                            Toast.makeText(
                                context,
                                "Floating button enabled",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        },
                        enabled = overlaySelected, // disabled if Accessibility mode
                        modifier = Modifier.width(220.dp)
                    ) {
                        Text("Enable Floating Button", textAlign = TextAlign.Center)
                    }
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
            ConfigurationLayout { }
        }
    }
}

@Preview(
    name = "Dark Mode",
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun GreetingPreviewDark() {
    VirtualBackTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ConfigurationLayout { }
        }
    }
}