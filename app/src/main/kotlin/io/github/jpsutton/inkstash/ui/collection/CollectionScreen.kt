package io.github.jpsutton.inkstash.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jpsutton.inkstash.data.OwnedCard
import io.github.jpsutton.inkstash.ui.ArtSize
import io.github.jpsutton.inkstash.ui.CardArt
import io.github.jpsutton.inkstash.ui.CostGem
import io.github.jpsutton.inkstash.ui.Ink
import io.github.jpsutton.inkstash.ui.InkColor
import io.github.jpsutton.inkstash.ui.Muted
import io.github.jpsutton.inkstash.ui.Nunito
import io.github.jpsutton.inkstash.ui.PillChip
import io.github.jpsutton.inkstash.ui.Title
import io.github.jpsutton.inkstash.ui.plural

@Composable
fun CollectionScreen(vm: CollectionViewModel, onCard: (String) -> Unit, onScan: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = Ink.colors
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Title("My collection")
                    Muted("${plural(s.totalCopies, "card")} · ${s.distinct} different", size = 16)
                }
                LocationPicker(s, vm::setLocation)
                SearchField(s.query, vm::setQuery)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip("All inks", s.ink == null, { vm.setInk(null) })
                for (ink in InkColor.entries) PillChip(ink.label, s.ink == ink, { vm.setInk(ink) }, dot = ink.color)
                Box(Modifier.padding(horizontal = 6.dp).width(2.dp).height(28.dp).background(c.border).align(Alignment.CenterVertically))
                FilterMenu("Set", s.setFilter, s.setOptions, vm::setSet)
                FilterMenu("Rarity", s.rarity, s.rarityOptions.map { it to it }, vm::setRarity)
                FilterMenu("Type", s.type, s.typeOptions.map { it to it }, vm::setType)
            }
            if (s.items.isEmpty()) EmptyState(s.hasAny, onScan)
            else LazyVerticalGrid(
                GridCells.Adaptive(150.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
            ) {
                items(s.items, key = { it.card.id }) { CardTile(it) { onCard(it.card.id) } }
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(32.dp)
                .shadow(8.dp, RoundedCornerShape(22.dp), spotColor = c.shadow)
                .clip(RoundedCornerShape(22.dp)).background(c.accent).clickable(onClick = onScan)
                .height(64.dp).padding(start = 22.dp, end = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.CameraAlt, null, tint = c.onAccent, modifier = Modifier.size(26.dp))
            Text("Scan cards", color = c.onAccent, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun EmptyState(hasAny: Boolean, onScan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(bottom = 120.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(if (hasAny) "No cards match" else "No cards yet", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink.colors.text)
        Muted(if (hasAny) "Try a different filter." else "Tap Scan cards to add your first ones.", size = 17)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = Ink.colors
    Row(
        Modifier.width(340.dp).height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.surface)
            .border(2.dp, c.border, RoundedCornerShape(26.dp)).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, null, tint = c.muted, modifier = Modifier.size(22.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Find a card by name", color = c.muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            BasicTextField(
                value, onChange, singleLine = true, cursorBrush = SolidColor(c.text),
                textStyle = TextStyle(color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Nunito),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LocationPicker(s: CollectionState, onPick: (Long) -> Unit) {
    val c = Ink.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.surface)
                .border(2.dp, c.border, RoundedCornerShape(26.dp)).clickable { open = true }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Inventory2, null, tint = c.text, modifier = Modifier.size(20.dp))
            Text("In: ${s.locationName}", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.text, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("Everywhere") }, onClick = { onPick(-1); open = false })
            for (l in s.locations) DropdownMenuItem(text = { Text(l.name) }, onClick = { onPick(l.id); open = false })
        }
    }
}

/** A "Set / Rarity / Type" chip that opens a menu; picking the chosen entry again clears it. */
@Composable
private fun FilterMenu(label: String, selected: String?, options: List<Pair<String, String>>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shown = options.firstOrNull { it.first == selected }?.second
    Box {
        PillChip(shown ?: label, selected != null, { open = true }, trailing = {
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = if (selected != null) Ink.colors.onPrimary else Ink.colors.text, modifier = Modifier.size(16.dp))
        })
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("Any") }, onClick = { onPick(null); open = false })
            for ((value, text) in options) DropdownMenuItem(text = { Text(text) }, onClick = { onPick(value); open = false })
        }
    }
}

@Composable
private fun CardTile(owned: OwnedCard, onClick: () -> Unit) {
    val c = Ink.colors
    val card = owned.card
    val ink = InkColor.of(card.ink)
    Column(
        Modifier.fillMaxWidth().aspectRatio(5f / 7f).clip(RoundedCornerShape(14.dp))
            .background(c.surface).border(BorderStroke(3.dp, ink.color), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CardArt(card, Modifier.fillMaxSize(), ArtSize.Small)
            CostGem(card.cost, ink, 34.dp, Modifier.align(Alignment.TopStart).padding(8.dp))
            Column(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("×${owned.qty}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(13.dp)).background(Color(0xFF1B1A3A)).padding(horizontal = 10.dp, vertical = 3.dp))
                if (owned.foilQty > 0) Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(c.warnBg).border(1.dp, c.accent, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = c.accent, modifier = Modifier.size(12.dp))
                    Text("Foil", color = c.warnFg, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(c.surface).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 9.dp)) {
            Text(card.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.version, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
