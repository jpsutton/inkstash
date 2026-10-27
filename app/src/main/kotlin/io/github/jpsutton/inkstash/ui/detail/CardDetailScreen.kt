package io.github.jpsutton.inkstash.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jpsutton.inkstash.data.Card
import io.github.jpsutton.inkstash.data.CardValue
import io.github.jpsutton.inkstash.data.GENERAL_ID
import io.github.jpsutton.inkstash.data.Location
import io.github.jpsutton.inkstash.data.LocationKind
import io.github.jpsutton.inkstash.ui.ArtSize
import io.github.jpsutton.inkstash.ui.Badge
import io.github.jpsutton.inkstash.ui.BigButton
import io.github.jpsutton.inkstash.ui.ButtonStyle
import io.github.jpsutton.inkstash.ui.CardArt
import io.github.jpsutton.inkstash.ui.Ink
import io.github.jpsutton.inkstash.ui.Fredoka
import io.github.jpsutton.inkstash.ui.SheetTitle
import io.github.jpsutton.inkstash.ui.cancelStyle
import io.github.jpsutton.inkstash.ui.confirmStyle
import io.github.jpsutton.inkstash.ui.isCompact
import io.github.jpsutton.inkstash.ui.InkColor
import io.github.jpsutton.inkstash.ui.Muted
import io.github.jpsutton.inkstash.ui.RoundIconButton
import io.github.jpsutton.inkstash.ui.SheetDialog
import io.github.jpsutton.inkstash.ui.Title
import io.github.jpsutton.inkstash.ui.kindLabel
import io.github.jpsutton.inkstash.ui.plural

