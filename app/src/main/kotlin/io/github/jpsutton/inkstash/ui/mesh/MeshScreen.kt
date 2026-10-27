package io.github.jpsutton.inkstash.ui.mesh

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jpsutton.inkstash.mesh.JoinProgress
import io.github.jpsutton.inkstash.mesh.LocalNetworkPermission
import io.github.jpsutton.inkstash.mesh.MeshInvite
import io.github.jpsutton.inkstash.mesh.MeshNode
import io.github.jpsutton.inkstash.mesh.MeshState
import io.github.jpsutton.inkstash.mesh.PeerStatus
import io.github.jpsutton.inkstash.mesh.PendingJoin
import io.github.jpsutton.inkstash.ui.BigButton
import io.github.jpsutton.inkstash.ui.ButtonStyle
import io.github.jpsutton.inkstash.ui.Fredoka
import io.github.jpsutton.inkstash.ui.Ink
import io.github.jpsutton.inkstash.ui.InkTextField
import io.github.jpsutton.inkstash.ui.Muted
import io.github.jpsutton.inkstash.ui.SheetDialog
import io.github.jpsutton.inkstash.ui.isCompact
import io.github.jpsutton.inkstash.ui.Title

private sealed interface Confirm {
    data class Remove(val peer: PeerStatus) : Confirm
    data object Leave : Confirm
}

