package io.github.jpsutton.inkstash.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jpsutton.inkstash.data.Location
import io.github.jpsutton.inkstash.data.LocationKind
import io.github.jpsutton.inkstash.ui.BigButton
import io.github.jpsutton.inkstash.ui.ButtonStyle
import io.github.jpsutton.inkstash.ui.Fredoka
import io.github.jpsutton.inkstash.ui.Ink
import io.github.jpsutton.inkstash.ui.InkDot
import io.github.jpsutton.inkstash.ui.Muted
import io.github.jpsutton.inkstash.ui.Nunito
import io.github.jpsutton.inkstash.ui.RoundIconButton
import io.github.jpsutton.inkstash.ui.SheetDialog
import io.github.jpsutton.inkstash.ui.SheetTitle
import io.github.jpsutton.inkstash.ui.cancelStyle
import io.github.jpsutton.inkstash.ui.confirmStyle
import io.github.jpsutton.inkstash.ui.dashedBorder
import io.github.jpsutton.inkstash.ui.isCompact
import io.github.jpsutton.inkstash.ui.Title
import io.github.jpsutton.inkstash.ui.plural

private sealed interface Dialog {
    data class New(val kind: LocationKind) : Dialog
    data class Rename(val location: Location) : Dialog
    data class Delete(val location: Location) : Dialog
}

@Composable
fun PlacesScreen(vm: PlacesViewModel, onGeneral: () -> Unit, onTempSet: (Long) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = Ink.colors
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    if (isCompact()) PlacesPhone(
        s, onGeneral, onTempSet,
        onNew = { dialog = Dialog.New(it) }, onRename = { dialog = Dialog.Rename(it) }, onDelete = { dialog = Dialog.Delete(it) },
    ) else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column {
            Title("Places")
            Muted("Where your ${s.total} cards live", size = 16)
        }
        // General
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.hero).clickable(onClick = onGeneral).padding(22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(c.hero2), Alignment.Center) {
                Icon(Icons.Outlined.Inventory2, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(32.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("General", color = androidx.compose.ui.graphics.Color.White, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
                Text("Your main collection. New cards land here unless you pick a different place.", color = c.railText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("${s.general?.cardCount ?: 0}", color = androidx.compose.ui.graphics.Color.White, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 40.sp)
            Text(" cards", color = c.railText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        // Pools
        Section("Pools", "Boxes and binders that keep cards for a long time") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (p in s.pools) PoolCard(p.location, p.cardCount, { dialog = Dialog.Rename(p.location) }, { dialog = Dialog.Delete(p.location) })
                AddTile("New pool", Modifier.width(300.dp).height(88.dp)) { dialog = Dialog.New(LocationKind.POOL) }
            }
        }
        // Temp sets
        Section("Temp sets", "Borrow cards for a deck. Dissolve it and every card goes back home.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (t in s.temps) TempCard(t, { onTempSet(t.location.id) }, { dialog = Dialog.Rename(t.location) }, { dialog = Dialog.Delete(t.location) })
                AddTile("New temp set", Modifier.width(300.dp).height(160.dp)) { dialog = Dialog.New(LocationKind.TEMP_SET) }
            }
        }
    }

    when (val d = dialog) {
        is Dialog.New -> NameDialog(
            if (d.kind == LocationKind.TEMP_SET) "New temp set" else "New pool",
            if (d.kind == LocationKind.TEMP_SET) "Make a deck from cards you already own. Moving a card in remembers where it came from." else "A pool is a box or binder that keeps cards for a long time.",
            if (d.kind == LocationKind.TEMP_SET) "Saturday tournament deck" else "Trade binder",
            "", "Create", { dialog = null }) { vm.create(it, d.kind); dialog = null }
        is Dialog.Rename -> NameDialog("Rename", "Pick a new name.", "", d.location.name, "Save", { dialog = null }) { vm.rename(d.location.id, it); dialog = null }
        is Dialog.Delete -> SheetDialog({ dialog = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SheetTitle("Delete ${d.location.name}?")
                Muted(
                    if (d.location.kind == LocationKind.TEMP_SET) "All its cards go back where they came from. No cards are lost."
                    else "Its cards move to General. No cards are lost.", size = 16,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Keep it", { dialog = null }, style = cancelStyle(), modifier = Modifier.weight(1f))
                    BigButton("Delete", { vm.delete(d.location.id); dialog = null }, style = ButtonStyle.Danger, modifier = Modifier.weight(1f))
                }
            }
        }
        null -> Unit
    }
}

/** The phone layout: everything in one column, pools as a list and temp sets as full-width cards. */
@Composable
private fun PlacesPhone(
    s: PlacesState, onGeneral: () -> Unit, onTempSet: (Long) -> Unit,
    onNew: (LocationKind) -> Unit, onRename: (Location) -> Unit, onDelete: (Location) -> Unit,
) {
    val c = Ink.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Title("Places")
            Muted("Where your ${s.total} cards live", size = 14)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.hero).clickable(onClick = onGeneral).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c.hero2), Alignment.Center) {
                Icon(Icons.Outlined.Inventory2, null, tint = c.accent, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("General", color = Color.White, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                Text("Your main collection. New cards land here unless you pick a different place.", color = c.railText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${s.general?.cardCount ?: 0}", color = Color.White, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 28.sp)
                Text("cards", color = c.railText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Section("Pools", "Boxes and binders that keep cards for a long time") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).border(2.dp, c.border, RoundedCornerShape(20.dp)).padding(start = 14.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)) {
                for (p in s.pools) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(c.surface2), Alignment.Center) { Icon(Icons.Outlined.Inventory2, null, tint = c.text, modifier = Modifier.size(20.dp)) }
                        Text(p.location.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(plural(p.cardCount, "card"), color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        RowMenu("Options for ${p.location.name}", { onRename(p.location) }, { onDelete(p.location) })
                    }
                    HorizontalDivider(color = c.divider)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { onNew(LocationKind.POOL) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(38.dp).dashedBorder(c.dash, 11.dp), Alignment.Center) { Icon(Icons.Outlined.Add, null, tint = c.text, modifier = Modifier.size(18.dp)) }
                    Text("New pool", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Section("Temp sets", "Borrow cards for a deck. Dissolve it and every card goes back home.") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (t in s.temps) TempCard(t, { onTempSet(t.location.id) }, { onRename(t.location) }, { onDelete(t.location) })
                Row(
                    Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(20.dp)).dashedBorder(c.infoBorder, 20.dp, 3.dp).clickable { onNew(LocationKind.TEMP_SET) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Outlined.Add, null, tint = c.text, modifier = Modifier.size(20.dp))
                    Text("New temp set", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, help: String, content: @Composable () -> Unit) {
    val compact = isCompact()
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)) {
        if (compact) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Ink.colors.text)
            Muted(help, size = 14)
        } else Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Ink.colors.text)
            Muted(help, size = 15)
        }
        content()
    }
}