@Composable
fun CardDetailScreen(vm: CardDetailViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val card = s.card ?: return
    val c = Ink.colors
    val ink = InkColor.of(card.ink)
    var moving by remember { mutableStateOf<CopyRow?>(null) }
    var adding by remember { mutableStateOf(false) }

    if (isCompact()) DetailPhone(
        s, card, ink, onBack, onMove = { moving = it }, onRemove = { vm.removeOne(it.holding.id) }, onAdd = { adding = true },
    ) else Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(26.dp)).clickable(onClick = onBack).height(52.dp).padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = c.text, modifier = Modifier.size(24.dp).padding(0.dp))
            Text("My collection", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Box(
                Modifier.fillMaxHeight().aspectRatio(5f / 7f).clip(RoundedCornerShape(22.dp))
                    .border(4.dp, ink.color, RoundedCornerShape(22.dp)),
            ) {
                CardArt(card, Modifier.fillMaxSize(), ArtSize.Large, costGem = 52.dp)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column {
                    Title(card.name)
                    Text(card.version, color = c.muted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Facts(card, ink)
                ValuePanel(card, s.value, s.total)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Your copies", fontFamily = io.github.jpsutton.inkstash.ui.Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = c.text, modifier = Modifier.weight(1f))
                        Muted("${s.total} total", size = 16)
                    }
                    for (row in s.rows) CopyRowView(row, onMove = { moving = row }, onRemove = { vm.removeOne(row.holding.id) })
                    if (s.rows.isEmpty()) Muted("You don't own this card yet.", size = 16)
                }
                BigButton("Add a copy", { adding = true }, icon = Icons.Outlined.Add, style = ButtonStyle.Primary, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    moving?.let { row ->
        MoveDialog(card, row, s.locations, onDismiss = { moving = null }, onMove = { qty, to ->
            vm.move(row.holding.id, qty, to); moving = null
        })
    }
    if (adding) AddCopyDialog(s.locations, onDismiss = { adding = false }, onAdd = { foil, loc -> vm.addCopy(foil, loc); adding = false })
}

/** The phone layout: art on top, then the facts, the copies and the add button, in one scrolling column. */
@Composable
private fun DetailPhone(
    s: CardDetailState, card: Card, ink: InkColor, onBack: () -> Unit,
    onMove: (CopyRow) -> Unit, onRemove: (CopyRow) -> Unit, onAdd: () -> Unit,
) {
    val c = Ink.colors
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onBack).padding(start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = c.text, modifier = Modifier.size(24.dp))
                Text("My collection", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(216.dp, 302.dp)
                    .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = c.shadow)
                    .clip(RoundedCornerShape(16.dp)).border(3.dp, ink.color, RoundedCornerShape(16.dp)),
            ) {
                CardArt(card, Modifier.fillMaxSize(), ArtSize.Large, costGem = 40.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Title(card.name, size = 32)
                Text(card.version, color = c.muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            FactsPhone(card, ink)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).border(2.dp, c.border, RoundedCornerShape(20.dp)).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp)) {
                Row(Modifier.padding(start = 2.dp, end = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your copies", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = c.text, modifier = Modifier.weight(1f))
                    Text("${s.total} total", color = c.muted, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                for (row in s.rows) {
                    HorizontalDivider(thickness = 2.dp, color = c.divider)
                    CopyRowPhone(row, onMove = { onMove(row) }, onRemove = { onRemove(row) })
                }
                if (s.rows.isEmpty()) Muted("You don't own this card yet.", Modifier.padding(start = 2.dp, bottom = 8.dp), size = 16)
            }
            BigButton("Add a copy", onAdd, icon = Icons.Outlined.Add, style = ButtonStyle.Primary, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Fact chips for the phone: 34 dp tall, outlined, with the ink tinted and the rarity highlighted. */
@Composable
private fun FactsPhone(card: Card, ink: InkColor) {
    val c = Ink.colors
    @Composable
    fun Chip(text: String, bg: Color = c.surface, border: Color = c.border, fg: Color = c.text, lead: (@Composable () -> Unit)? = null) {
        Row(
            Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(bg).border(2.dp, border, RoundedCornerShape(17.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            lead?.invoke()
            Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val tint = ink.color.copy(alpha = 0.18f).compositeOver(c.surface)
        Chip(card.ink.ifBlank { "No ink" }, bg = tint, border = tint, lead = { Box(Modifier.size(12.dp).background(ink.color, CircleShape)) })
        if (card.inkable) Chip("Inkable", lead = { Icon(Icons.Outlined.WaterDrop, null, tint = c.text, modifier = Modifier.size(14.dp)) })
        Chip("Costs ${card.cost}")
        if (card.type.isNotBlank()) Chip(card.type)
        if (card.rarity.isNotBlank()) Chip(card.rarity, bg = c.warnBg, border = c.accent, fg = c.warnFg)
        Chip("${card.setName} · ${card.collectorNumber}", fg = c.muted)
    }
}

@Composable
private fun CopyRowPhone(row: CopyRow, onMove: () -> Unit, onRemove: () -> Unit) {
    val c = Ink.colors
    val temp = row.place?.kind == LocationKind.TEMP_SET
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(if (temp) c.infoBg else c.surface2), Alignment.Center) {
            Icon(Icons.Outlined.Inventory2, null, tint = if (temp) c.infoFg else c.text, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.place?.name ?: "Unknown place", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (row.holding.foil) Badge("Foil", c.warnBg, c.warnFg, border = c.accent)
            }
            Muted(copyKind(row), size = 13)
        }
        Text("×${row.holding.quantity}", color = c.text, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Row(
            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.primary).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onMove).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.SwapHoriz, null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
            Text("Move", color = c.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        RoundIconButton(Icons.Outlined.Remove, "Remove one copy from ${row.place?.name}", onRemove, size = 44.dp, tint = c.danger)
    }
}

private fun copyKind(row: CopyRow) = when (row.place?.kind) {
    LocationKind.GENERAL -> "Main collection · ${if (row.holding.foil) "Foil" else "Normal"}"
    LocationKind.TEMP_SET -> "Temp set" + (row.sourceName?.let { " · borrowed from $it" } ?: "")
    else -> "Pool"
}

@Composable
private fun Facts(card: Card, ink: InkColor) {
    val c = Ink.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(c.surface2).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(14.dp).background(ink.color, CircleShape))
            Text(card.ink.ifBlank { "No ink" }, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (card.inkable) Fact("Inkable", icon = true)
        Fact("Costs ${card.cost}")
        if (card.type.isNotBlank()) Fact(card.type)
        if (card.rarity.isNotBlank()) Fact(card.rarity)
        Fact("${card.setName} · ${card.collectorNumber}")
    }
}

private val Dollars: java.text.NumberFormat = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US)

/** "Estimated value": what your copies are worth, and the price of one. A guess, not a promise. */
@Composable
private fun ValuePanel(card: Card, value: CardValue?, copies: Int) {
    val c = Ink.colors
    val each = buildList {
        card.priceUsd?.let { add("${Dollars.format(it)} each") }
        card.priceFoilUsd?.let { add("${Dollars.format(it)} foil") }
    }
    val hasPrice = each.isNotEmpty()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(2.dp, c.border, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c.warnBg), Alignment.Center) {
            Icon(Icons.Outlined.Sell, null, tint = c.warnFg, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Estimated value", color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                when {
                    !hasPrice -> "No price yet"
                    copies == 0 -> each.joinToString(" · ")
                    else -> Dollars.format(value?.total ?: 0.0)
                },
                fontFamily = io.github.jpsutton.inkstash.ui.Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = c.text,
            )
            val note = when {
                !hasPrice -> "Update the card database in Settings to get prices."
                copies == 0 -> "You don't own this card yet."
                value != null && value.unpricedCopies > 0 ->
                    "${each.joinToString(" · ")}. ${plural(value.unpricedCopies, "copy", "copies")} ${if (value.unpricedCopies == 1) "has" else "have"} no price."
                else -> "For your ${plural(copies, "copy", "copies")}: ${each.joinToString(" · ")}"
            }
            Muted(note, size = 14)
        }
        Muted("A guess from Lorcast, not a promise", size = 12, modifier = Modifier.width(150.dp))
    }
}

@Composable
private fun Fact(text: String, icon: Boolean = false) {
    val c = Ink.colors
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(c.surface2).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon) Icon(Icons.Outlined.WaterDrop, null, tint = c.text, modifier = Modifier.size(14.dp))
        Text(text, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun CopyRowView(row: CopyRow, onMove: () -> Unit, onRemove: () -> Unit) {
    val c = Ink.colors
    val temp = row.place?.kind == LocationKind.TEMP_SET
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(2.dp, c.border, RoundedCornerShape(18.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (temp) c.infoBg else c.surface2), Alignment.Center) {
            Icon(Icons.Outlined.Inventory2, null, tint = if (temp) c.infoFg else c.text, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(row.place?.name ?: "Unknown place", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Muted(copyKind(row), size = 14)
        }
        if (row.holding.foil) Badge("Foil", c.warnBg, c.warnFg, border = c.accent)
        Text("×${row.holding.quantity}", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        BigButton("Move", onMove, icon = Icons.Outlined.SwapHoriz, height = 48.dp)
        RoundIconButton(Icons.Outlined.Remove, "Remove one copy from ${row.place?.name}", onRemove, size = 48.dp)
    }
}

@Composable
private fun MoveDialog(card: Card, row: CopyRow, locations: List<Location>, onDismiss: () -> Unit, onMove: (Int, Long) -> Unit) {
    val c = Ink.colors
    val from = row.holding
    val dests = locations.filter { it.id != from.locationId }
    var qty by remember { mutableStateOf(1) }
    var dest by remember { mutableStateOf(dests.firstOrNull()?.id) }
    val destLoc = dests.firstOrNull { it.id == dest }
    SheetDialog(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetTitle("Move cards", Modifier.weight(1f))
                RoundIconButton(Icons.Outlined.Close, "Close", onDismiss, size = 48.dp)
            }
            Muted("${card.fullName} · from ${row.place?.name}", size = 16)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("How many?", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                RoundIconButton(Icons.Outlined.Remove, "One fewer", { qty = (qty - 1).coerceAtLeast(1) })
                Text("$qty of ${from.quantity}", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(90.dp))
                RoundIconButton(Icons.Outlined.Add, "One more", { qty = (qty + 1).coerceAtMost(from.quantity) })
            }
            Text("Move to", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Column(Modifier.height(240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in dests) {
                    val on = d.id == dest
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) c.surface2 else c.surface)
                            .border(2.dp, if (on) c.primary else c.border, RoundedCornerShape(16.dp))
                            .clickable { dest = d.id }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(22.dp).border(2.dp, c.primary, CircleShape).padding(4.dp).background(if (on) c.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape))
                        Column {
                            Text(d.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            Muted(d.kindLabel(), size = 13)
                        }
                    }
                }
            }
            if (destLoc?.kind == LocationKind.TEMP_SET) {
                Muted("These copies remember they came from ${row.place?.name}. Dissolving the temp set sends them back.", size = 14)
            }
            val n = qty.coerceAtMost(from.quantity)
            BigButton(
                "Move ${plural(n, "card")} to ${destLoc?.name ?: ""}", { dest?.let { onMove(n, it) } },
                style = confirmStyle(), enabled = dest != null, modifier = Modifier.fillMaxWidth(), height = if (isCompact()) 56.dp else 60.dp,
            )
        }
    }
}

@Composable
private fun AddCopyDialog(locations: List<Location>, onDismiss: () -> Unit, onAdd: (Boolean, Long) -> Unit) {
    val c = Ink.colors
    var foil by remember { mutableStateOf(false) }
    var dest by remember { mutableStateOf(GENERAL_ID) }
    SheetDialog(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SheetTitle("Add a copy")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                io.github.jpsutton.inkstash.ui.PillChip("Normal", !foil, { foil = false })
                io.github.jpsutton.inkstash.ui.PillChip("Foil", foil, { foil = true })
            }
            Text("Put it in", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in locations) io.github.jpsutton.inkstash.ui.PillChip(l.name, l.id == dest, { dest = l.id })
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Cancel", onDismiss, style = cancelStyle(), modifier = Modifier.weight(1f))
                BigButton("Add", { onAdd(foil, dest) }, style = confirmStyle(), icon = Icons.Outlined.Check, modifier = Modifier.weight(1f))
            }
        }
    }
}
