package io.github.jpsutton.inkstash.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jpsutton.inkstash.data.Location
import io.github.jpsutton.inkstash.data.LocationKind
import io.github.jpsutton.inkstash.ui.BigButton
import io.github.jpsutton.inkstash.ui.ButtonStyle
import io.github.jpsutton.inkstash.ui.CostGem
import io.github.jpsutton.inkstash.ui.Fredoka
import io.github.jpsutton.inkstash.ui.Ink
import io.github.jpsutton.inkstash.ui.InkColor
import io.github.jpsutton.inkstash.ui.InkDot
import io.github.jpsutton.inkstash.ui.Muted
import io.github.jpsutton.inkstash.ui.Nunito
import io.github.jpsutton.inkstash.ui.RoundIconButton
import io.github.jpsutton.inkstash.ui.SheetDialog
import io.github.jpsutton.inkstash.ui.SheetTitle
import io.github.jpsutton.inkstash.ui.cancelStyle
import io.github.jpsutton.inkstash.ui.isCompact
import io.github.jpsutton.inkstash.ui.plural

@Composable
fun TempSetScreen(vm: TempSetViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val loc = s.location ?: return
    val c = Ink.colors
    var renaming by remember { mutableStateOf(false) }
    var dissolving by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    if (isCompact()) TempSetPhone(s, loc, vm, onBack, onRename = { renaming = true }, onDissolve = { dissolving = true }, onAdd = { adding = true })
    else Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.clip(RoundedCornerShape(26.dp)).clickable(onClick = onBack).height(48.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = c.text)
            Text("Places", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Temp set", color = c.infoFg, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.infoBg).padding(horizontal = 10.dp, vertical = 2.dp))
                Text(loc.name, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = c.text, maxLines = 1)
            }
            BigButton("Rename", { renaming = true }, icon = Icons.Outlined.Edit, height = 52.dp)
            BigButton("Dissolve", { dissolving = true }, icon = Icons.AutoMirrored.Outlined.Undo, height = 52.dp, style = ButtonStyle.Danger)
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Panel("Cards", Modifier.weight(1f)) {
                        Text("${s.total}", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, color = c.text)
                        Muted("${s.different} different · ${s.inkable} inkable", size = 14)
                    }
                    Panel("Ink mix", Modifier.weight(1f)) {
                        val total = s.inks.sumOf { it.second }.coerceAtLeast(1)
                        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(c.surface2)) {
                            for ((ink, n) in s.inks) Box(Modifier.weight(n.toFloat() / total).fillMaxSize().background(ink.color))
                        }
                        for ((ink, n) in s.inks) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InkDot(ink); Text(ink.label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); Text("$n", color = c.text, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Panel("Borrowed from", Modifier.fillMaxWidth()) {
                    for ((name, n) in s.borrowedFrom) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Inventory2, null, tint = c.muted, modifier = Modifier.size(18.dp))
                        Text(name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        Text("$n", color = c.text, fontWeight = FontWeight.ExtraBold)
                    }
                    if (s.rows.isEmpty()) Muted("Nothing yet.", size = 14)
                }
                Panel("Cost curve", Modifier.fillMaxWidth()) {
                    Muted("How many cards at each ink cost", size = 14)
                    val curve = s.curve
                    val max = curve.maxOrNull()?.coerceAtLeast(1) ?: 1
                    Row(
                        Modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = "Cost curve: " + curve.mapIndexed { i, n -> "$n at cost ${if (i == 6) "7+" else (i + 1)}" }.joinToString(", ") },
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom,
                    ) {
                        for (n in curve) Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text("$n", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            Box(Modifier.fillMaxWidth().height((n.toFloat() / max * 160).dp).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(c.primary))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in 1..7) Text(if (i == 7) "7+" else "$i", color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(c.surface).border(2.dp, c.border, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("In this set", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = c.text, modifier = Modifier.weight(1f))
                    BigButton("Add cards", { adding = true }, icon = Icons.Outlined.Add, height = 48.dp)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(s.rows, key = { it.holding.id }) { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CostGem(r.card.cost, InkColor.of(r.card.ink), 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(r.card.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                Muted(r.card.version + if (r.holding.foil) " · Foil" else "", size = 12)
                            }
                            Text("×${r.holding.quantity}", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            RoundIconButton(Icons.Outlined.Remove, "Send one ${r.card.name} back", { vm.giveBack(r.holding.id) }, size = 40.dp)
                        }
                    }
                }
            }
        }
    }

    if (renaming) NameDialog("Rename", "Pick a new name.", "", loc.name, "Save", { renaming = false }) { vm.rename(it); renaming = false }
    if (adding) AddCardsDialog(s, loc.id, vm, onDismiss = { adding = false })
    if (dissolving) {
        val back = s.borrowedFrom
        SheetDialog({ dissolving = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SheetTitle("Dissolve this temp set?")
                Muted("All ${s.total} cards go back where they came from. The temp set goes away, but no cards are lost.", size = 16)
                for ((name, n) in back) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.okBg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { Text("$n ${if (n == 1) "card" else "cards"} back to ${if (name.startsWith("Nowhere")) "General" else name}", color = c.okFg, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("Keep it", { dissolving = false }, style = cancelStyle(), modifier = Modifier.weight(1f))
                    BigButton("Dissolve set", { dissolving = false; vm.dissolve(onBack) }, style = ButtonStyle.Danger, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** The phone layout: back link, name and actions, then stat panels stacked in a single scrolling column. */
@Composable
private fun TempSetPhone(
    s: TempSetState, loc: Location, vm: TempSetViewModel, onBack: () -> Unit,
    onRename: () -> Unit, onDissolve: () -> Unit, onAdd: () -> Unit,
) {
    val c = Ink.colors
    @Composable
    fun Tile(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp), content: @Composable ColumnScope.() -> Unit) =
        Column(modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).border(2.dp, c.border, RoundedCornerShape(18.dp)).padding(padding), content = content)
    @Composable
    fun Label(text: String) = Text(text, color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onBack).padding(start = 4.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = c.text, modifier = Modifier.size(24.dp))
            Text("Places", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Temp set", color = c.infoFg, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(c.infoBg).padding(horizontal = 10.dp).wrapContentHeight(Alignment.CenterVertically))
                Text(loc.name, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp, color = c.text)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Rename", Icons.Outlined.Edit, c.text, c.border, onRename, Modifier.weight(1f))
                ActionButton("Dissolve", Icons.AutoMirrored.Outlined.Undo, c.danger, c.danger, onDissolve, Modifier.weight(1f))
            }
        }
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                Label("Cards")
                Text("${s.total}", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 38.sp, color = c.text)
                Muted("${s.different} different · ${s.inkable} inkable", size = 13)
            }
            Tile(Modifier.weight(1f).fillMaxHeight()) {
                Label("Ink mix")
                Spacer(Modifier.height(8.dp))
                val total = s.inks.sumOf { it.second }.coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for ((ink, n) in s.inks) Box(Modifier.weight(n.toFloat() / total).fillMaxSize().background(ink.color))
                }
                Spacer(Modifier.height(8.dp))
                for ((ink, n) in s.inks) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InkDot(ink, 10.dp)
                    Text(ink.label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text("$n", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Tile(Modifier.fillMaxWidth()) {
            Label("Borrowed from")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((name, n) in s.borrowedFrom) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Inventory2, null, tint = c.text, modifier = Modifier.size(16.dp))
                    Text(name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text("$n", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                if (s.rows.isEmpty()) Muted("Nothing yet.", size = 14)
            }
        }
        Tile(Modifier.fillMaxWidth()) {
            Text("Cost curve", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = c.text)
            Muted("How many cards at each ink cost", size = 13)
            Spacer(Modifier.height(8.dp))
            val curve = s.curve
            val max = curve.maxOrNull()?.coerceAtLeast(1) ?: 1
            Row(
                Modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = "Cost curve: " + curve.mapIndexed { i, n -> "$n at cost ${if (i == 6) "7+" else (i + 1)}" }.joinToString(", ") },
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom,
            ) {
                for (n in curve) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)) {
                    Text("$n", color = c.text, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Box(Modifier.fillMaxWidth().height((n.toFloat() / max * 120).dp).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(c.primary))
                }
            }
            HorizontalDivider(thickness = 2.dp, color = c.border)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 1..7) Box(Modifier.weight(1f), Alignment.Center) {
                    Text(if (i == 7) "7+" else "$i", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                        modifier = Modifier.heightIn(min = 26.dp).widthIn(min = 26.dp).clip(CircleShape).background(c.surface2).wrapContentSize(Alignment.Center))
                }
            }
        }
        Tile(Modifier.fillMaxWidth(), PaddingValues(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
            Row(Modifier.padding(end = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("In this set", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = c.text, modifier = Modifier.weight(1f))
                Row(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.accent).clickable(role = Role.Button, onClick = onAdd).padding(start = 10.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Add, null, tint = c.onAccent, modifier = Modifier.size(18.dp))
                    Text("Add cards", color = c.onAccent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            for (r in s.rows) {
                HorizontalDivider(color = c.divider)
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CostGem(r.card.cost, InkColor.of(r.card.ink), 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(r.card.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.card.version + if (r.holding.foil) " · Foil" else "", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("×${r.holding.quantity}", color = c.text, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { vm.giveBack(r.holding.id) }.semantics { contentDescription = "Send one ${r.card.name} back" },
                        Alignment.Center,
                    ) { Icon(Icons.Outlined.Remove, null, tint = c.muted, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, tint: Color, border: Color, onClick: () -> Unit, modifier: Modifier) {
    val c = Ink.colors
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.surface).border(2.dp, border, RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Panel(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    val c = Ink.colors
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(c.surface).border(2.dp, c.border, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = c.text)
        content()
    }
}

/** Search the cards you own and borrow one copy at a time into this set. */
@Composable
private fun AddCardsDialog(s: TempSetState, setId: Long, vm: TempSetViewModel, onDismiss: () -> Unit) {
    val c = Ink.colors
    var q by remember { mutableStateOf("") }
    val inSet = s.rows.groupBy { it.card.id }.mapValues { e -> e.value.sumOf { it.holding.quantity } }
    val list = s.owned.filter { q.isBlank() || it.card.name.contains(q.trim(), true) || it.card.version.contains(q.trim(), true) }.take(60)
    SheetDialog(onDismiss, width = 640.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetTitle("Add cards", Modifier.weight(1f))
                if (!isCompact()) RoundIconButton(Icons.Outlined.Close, "Close", onDismiss, size = 48.dp)
            }
            Muted("Tap + to borrow one copy. It goes home when you dissolve the set.", size = 15)
            Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.surface2).border(2.dp, c.border, RoundedCornerShape(26.dp)).padding(horizontal = 18.dp), Alignment.CenterStart) {
                if (q.isEmpty()) Text("Find a card by name", color = c.muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                BasicTextField(q, { q = it }, singleLine = true, cursorBrush = SolidColor(c.text),
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Nunito), modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.card.id }) { o ->
                    val taken = inSet[o.card.id] ?: 0
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CostGem(o.card.cost, InkColor.of(o.card.ink), 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(o.card.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Muted(o.card.version, size = 12)
                        }
                        Muted("${o.qty} owned" + if (taken > 0) " · $taken here" else "", size = 13)
                        RoundIconButton(Icons.Outlined.Add, "Borrow one ${o.card.name}", { vm.borrow(o.card.id) }, size = 44.dp)
                    }
                }
            }
            if (isCompact()) BigButton("Done", onDismiss, style = ButtonStyle.Quiet, modifier = Modifier.fillMaxWidth())
        }
    }
}
