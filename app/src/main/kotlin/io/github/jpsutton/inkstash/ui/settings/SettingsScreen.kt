package io.github.jpsutton.inkstash.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jpsutton.inkstash.BuildConfig
import io.github.jpsutton.inkstash.ui.BigButton
import io.github.jpsutton.inkstash.ui.ButtonStyle
import io.github.jpsutton.inkstash.ui.Fredoka
import io.github.jpsutton.inkstash.ui.Ink
import io.github.jpsutton.inkstash.ui.Muted
import io.github.jpsutton.inkstash.ui.SheetTitle
import io.github.jpsutton.inkstash.ui.cancelStyle
import io.github.jpsutton.inkstash.ui.isCompact
import io.github.jpsutton.inkstash.ui.ThemeMode
import io.github.jpsutton.inkstash.ui.Title
import java.text.DateFormat
import java.util.Date

const val COMMUNITY_CODE_NOTICE =
    "Portions of the materials used are property of Disney/Ravensburger. ©Disney/Ravensburger. " +
        "Used under Ravensburger's Community Code Policy. We are expressly prohibited from charging you " +
        "to use or access this content. inkstash is not published, endorsed, or specifically approved " +
        "by Disney or Ravensburger."

@Composable
fun SettingsScreen(vm: SettingsViewModel, theme: ThemeMode, onTheme: (ThemeMode) -> Unit, onMesh: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    var pendingLoad by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u -> u?.let { vm.saveBackup(ctx.contentResolver, it) } }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> pendingLoad = u }
    pendingLoad?.let { u ->
        io.github.jpsutton.inkstash.ui.SheetDialog({ pendingLoad = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SheetTitle("Replace your collection?")
                Muted("Everything in your collection and places is replaced by what is in the backup file.", size = 16)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Keep mine", { pendingLoad = null }, style = cancelStyle(), modifier = Modifier.weight(1f))
                    BigButton("Replace", { vm.loadBackup(ctx.contentResolver, u); pendingLoad = null }, style = ButtonStyle.Danger, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    val onSave = { save.launch("inkstash-backup.json") }
    val onLoad = { load.launch(arrayOf("application/json", "text/plain", "*/*")) }

    if (isCompact()) Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Title("Settings")
        LookSection(theme, onTheme)
        DatabaseSection(s, vm)
        MeshSection(onMesh)
        BackupSection(onSave, onLoad)
        AboutSection(uri::openUri)
    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Title("Settings")
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                LookSection(theme, onTheme)
                DatabaseSection(s, vm)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                MeshSection(onMesh)
                BackupSection(onSave, onLoad)
                AboutSection(uri::openUri)
            }
        }
    }
}

@Composable
private fun LookSection(theme: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    val c = Ink.colors
    val compact = isCompact()
    val device = if (compact) "device" else "tablet"
    Section("Look") {
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
            for ((mode, label, icon) in listOf(
                Triple(ThemeMode.SYSTEM, "Same as $device", Icons.Outlined.PhoneAndroid),
                Triple(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
                Triple(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
            )) {
                val on = mode == theme
                val shape = RoundedCornerShape(if (compact) 16.dp else 18.dp)
                // Icon above label on every size. A side-by-side Row let a wrapped label fill the
                // leftover width, which pushed the icon to the tile's left edge.
                val tile = Modifier.weight(1f).height(if (compact) 84.dp else 72.dp).clip(shape).background(if (on) c.surface2 else c.surface)
                    .border(if (compact) 3.dp else 2.dp, if (on) c.primary else c.border, shape).clickable(role = Role.RadioButton) { onTheme(mode) }
                Column(tile.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                    Icon(icon, null, tint = c.text, modifier = Modifier.size(24.dp))
                    Text(label, color = c.text, fontSize = if (compact) 14.sp else 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                }
            }
        }
        Muted(when (theme) {
            ThemeMode.SYSTEM -> "Follows the $device: light by day, dark when the $device switches to dark mode."
            ThemeMode.LIGHT -> "Always light."
            ThemeMode.DARK -> "Always dark. Easier on the eyes at night."
        }, size = 14)
    }
}

@Composable
private fun DatabaseSection(s: SettingsState, vm: SettingsViewModel) {
    val c = Ink.colors
    Section("Card database", "Every Lorcana card the app knows, used to match your scans", Icons.Outlined.Storage) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (isCompact()) 14.dp else 16.dp)).background(c.surface2).padding(if (isCompact()) PaddingValues(horizontal = 14.dp, vertical = 12.dp) else PaddingValues(14.dp))) {
            Text("${s.cardCount} cards from ${s.setCount} sets", color = c.text, fontSize = if (isCompact()) 15.sp else 17.sp, fontWeight = FontWeight.ExtraBold)
            Muted(if (s.lastSync == 0L) "Not updated yet. Using the cards that came with the app." else "Last updated " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.lastSync)), size = 14)
        }
        s.progress?.let { p ->
            val frac = if (p.total == 0) 0f else p.done.toFloat() / p.total
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.surface2)) {
                Box(Modifier.fillMaxWidth(frac).height(10.dp).background(c.accent))
            }
            Muted("Downloading set ${p.done.coerceAtLeast(1).coerceAtMost(p.total)} of ${p.total}…", size = 14)
        }
        BigButton(if (s.updating) "Stop update" else "Update card database", vm::toggleUpdate, icon = if (s.updating) Icons.Outlined.Stop else Icons.Outlined.Refresh,
            style = if (s.updating) ButtonStyle.Outlined else ButtonStyle.Primary, modifier = Modifier.fillMaxWidth())
        s.message?.let { Muted(it, size = 14) }
    }
}

