package com.example.ui.renderer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.LiveReloadGreen
import com.example.ui.theme.MeteoroBorder
import com.example.ui.theme.MeteoroCard
import com.example.ui.theme.MeteoroBackground
import com.example.ui.theme.MeteoroOrange
import com.example.ui.theme.MeteoroSurface
import com.example.ui.theme.MeteoroSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RendererScreen(
    targetUrl: String,
    onNavigateBack: () -> Unit,
    viewModel: RendererViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val inputValues by viewModel.inputValues.collectAsState()
    val interactiveCounter by viewModel.interactiveCounter.collectAsState()
    val isLiveReloading by viewModel.isLiveReloading.collectAsState()

    var activeAlertDialogMessage by remember { mutableStateOf<String?>(null) }
    var showRawJsonDialog by remember { mutableStateOf(false) }
    val routeStack = remember(targetUrl) { mutableStateListOf<String>() }

    fun navigateBack(): Boolean {
        if (routeStack.isNotEmpty()) {
            routeStack.removeAt(routeStack.lastIndex)
            return true
        }
        return false
    }

    BackHandler {
        if (!navigateBack()) onNavigateBack()
    }

    LaunchedEffect(targetUrl) {
        viewModel.loadApp(targetUrl)
    }

    // Infinite animation for the live reload beacon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )

    fun handleAction(actionStr: String, availableRoutes: List<com.example.model.MeteoroRoute>) {
        when {
            actionStr.startsWith("navigate:", ignoreCase = true) -> {
                val routeId = actionStr.substringAfter(':').trim()
                if (availableRoutes.any { it.id == routeId }) {
                    routeStack.add(routeId)
                } else {
                    Toast.makeText(context, "No existe la pantalla '$routeId'.", Toast.LENGTH_SHORT).show()
                }
            }
            actionStr.startsWith("alert:", ignoreCase = true) -> {
                activeAlertDialogMessage = actionStr.removePrefix("alert:")
            }
            actionStr.startsWith("toast:", ignoreCase = true) -> {
                Toast.makeText(context, actionStr.removePrefix("toast:"), Toast.LENGTH_SHORT).show()
            }
            actionStr.startsWith("open_url:", ignoreCase = true) -> {
                val url = actionStr.removePrefix("open_url:").trim()
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(browserIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No se pudo abrir URL: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            actionStr.equals("counter:inc", ignoreCase = true) -> {
                viewModel.incrementCounter()
            }
            actionStr.equals("counter:dec", ignoreCase = true) -> {
                viewModel.decrementCounter()
            }
            actionStr.equals("submit", ignoreCase = true) -> {
                val summary = if (inputValues.isEmpty()) {
                    "No se introdujeron datos en los campos de entrada."
                } else {
                    inputValues.entries.joinToString("\n") { "• ${it.key}: ${it.value}" }
                }
                activeAlertDialogMessage = "Datos de formulario enviados:\n\n$summary"
            }
            else -> {
                Toast.makeText(context, actionStr, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val titleText = when (val state = uiState) {
                            is RendererUiState.Success -> {
                                val routeId = routeStack.lastOrNull() ?: state.app.initialRoute
                                state.app.routes.orEmpty().firstOrNull { it.id == routeId }?.title
                                    ?: state.app.appName
                                    ?: "Meteoro App"
                            }
                            else -> "Meteoro Live"
                        }
                        Text(
                            text = titleText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        // Live reload badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.toggleLiveReload() }
                                .padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .alpha(if (isLiveReloading) beaconAlpha else 0.4f)
                                    .background(
                                        if (isLiveReloading) LiveReloadGreen else TextMuted,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLiveReloading) "Live Reload (2s)" else "Pausado",
                                fontSize = 11.sp,
                                color = if (isLiveReloading) LiveReloadGreen else TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (!navigateBack()) onNavigateBack() },
                        modifier = Modifier.testTag("renderer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshManually() },
                        modifier = Modifier.testTag("renderer_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recargar código",
                            tint = MeteoroOrange
                        )
                    }
                    IconButton(
                        onClick = { showRawJsonDialog = true },
                        modifier = Modifier.testTag("renderer_code_inspector")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Ver JSON",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeteoroSurface,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            val successState = uiState as? RendererUiState.Success
            val navigationItems = successState?.app?.navigationBar.orEmpty()
            if (navigationItems.isNotEmpty()) {
                val initialRoute = successState?.app?.initialRoute
                val activeRoute = routeStack.lastOrNull() ?: initialRoute
                NavigationBar(containerColor = MeteoroSurface) {
                    navigationItems.forEach { item ->
                        val icon = when (item.route.lowercase()) {
                            "inicio", "home" -> Icons.Default.Home
                            "perfil", "profile" -> Icons.Default.Person
                            "ajustes", "settings" -> Icons.Default.Settings
                            else -> Icons.Default.Circle
                        }
                        NavigationBarItem(
                            selected = activeRoute == item.route,
                            onClick = {
                                routeStack.clear()
                                if (item.route != initialRoute) {
                                    routeStack.add(item.route)
                                }
                            },
                            icon = {
                                Icon(imageVector = icon, contentDescription = item.label)
                            },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag("navigation_tab_${item.route}")
                        )
                    }
                }
            }
        },
        containerColor = MeteoroBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is RendererUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MeteoroOrange,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Obteniendo esquema nativo...",
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = targetUrl,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                    }
                }

                is RendererUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MeteoroOrange,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No se pudo cargar la App",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.refreshManually() },
                            colors = ButtonDefaults.buttonColors(containerColor = MeteoroOrange),
                            modifier = Modifier.fillMaxWidth().testTag("retry_button")
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reintentar Conexión")
                        }

                        if (state.canLoadDemo) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { viewModel.loadFallbackDemo("coffee") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MeteoroOrange),
                                modifier = Modifier.fillMaxWidth().testTag("load_demo_button")
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Probar con Esquema Demo (Café Meteoro)")
                            }
                        }
                    }
                }

                is RendererUiState.Success -> {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp)
                    ) {
                        // Live reload info chip
                        val timeStr = remember(state.lastUpdated) {
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(state.lastUpdated))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MeteoroCard, RoundedCornerShape(8.dp))
                                .border(1.dp, MeteoroBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = MeteoroOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "v${state.app.version ?: "1.0"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = "Última sincronización: $timeStr",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val activeRouteId = routeStack.lastOrNull() ?: state.app.initialRoute
                        val activeRoute = state.app.routes.orEmpty().firstOrNull { it.id == activeRouteId }
                        val components = activeRoute?.components ?: state.app.screens.orEmpty()
                        if (components.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "El esquema no contiene componentes visuales en 'screens'.",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            MeteoroScreenRenderer(
                                components = components,
                                inputValues = inputValues,
                                interactiveCounter = interactiveCounter,
                                onAction = { handleAction(it, state.app.routes.orEmpty()) },
                                onInputChange = { id, text -> viewModel.setInputValue(id, text) }
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    // Alert Dialog for component action
    if (activeAlertDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { activeAlertDialogMessage = null },
            title = {
                Text(
                    text = "Meteoro Native Alert",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = activeAlertDialogMessage.orEmpty(),
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { activeAlertDialogMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MeteoroOrange)
                ) {
                    Text("Aceptar")
                }
            },
            containerColor = MeteoroSurface
        )
    }

    // Raw JSON Schema Inspector
    if (showRawJsonDialog) {
        val rawJsonText = when (val s = uiState) {
            is RendererUiState.Success -> s.rawJson
            else -> "Cargando esquema..."
        }

        AlertDialog(
            onDismissRequest = { showRawJsonDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = MeteoroOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Esquema JSON Recibido", color = TextPrimary, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(MeteoroBackground, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = rawJsonText,
                        color = LiveReloadGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showRawJsonDialog = false }) {
                    Text("Cerrar", color = MeteoroOrange)
                }
            },
            containerColor = MeteoroSurface
        )
    }
}
