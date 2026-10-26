package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.main.MainScreen
import com.example.ui.renderer.RendererScreen
import com.example.ui.scanner.QrScannerScreen
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.MeteoroBackground
import com.example.ui.theme.MeteoroTheme
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private var initialDeepLinkUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleDeepLinkIntent(intent)
        if (BuildConfig.METEORO_STANDALONE && initialDeepLinkUrl == null) {
            initialDeepLinkUrl = BuildConfig.METEORO_DEV_SERVER_URL.ifBlank { "project://bundle" }
        }

        setContent {
            MeteoroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MeteoroBackground
                ) {
                    MeteoroAppNavigation(
                        deepLinkUrl = initialDeepLinkUrl,
                        standaloneMode = BuildConfig.METEORO_STANDALONE
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(incomingIntent: Intent?) {
        val dataUri = incomingIntent?.data
        if (dataUri != null) {
            initialDeepLinkUrl = dataUri.toString()
        }
    }
}

object MeteoroRoutes {
    const val SPLASH = "splash"
    const val MAIN = "main"
    const val SCANNER = "scanner"
    const val RENDERER = "renderer/{encodedUrl}"

    fun buildRendererRoute(rawUrl: String): String {
        val encoded = URLEncoder.encode(rawUrl, StandardCharsets.UTF_8.toString())
        return "renderer/$encoded"
    }
}

@Composable
fun MeteoroAppNavigation(deepLinkUrl: String?, standaloneMode: Boolean = false) {
    val navController = rememberNavController()
    var splashFinished by remember { mutableStateOf(false) }

    // Handle incoming deep link
    LaunchedEffect(deepLinkUrl) {
        if (!deepLinkUrl.isNullOrBlank() && (!standaloneMode || splashFinished)) {
            navController.navigate(MeteoroRoutes.buildRendererRoute(deepLinkUrl)) {
                popUpTo(MeteoroRoutes.MAIN) { inclusive = false }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = MeteoroRoutes.SPLASH
    ) {
        composable(MeteoroRoutes.SPLASH) {
            SplashScreen(
                appName = if (standaloneMode) {
                    BuildConfig.METEORO_PROJECT_NAME.uppercase()
                } else {
                    "METEORO GO"
                },
                onSplashFinished = {
                    splashFinished = true
                    val startupUrl = deepLinkUrl
                    if (standaloneMode && !startupUrl.isNullOrBlank()) {
                        navController.navigate(MeteoroRoutes.buildRendererRoute(startupUrl)) {
                            popUpTo(MeteoroRoutes.SPLASH) { inclusive = true }
                        }
                    } else {
                        navController.navigate(MeteoroRoutes.MAIN) {
                            popUpTo(MeteoroRoutes.SPLASH) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(MeteoroRoutes.MAIN) {
            MainScreen(
                onNavigateToScanner = {
                    navController.navigate(MeteoroRoutes.SCANNER)
                },
                onNavigateToRenderer = { url ->
                    navController.navigate(MeteoroRoutes.buildRendererRoute(url))
                }
            )
        }

        composable(MeteoroRoutes.SCANNER) {
            QrScannerScreen(
                onQrScanned = { scannedUrl ->
                    navController.navigate(MeteoroRoutes.buildRendererRoute(scannedUrl)) {
                        popUpTo(MeteoroRoutes.SCANNER) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = MeteoroRoutes.RENDERER,
            arguments = listOf(
                navArgument("encodedUrl") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("encodedUrl").orEmpty()
            val decodedUrl = try {
                URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                encodedUrl
            }

            RendererScreen(
                targetUrl = decodedUrl,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