@Composable
private fun MeshSection(onMesh: () -> Unit) {
    Section("Sync with other devices", "Keep this collection the same on your other devices, over Wi-Fi", Icons.Outlined.Sync) {
        BigButton("Set up or manage", onMesh, icon = Icons.Outlined.Sync, style = if (isCompact()) ButtonStyle.Accent else ButtonStyle.Outlined, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun BackupSection(onSave: () -> Unit, onLoad: () -> Unit) {
    val c = Ink.colors
    val compact = isCompact()
    Section("Backup", "Save your collection to a file so you never lose it") {
        if (compact) {
            BigButton("Save a backup", onSave, icon = Icons.Outlined.FileDownload, style = ButtonStyle.Accent, modifier = Modifier.fillMaxWidth())
            BigButton("Load a backup", onLoad, icon = Icons.Outlined.FileUpload, modifier = Modifier.fillMaxWidth())
            Text("Loading a backup replaces everything in your collection.", color = c.warnFg, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.warnBg).padding(horizontal = 12.dp, vertical = 10.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Save a backup", onSave, icon = Icons.Outlined.FileDownload, modifier = Modifier.weight(1f))
                BigButton("Load a backup", onLoad, icon = Icons.Outlined.FileUpload, modifier = Modifier.weight(1f))
            }
            Muted("Loading a backup replaces everything in your collection.", size = 14)
        }
    }
}

@Composable
private fun AboutSection(openUri: (String) -> Unit) {
    val c = Ink.colors
    val compact = isCompact()
    Section(
        "inkstash", "Version ${BuildConfig.VERSION_NAME} · MIT License", Icons.Outlined.WaterDrop,
        iconBg = if (compact) c.hero else c.surface2, iconTint = if (compact) c.accent else c.text,
    ) {
        Row { Muted("Card data and images from ", size = 14); Text("Lorcast", color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { openUri("https://lorcast.com") }); Muted(".", size = 14) }
        Muted(COMMUNITY_CODE_NOTICE, size = 13)
        Row(Modifier.heightIn(min = 44.dp).clickable { openUri(BuildConfig.SOURCE_URL) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Code, null, tint = c.primary, modifier = Modifier.size(20.dp))
            Text(BuildConfig.SOURCE_URL.removePrefix("https://"), color = c.primary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun Section(
    title: String, subtitle: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconBg: Color = Ink.colors.surface2, iconTint: Color = Ink.colors.text, content: @Composable () -> Unit,
) {
    val c = Ink.colors
    val compact = isCompact()
    val shape = RoundedCornerShape(if (compact) 20.dp else 22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.surface).border(2.dp, c.border, shape)
            .padding(if (compact) PaddingValues(horizontal = 16.dp, vertical = 18.dp) else PaddingValues(20.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) Box(Modifier.size(44.dp).clip(RoundedCornerShape(if (compact) 13.dp else 14.dp)).background(iconBg), Alignment.Center) { Icon(icon, null, tint = iconTint) }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = c.text)
                if (subtitle != null) Muted(subtitle, size = if (compact) 13 else 14)
            }
        }
        content()
    }
}
