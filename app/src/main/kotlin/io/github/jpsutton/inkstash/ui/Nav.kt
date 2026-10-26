package io.github.jpsutton.inkstash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.jpsutton.inkstash.AppContainer
import io.github.jpsutton.inkstash.ui.collection.CollectionScreen
import io.github.jpsutton.inkstash.ui.collection.CollectionViewModel
import io.github.jpsutton.inkstash.ui.detail.CardDetailScreen
import io.github.jpsutton.inkstash.ui.detail.CardDetailViewModel
import io.github.jpsutton.inkstash.ui.places.PlacesScreen
import io.github.jpsutton.inkstash.ui.places.PlacesViewModel
import io.github.jpsutton.inkstash.ui.places.TempSetScreen
import io.github.jpsutton.inkstash.ui.places.TempSetViewModel
import io.github.jpsutton.inkstash.ui.mesh.MeshScreen
import io.github.jpsutton.inkstash.ui.mesh.MeshViewModel
import io.github.jpsutton.inkstash.ui.mesh.QrScanScreen
import io.github.jpsutton.inkstash.ui.settings.SettingsScreen
import io.github.jpsutton.inkstash.ui.settings.SettingsViewModel
import androidx.compose.runtime.collectAsState
import io.github.jpsutton.inkstash.ui.scan.ReviewScreen
import io.github.jpsutton.inkstash.ui.scan.ReviewViewModel
import io.github.jpsutton.inkstash.ui.scan.ScanScreen
import io.github.jpsutton.inkstash.ui.scan.ScanViewModel
import androidx.compose.runtime.getValue

/** Screens narrower than this (smallest side, in dp) get the phone layouts. */
private const val PHONE_MAX_WIDTH_DP = 600

@Composable
fun InkstashRoot(app: AppContainer) {
    val nav = rememberNavController()
    val phone = LocalConfiguration.current.smallestScreenWidthDp < PHONE_MAX_WIDTH_DP
    CompositionLocalProvider(LocalCompact provides phone) {
        NavHost(nav, startDestination = "collection") {
            composable("collection") {
                ScreenWithRail(Tab.Collection, { nav.navigate(it.route) }) {
                    CollectionScreen(vm { CollectionViewModel(app.collection, app.cards) }, onCard = { nav.navigate("card/$it") }, onScan = { nav.navigate("scan") })
                }
            }
            composable("card/{id}", listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments!!.getString("id")!!
                ScreenWithRail(Tab.Collection, { nav.navigate(it.route) }) {
                    CardDetailScreen(vm("card-$id") { CardDetailViewModel(id, app.cards, app.collection) }, onBack = { nav.popBackStack() })
                }
            }
            composable("scan") {
                ScanScreen(
                    vm { ScanViewModel(app) },
                    onClose = { nav.popBackStack() },
                    onScanned = { nav.navigate("review") },
                )
            }
            composable("review") {
                ReviewScreen(
                    vm { ReviewViewModel(app) },
                    onRetake = { nav.popBackStack() },
                    onDone = { nav.popBackStack("collection", inclusive = false) },
                )
            }
            composable("places") {
                ScreenWithRail(Tab.Places, { nav.navigate(it.route) }) {
                    PlacesScreen(
                        vm { PlacesViewModel(app.collection) },
                        onGeneral = { nav.navigate("collection") },
                        onTempSet = { nav.navigate("tempset/$it") },
                    )
                }
            }
            composable("tempset/{id}", listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments!!.getLong("id")
                ScreenWithRail(Tab.Places, { nav.navigate(it.route) }) {
                    TempSetScreen(vm("tempset-$id") { TempSetViewModel(id, app.collection) }, onBack = { nav.popBackStack() })
                }
            }
            composable("settings") {
                val theme by app.theme.collectAsState()
                ScreenWithRail(Tab.Settings, { nav.navigate(it.route) }) {
                    SettingsScreen(vm { SettingsViewModel(app.cards, app.collection) }, theme, app::setTheme, onMesh = { nav.navigate("mesh") })
                }
            }
            composable("mesh") {
                ScreenWithRail(Tab.Settings, { nav.navigate(it.route) }) {
                    MeshScreen(
                        vm { MeshViewModel(app.mesh, app.appScope) },
                        onBack = { nav.popBackStack() },
                        onScanCode = { nav.navigate("mesh/scan") },
                    )
                }
            }
            composable("mesh/scan") {
                val meshVm = vm { MeshViewModel(app.mesh, app.appScope) }
                QrScanScreen(
                    onInvite = { invite ->
                        meshVm.join(invite)
                        nav.popBackStack() // back to the mesh screen, which shows the progress
                    },
                    onClose = { nav.popBackStack() },
                )
            }
        }
    }
}
