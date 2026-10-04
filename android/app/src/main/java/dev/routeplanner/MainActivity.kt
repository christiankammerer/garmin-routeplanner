package dev.routeplanner

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.routeplanner.route.BROUTER_PACKAGE
import dev.routeplanner.route.BRouterEngine
import dev.routeplanner.route.RouteGenerator
import dev.routeplanner.ui.RequestScreen
import dev.routeplanner.ui.RequestViewModel

class MainActivity : ComponentActivity() {
    private val requestViewModel: RequestViewModel by viewModels {
        viewModelFactory { initializer { RequestViewModel(RouteGenerator(BRouterEngine(application))) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RequestScreen(
                    viewModel = requestViewModel,
                    onGetBRouter = ::getBRouter,
                    onOpenBRouter = ::openBRouter,
                )
            }
        }
    }

    private fun getBRouter() {
        val store = Intent(Intent.ACTION_VIEW, "market://details?id=$BROUTER_PACKAGE".toUri())
        val web = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$BROUTER_PACKAGE".toUri())
        try {
            startActivity(store)
        } catch (_: ActivityNotFoundException) {
            startActivity(web)
        }
    }

    private fun openBRouter() {
        packageManager.getLaunchIntentForPackage(BROUTER_PACKAGE)?.let(::startActivity)
    }
}
