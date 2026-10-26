package io.github.jpsutton.inkstash.ui.scan

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jpsutton.inkstash.data.Card
import io.github.jpsutton.inkstash.scan.MatchLevel
import io.github.jpsutton.inkstash.ui.ArtSize
import io.github.jpsutton.inkstash.ui.Badge
import io.github.jpsutton.inkstash.ui.BigButton
import io.github.jpsutton.inkstash.ui.ButtonStyle
import io.github.jpsutton.inkstash.ui.CardArt
import io.github.jpsutton.inkstash.ui.CostGem
import io.github.jpsutton.inkstash.ui.Fredoka
import io.github.jpsutton.inkstash.ui.Ink
import io.github.jpsutton.inkstash.ui.InkColor
import io.github.jpsutton.inkstash.ui.Muted
import io.github.jpsutton.inkstash.ui.Nunito
import io.github.jpsutton.inkstash.ui.PillChip
import io.github.jpsutton.inkstash.ui.RoundIconButton
import io.github.jpsutton.inkstash.ui.SheetDialog
import io.github.jpsutton.inkstash.ui.plural

@Composable
fun ReviewScreen(vm: ReviewViewModel, onRetake: () -> Unit, onDone: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = Ink.colors
    var searchFor by remember { mutableStateOf<SearchTarget?>(null) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(26.dp)).clickable { vm.discard(); onRetake() }.height(52.dp).padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = c.text)
                Text("Retake", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f)) {
                Text("We found ${s.rows.size} cards", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = c.text)
                Muted("Check each one, then add them to your collection", size = 16)
            }
            DestinationPicker(s, vm::setDestination)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(s.rows, key = { it.id }) { row ->
                RowCard(row, s.rows.indexOf(row) + 1, vm, onFind = { searchFor = SearchTarget(row.id) })
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.surface).border(1.dp, c.border).padding(horizontal = 32.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BigButton("Add a card we missed", { searchFor = SearchTarget(null) }, icon = Icons.Outlined.Add)
            Text(s.summary, color = c.muted, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            BigButton(
                "Add ${plural(s.totalCards, "card")} to ${s.destination?.name ?: "General"}", { vm.confirm(onDone) },
                icon = Icons.Outlined.Check, style = ButtonStyle.Accent, height = 60.dp, enabled = s.totalCards > 0,
            )
        }
    }

    searchFor?.let { target ->
        SearchDialog(vm, onDismiss = { searchFor = null }) { card -> vm.choose(target.rowId, card); searchFor = null }
    }
}

private data class SearchTarget(val rowId: Int?)

@Composable
private fun DestinationPicker(s: ReviewState, onPick: (Long) -> Unit) {
    val c = Ink.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.surface).border(2.dp, c.border, RoundedCornerShape(26.dp)).clickable { open = true }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Inventory2, null, tint = c.text, modifier = Modifier.size(20.dp))
            Text("Add to: ${s.destination?.name ?: "General"}", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.text, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(open, { open = false }) {
            for (d in s.destinations) DropdownMenuItem(text = { Text(d.name) }, onClick = { onPick(d.id); open = false })
        }
    }
}

@Composable
private fun RowCard(row: ReviewRow, number: Int, vm: ReviewViewModel, onFind: () -> Unit) {
    val c = Ink.colors
    val card = row.card
    val (badgeBg, badgeFg, border) = when {
        card == null -> Triple(c.badBg, c.badFg, c.badFg)
        row.level == MatchLevel.CHECK -> Triple(c.warnBg, c.warnFg, c.accent)
        else -> Triple(c.okBg, c.okFg, c.border)
    }
    val badge = when { card == null -> "Not found"; row.level == MatchLevel.CHECK -> "Check this"; else -> "Sure" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).border(2.dp, border, RoundedCornerShape(20.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("$number", color = c.muted, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(28.dp))
        Box(Modifier.size(70.dp, 98.dp).clip(RoundedCornerShape(8.dp)).background(c.surface2)) {
            row.crop?.let { Image(it.asImageBitmap(), "Crop from your photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = c.muted)
        Box(Modifier.size(70.dp, 98.dp).clip(RoundedCornerShape(8.dp)).background(c.surface2), Alignment.Center) {
            if (card != null) {
                CardArt(card, Modifier.fillMaxSize(), ArtSize.Small)
                CostGem(card.cost, InkColor.of(card.ink), 26.dp, Modifier.align(Alignment.TopStart).padding(4.dp))
            } else Text("?", color = c.badFg, fontFamily = Fredoka, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(card?.fullName ?: "We couldn't read this card", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Muted(row.detail, size = 14)
            if (row.level == MatchLevel.CHECK && row.alternates.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muted("Or is it:", size = 14, modifier = Modifier.align(Alignment.CenterVertically))
                    for (alt in row.alternates) PillChip(alt.fullName, false, { vm.pickAlternate(row.id, alt) })
                }
            }
        }
        Badge(badge, badgeBg, badgeFg, border = border)
        if (card != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (row.foil) c.warnBg else c.surface)
                        .border(2.dp, if (row.foil) c.accent else c.border, RoundedCornerShape(24.dp)).clickable { vm.toggleFoil(row.id) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = if (row.foil) c.accent else c.muted, modifier = Modifier.size(18.dp))
                    Text("Foil", color = if (row.foil) c.warnFg else c.muted, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                RoundIconButton(Icons.Outlined.Remove, "One fewer", { vm.dec(row.id) }, size = 44.dp)
                Text("${row.qty}", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(24.dp))
                RoundIconButton(Icons.Outlined.Add, "One more", { vm.inc(row.id) }, size = 44.dp)
            }
        } else BigButton("Find it", onFind, icon = Icons.Outlined.Search, height = 48.dp)
        RoundIconButton(Icons.Outlined.Close, "Remove row $number", { vm.remove(row.id) }, size = 44.dp)
    }
}

@Composable
private fun SearchDialog(vm: ReviewViewModel, onDismiss: () -> Unit, onPick: (Card) -> Unit) {
    val c = Ink.colors
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Card>()) }
    LaunchedEffect(q) { results = vm.search(q) }
    SheetDialog(onDismiss, width = 640.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Find a card", fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, color = c.text, modifier = Modifier.weight(1f))
                RoundIconButton(Icons.Outlined.Close, "Close", onDismiss, size = 48.dp)
            }
            Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.surface2).border(2.dp, c.border, RoundedCornerShape(26.dp)).padding(horizontal = 18.dp), Alignment.CenterStart) {
                if (q.isEmpty()) Text("Type part of the name", color = c.muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                BasicTextField(q, { q = it }, singleLine = true, cursorBrush = SolidColor(c.text),
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Nunito), modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results, key = { it.id }) { card ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(card) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CostGem(card.cost, InkColor.of(card.ink), 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(card.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Muted("${card.version} · ${card.setName} ${card.collectorNumber}", size = 13)
                        }
                    }
                }
            }
        }
    }
}