@Composable
fun MeshScreen(vm: MeshViewModel, onBack: () -> Unit, onScanCode: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val invite by vm.invite.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val c = Ink.colors
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val ctx = LocalContext.current
    var allowed by remember { mutableStateOf(LocalNetworkPermission.granted(ctx)) }
    fun setAllowed(now: Boolean) {
        if (now && !allowed) vm.restartNetwork() // listening and dialing were blocked until now
        allowed = now
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { setAllowed(it) }
    val ask = { request.launch(LocalNetworkPermission.NAME) }
    LaunchedEffect(Unit) { if (!allowed) ask() }
    // The person may switch it on in the phone's settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { setAllowed(LocalNetworkPermission.granted(ctx)) }
    val openSettings = {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = if (isCompact()) 16.dp else 32.dp, vertical = if (isCompact()) 20.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(if (isCompact()) 16.dp else 20.dp),
    ) {
        Row(Modifier.clip(RoundedCornerShape(26.dp)).clickable(onClick = onBack).height(48.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = c.text)
            Text("Settings", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column {
            Title("Sync with other devices")
            Muted("Each device keeps its own copy. Linked devices keep each other up to date over Wi-Fi. No account and no cloud.", size = 16)
        }
        s.notice?.let { Notice(it, vm::dismissNotice) }
        s.listenError?.let { Notice(it, null) }
        if (!allowed) NeedsLocalNetwork(onAllow = ask, onSettings = openSettings)
        if (s.inMesh) InMesh(s, vm, allowed, ask, onConfirm = { confirm = it }) else NotInMesh(s, vm, allowed, ask, onScanCode)
    }

    invite?.let { InviteDialog(it, vm::hideInvite) }
    error?.let { msg ->
        SheetDialog(vm::clearError) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Hmm", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = c.text)
                Muted(msg, size = 16)
                BigButton("OK", vm::clearError, style = ButtonStyle.Primary, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    when (val cf = confirm) {
        is Confirm.Remove -> ConfirmDialog(
            "Remove ${cf.peer.record.name}?", "It stops getting updates. It keeps the copy of your collection it already has.", "Remove",
            onNo = { confirm = null }, onYes = { vm.remove(cf.peer.record.nodeId); confirm = null },
        )
        Confirm.Leave -> ConfirmDialog(
            "Leave this mesh?", "This device stops syncing. It keeps its own copy of everything, and can join again later.", "Leave",
            onNo = { confirm = null }, onYes = { vm.leave(); confirm = null },
        )
        null -> Unit
    }
}

@Composable
private fun NeedsLocalNetwork(onAllow: () -> Unit, onSettings: () -> Unit) {
    Panel("Allow devices on your Wi-Fi", "inkstash needs your OK to talk to your other devices. Without it, syncing can't work.") {
        BigButton("Allow", onAllow, style = ButtonStyle.Primary, modifier = Modifier.fillMaxWidth())
        Muted("If nothing pops up, open the settings. Then choose Permissions, and turn on Nearby devices.", size = 14)
        BigButton("Open settings", onSettings, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NotInMesh(s: MeshState, vm: MeshViewModel, allowed: Boolean, ask: () -> Unit, onScanCode: () -> Unit) {
    var name by remember { mutableStateOf(s.deviceName) }
    var address by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var newPass2 by remember { mutableStateOf("") }

    Panel("This device's name", "Other devices show it under this name.") {
        InkTextField(name, { name = it; vm.rename(it) }, "My tablet", Modifier.fillMaxWidth())
    }
    Panel("Join a mesh", "Use another device that is already in it.") {
        JoinProgressView(s.join)
        BigButton("Scan a code", if (allowed) onScanCode else ask, icon = Icons.Outlined.QrCodeScanner, style = ButtonStyle.Primary, modifier = Modifier.fillMaxWidth())
        Muted("On the other device: Settings, Sync with other devices, Show a code to scan.", size = 14)
        Muted("Or type it in", size = 15)
        InkTextField(address, { address = it }, "Its address, like 192.168.1.20", Modifier.fillMaxWidth(), androidx.compose.ui.text.input.KeyboardType.Uri)
        InkTextField(pass, { pass = it }, "The mesh's passphrase", Modifier.fillMaxWidth())
        BigButton(
            "Ask to join", { if (allowed) vm.join(address, pass) else ask() }, modifier = Modifier.fillMaxWidth(),
            enabled = address.isNotBlank() && pass.isNotEmpty() && s.join !is JoinProgress.Connecting && s.join !is JoinProgress.WaitingForApproval,
        )
    }
    Panel("Start a mesh here", "Do this once, on the first device. Then add the others to it.") {
        Muted("Pick a passphrase you can remember, at least ${MeshNode.MIN_PASSPHRASE} letters. Three words work well.", size = 14)
        InkTextField(newPass, { newPass = it }, "Passphrase", Modifier.fillMaxWidth())
        InkTextField(newPass2, { newPass2 = it }, "Passphrase again", Modifier.fillMaxWidth())
        val ok = newPass.length >= MeshNode.MIN_PASSPHRASE && newPass == newPass2
        if (newPass2.isNotEmpty() && newPass != newPass2) Muted("The two don't match yet.", size = 14)
        BigButton("Start mesh", { vm.startMesh(newPass) }, style = ButtonStyle.Primary, enabled = ok, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun JoinProgressView(p: JoinProgress?) {
    val c = Ink.colors
    when (p) {
        null -> Unit
        JoinProgress.Connecting -> Muted("Connecting…", size = 16)
        is JoinProgress.WaitingForApproval -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.warnBg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Waiting for the other device to say yes", color = c.warnFg, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(p.code, color = c.text, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, letterSpacing = 6.sp)
            Muted("It should show the same number. If it doesn't, don't approve it.", size = 14)
        }
        is JoinProgress.Failed -> Text(p.message, color = c.badFg, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.badBg).padding(14.dp))
    }
}

@Composable
private fun InMesh(s: MeshState, vm: MeshViewModel, allowed: Boolean, ask: () -> Unit, onConfirm: (Confirm) -> Unit) {
    var name by remember { mutableStateOf(s.deviceName) }
    var newAddress by remember { mutableStateOf("") }

    for (p in s.pending) PendingCard(p, onApprove = { vm.approve(p.nodeId) }, onDecline = { vm.decline(p.nodeId) })

    Panel("Add a device", "The new device must be on the same Wi-Fi.") {
        BigButton("Show a code to scan", if (allowed) vm::showInvite else ask, icon = Icons.Outlined.QrCode2, style = ButtonStyle.Primary, modifier = Modifier.fillMaxWidth())
        Muted("On the new device, open Sync with other devices and tap Scan a code.", size = 14)
    }
    Panel("Devices in this mesh", if (s.members.isEmpty()) "No other device yet." else null) {
        for (m in s.members) PeerRow(m) { onConfirm(Confirm.Remove(m)) }
    }
    Panel("This device") {
        InkTextField(name, { name = it; vm.rename(it) }, "My tablet", Modifier.fillMaxWidth())
        if (s.addresses.isNotEmpty()) Muted("Reachable at ${s.addresses.joinToString(", ")}", size = 14)
        Muted("Other addresses to try (for a VPN, say)", size = 14)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            InkTextField(newAddress, { newAddress = it }, "Address", Modifier.weight(1f), androidx.compose.ui.text.input.KeyboardType.Uri)
            BigButton("Add", { vm.addAddress(newAddress); newAddress = "" }, icon = Icons.Outlined.Add, enabled = newAddress.isNotBlank())
        }
    }
    BigButton("Leave this mesh", { onConfirm(Confirm.Leave) }, style = ButtonStyle.Danger, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PendingCard(p: PendingJoin, onApprove: () -> Unit, onDecline: () -> Unit) {
    val c = Ink.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.warnBg).border(2.dp, c.accent, RoundedCornerShape(22.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${p.name} wants to join", color = c.warnFg, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
        Text(p.code, color = c.text, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, letterSpacing = 6.sp)
        Text("Does ${p.name} show the same number? If yes, approve it. If not, say no.", color = c.warnFg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("No", onDecline, icon = Icons.Outlined.Close, modifier = Modifier.weight(1f))
            BigButton("Yes, same number", onApprove, icon = Icons.Outlined.Check, style = ButtonStyle.Primary, modifier = Modifier.weight(2f))
        }
    }
}

@Composable
private fun PeerRow(p: PeerStatus, onRemove: () -> Unit) {
    val c = Ink.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(14.dp).background(if (p.online) c.okFg else c.muted, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(p.record.name, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Muted(if (p.online) "Connected" else "Not connected right now", size = 14)
        }
        BigButton("Remove", onRemove, height = 48.dp)
    }
}

@Composable
private fun InviteDialog(invite: MeshInvite, onDone: () -> Unit) {
    val c = Ink.colors
    val bitmap = remember(invite) { qrBitmap(invite.encode()) }
    val remaining by produceState(invite.expiresAt - System.currentTimeMillis(), invite) {
        while (true) {
            value = invite.expiresAt - System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }
    LaunchedEffect(remaining <= 0) { if (remaining <= 0) onDone() }
    SheetDialog(onDone, width = 560.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Scan this with the new device", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = c.text)
            Image(
                bitmap.asImageBitmap(), "QR code to join this mesh", filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
            )
            val min = (remaining.coerceAtLeast(0) / 60_000).toInt()
            val sec = ((remaining.coerceAtLeast(0) / 1000) % 60).toInt()
            Muted("Works once, for %d:%02d more. Only show it to the device you want to add.".format(min, sec), size = 15)
            BigButton("Done", onDone, style = ButtonStyle.Primary, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, yes: String, onNo: () -> Unit, onYes: () -> Unit) {
    val c = Ink.colors
    SheetDialog(onNo) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = c.text)
            Muted(text, size = 16)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Keep", onNo, modifier = Modifier.weight(1f))
                BigButton(yes, onYes, style = ButtonStyle.Danger, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Notice(text: String, onDismiss: (() -> Unit)?) {
    val c = Ink.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.infoBg).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, color = c.infoFg, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (onDismiss != null) BigButton("OK", onDismiss, height = 44.dp)
    }
}

@Composable
private fun Panel(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    val c = Ink.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).border(2.dp, c.border, RoundedCornerShape(22.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(title, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = c.text)
            if (subtitle != null) Muted(subtitle, size = 14)
        }
        content()
    }
}
