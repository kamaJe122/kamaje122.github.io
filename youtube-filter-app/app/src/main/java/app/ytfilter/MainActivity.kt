package app.ytfilter

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.ytfilter.ui.FeedScreen
import app.ytfilter.ui.FeedViewModel
import app.ytfilter.ui.PlayerScreen
import app.ytfilter.ui.RulesScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: FeedViewModel by viewModels()
    private val auth by lazy { GoogleAuth(this) }

    private val consent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        auth.tokenFromConsent(result.data)?.let(vm::signedIn)
            ?: Toast.makeText(this, "登入取消", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) vm.refresh()

        setContent {
            val ctx = LocalContext.current
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            MaterialTheme(colorScheme = scheme) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "feed") {
                    composable("feed") {
                        FeedScreen(
                            vm = vm,
                            onSignIn = ::signIn,
                            onOpenRules = { nav.navigate("rules") },
                            onPlay = { nav.navigate("player/${it.id}") },
                        )
                    }
                    composable("rules") { RulesScreen(vm, onBack = { nav.popBackStack() }) }
                    composable("player/{id}") { entry ->
                        PlayerScreen(entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    private fun signIn() = lifecycleScope.launch {
        runCatching { auth.authorize() }
            .onSuccess { r ->
                when (r) {
                    is GoogleAuth.Result.Token -> vm.signedIn(r.value)
                    is GoogleAuth.Result.NeedsConsent -> consent.launch(r.request)
                }
            }
            .onFailure { Toast.makeText(this@MainActivity, "登入失敗：${it.message}", Toast.LENGTH_LONG).show() }
    }
}
