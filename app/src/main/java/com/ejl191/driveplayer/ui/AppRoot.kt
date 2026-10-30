package com.ejl191.driveplayer.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ejl191.driveplayer.app
import kotlinx.coroutines.launch

@Composable
fun AppRoot(connection: PlayerConnection) {
    val context = LocalContext.current
    val auth = context.app.auth
    val signedIn by auth.signedIn.collectAsState()
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    val controller = connection.controller
    val ui = rememberPlayerUi(controller)

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        auth.handleConsentResult(it.data)
    }
    val signIn: () -> Unit = {
        scope.launch {
            try {
                auth.authorize()?.let { consent.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
            } catch (e: Exception) {
                Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    // Silent check: succeeds without any UI if Drive access was granted before.
    LaunchedEffect(Unit) { runCatching { auth.authorize() } }

    val play: (String) -> Unit = { bookId ->
        scope.launch {
            connection.play(bookId)
            nav.navigate("player") { launchSingleTop = true }
        }
    }

    NavHost(nav, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                signedIn = signedIn,
                onSignIn = signIn,
                ui = ui,
                player = controller,
                onBrowse = { nav.navigate("browse") },
                onPlay = play,
                onOpenPlayer = { nav.navigate("player") { launchSingleTop = true } },
            )
        }
        composable("browse") {
            BrowseScreen(
                signedIn = signedIn,
                onSignIn = signIn,
                ui = ui,
                player = controller,
                onBack = { nav.popBackStack() },
                onPlay = { bookId ->
                    scope.launch {
                        connection.play(bookId)
                        nav.navigate("player") { popUpTo("library") }
                    }
                },
                onOpenPlayer = { nav.navigate("player") { launchSingleTop = true } },
            )
        }
        composable("player") {
            PlayerScreen(ui = ui, player = controller, onBack = { nav.popBackStack() })
        }
    }
}
