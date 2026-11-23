package meow.nyx.t3up

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            MaterialExpressiveTheme(
                colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx),
                motionScheme = MotionScheme.expressive(),
            ) { Screen() }
        }
    }
}

private sealed interface Latest {
    data object Loading : Latest
    data class Found(val r: Updater.Release) : Latest
    data class Error(val msg: String) : Latest
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Screen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(Updater.installed(ctx)) }
    var latest by remember { mutableStateOf<Latest>(Latest.Loading) }
    var status by remember { mutableStateOf(UpdateJob.prefs(ctx).getString("last", null) ?: "No runs yet") }
    var auto by remember { mutableStateOf(UpdateJob.isScheduled(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }

    fun check() = scope.launch {
        installed = Updater.installed(ctx)
        latest = Latest.Loading
        latest = withContext(Dispatchers.IO) {
            try {
                Updater.latest(ctx)?.let { Latest.Found(it) } ?: Latest.Error("No APK in recent releases")
            } catch (e: Exception) {
                Latest.Error(e.message ?: "Network error")
            }
        }
    }

    fun update() = scope.launch {
        busy = true
        status = "Checking..."
        val result = withContext(Dispatchers.IO) {
            try {
                Updater.update(ctx) { pct -> progress = pct / 100f }
            } catch (e: Exception) {
                "Failed: ${e.message}"
            }
        }
        UpdateJob.saveStatus(ctx, result)
        status = UpdateJob.prefs(ctx).getString("last", result)!!
        progress = null
        busy = false
        installed = Updater.installed(ctx)
    }

    LaunchedEffect(Unit) {
        if (auto) UpdateJob.setEnabled(ctx, true) // re-register so interval changes apply after an app update
        check()
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("T3 Updater") },
                subtitle = { Text("Nightly channel, pingdotgg/t3code") },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val found = (latest as? Latest.Found)?.r
            val behind = found != null && (installed?.versionCode ?: 0) < found.versionCode

            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (behind) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        when {
                            found == null -> "Checking GitHub"
                            behind -> "Update available"
                            else -> "Up to date"
                        },
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        VersionBlock("Installed", installed?.versionCode?.toString() ?: "none", installed?.versionName ?: "not installed", Modifier.weight(1f))
                        AnimatedContent(latest, label = "latest", modifier = Modifier.weight(1f)) { l ->
                            when (l) {
                                Latest.Loading -> Row(Modifier.height(96.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LoadingIndicator(Modifier.size(56.dp))
                                }
                                is Latest.Found -> VersionBlock("Latest", l.r.versionCode.toString(), l.r.versionName)
                                is Latest.Error -> VersionBlock("Latest", "?", l.msg)
                            }
                        }
                    }
                    found?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it.tag, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            AnimatedVisibility(progress != null) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    LinearWavyProgressIndicator(progress = { progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("Downloading ${((progress ?: 0f) * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { check() },
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).height(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) { Text("Check", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
                Button(
                    onClick = { update() },
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1.4f).height(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) { Text(if (busy) "Working" else "Update now", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
            }

            Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ListItem(
                    headlineContent = { Text("Auto-update") },
                    supportingContent = { Text("Every hour on Wi-Fi, installs with root") },
                    trailingContent = {
                        Switch(checked = auto, onCheckedChange = { auto = it; UpdateJob.setEnabled(ctx, it) })
                    },
                    colors = itemColors,
                )
                ListItem(
                    headlineContent = { Text("Last run") },
                    supportingContent = { Text(status) },
                    colors = itemColors,
                )
            }
            var token by remember { mutableStateOf(UpdateJob.prefs(ctx).getString("token", "")!!) }
            OutlinedTextField(
                value = token,
                onValueChange = {
                    token = it
                    UpdateJob.prefs(ctx).edit().putString("token", it.trim()).apply()
                },
                label = { Text("GitHub token (optional)") },
                supportingText = { Text("Fine-grained PAT with public read access. Stored on this phone only.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VersionBlock(label: String, code: String, name: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(code, style = MaterialTheme.typography.displayLarge)
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}