@Composable
private fun RowMenu(label: String, onRename: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        if (isCompact()) Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { open = true }.semantics { contentDescription = label },
            Alignment.Center,
        ) { Icon(Icons.Outlined.MoreVert, null, tint = Ink.colors.muted, modifier = Modifier.size(22.dp)) }
        else RoundIconButton(Icons.Outlined.MoreVert, label, { open = true }, size = 48.dp)
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { open = false; onDelete() })
        }
    }
}

@Composable
private fun PoolCard(l: Location, count: Int, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = Ink.colors
    Row(
        Modifier.width(300.dp).height(88.dp).clip(RoundedCornerShape(20.dp)).background(c.surface).border(2.dp, c.border, RoundedCornerShape(20.dp)).padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2), Alignment.Center) { Icon(Icons.Outlined.Inventory2, null, tint = c.text) }
        Column(Modifier.weight(1f)) {
            Text(l.name, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Muted(plural(count, "card"), size = 14)
        }
        RowMenu("Options for ${l.name}", onRename, onDelete)
    }
}

@Composable
private fun TempCard(t: TempSetSummary, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = Ink.colors
    val compact = isCompact()
    Box(
        (if (compact) Modifier.fillMaxWidth() else Modifier.width(300.dp).height(160.dp)).clip(RoundedCornerShape(20.dp)).background(c.surface)
            .border(2.dp, if (compact) c.infoBorder else c.border, RoundedCornerShape(20.dp)).clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Temp set", color = c.infoFg, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.infoBg).padding(horizontal = 8.dp, vertical = 2.dp))
            Text(t.location.name, color = c.text, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 1, modifier = Modifier.padding(end = 40.dp))
            Muted("${plural(t.count, "card")} · from ${t.sources}", size = 14)
            if (compact) Spacer(Modifier.height(2.dp))
            val total = t.inks.sumOf { it.second }.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.surface2)) {
                for ((ink, n) in t.inks) Box(Modifier.weight(n.toFloat() / total).fillMaxSize().background(ink.color))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((ink, n) in t.inks) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    InkDot(ink, 10.dp); Text("${ink.label} $n", color = c.muted, fontSize = if (compact) 13.sp else 12.sp, fontWeight = if (compact) FontWeight.ExtraBold else FontWeight.Bold)
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { RowMenu("Options for ${t.location.name}", onRename, onDelete) }
    }
}

@Composable
private fun AddTile(label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Ink.colors
    Box(
        modifier.clip(RoundedCornerShape(20.dp)).border(2.dp, c.dash, RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Add, null, tint = c.text)
            Text(label, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun NameDialog(title: String, help: String, placeholder: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = Ink.colors
    var name by remember { mutableStateOf(initial) }
    SheetDialog(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SheetTitle(title)
            Muted(help, size = if (isCompact()) 15 else 16)
            Text("Name", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(c.surface2).border(2.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp), Alignment.CenterStart) {
                if (name.isEmpty()) Text(placeholder, color = c.muted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                BasicTextField(name, { name = it }, singleLine = true, cursorBrush = SolidColor(c.text),
                    textStyle = TextStyle(color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = Nunito), modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Cancel", onDismiss, style = cancelStyle(), modifier = Modifier.weight(1f))
                BigButton(confirm, { onConfirm(name) }, style = confirmStyle(), enabled = name.isNotBlank(), modifier = Modifier.weight(1f))
            }
        }
    }
}
