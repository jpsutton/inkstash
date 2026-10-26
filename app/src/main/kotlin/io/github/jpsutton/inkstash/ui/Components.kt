package io.github.jpsutton.inkstash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Collection("collection", "Collection", Icons.Outlined.GridView),
    Scan("scan", "Scan", Icons.Outlined.CameraAlt),
    Places("places", "Places", Icons.Outlined.Inventory2),
    Settings("settings", "Settings", Icons.Outlined.Tune),
}

/**
 * True on phones (smallest screen width under 600 dp), which get the phone layouts: a bottom bar
 * instead of the rail, single-column screens and bottom sheets instead of centred dialogs.
 */
val LocalCompact = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun isCompact(): Boolean = LocalCompact.current

/** The 96 dp navigation rail from the mockups. */
@Composable
fun NavRail(selected: Tab?, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    Column(
        modifier.width(96.dp).fillMaxHeight().background(c.rail).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.rail2).padding(bottom = 0.dp), Alignment.Center) {
            Icon(Icons.Outlined.WaterDrop, "inkstash", tint = c.accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(4.dp))
        for (tab in Tab.entries) {
            if (tab == Tab.Settings) Spacer(Modifier.weight(1f))
            val on = tab == selected
            Column(
                Modifier.width(84.dp).clickable(role = Role.Tab) { onTab(tab) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.width(60.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) c.accent else Color.Transparent),
                    Alignment.Center,
                ) { Icon(tab.icon, null, tint = if (on) c.onAccent else c.railText, modifier = Modifier.size(24.dp)) }
                Text(tab.label, color = if (on) Color.White else c.railText, fontSize = 13.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold)
            }
        }
    }
}

/** The phone's bottom navigation bar: the same four tabs as the rail, 80 dp plus the system navigation bar. */
@Composable
fun BottomNav(selected: Tab?, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    Row(
        modifier.fillMaxWidth().background(c.rail).windowInsetsPadding(WindowInsets.navigationBars).height(80.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in Tab.entries) {
            val on = tab == selected
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Tab) { onTab(tab) }.semantics { this.selected = on },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Box(
                    Modifier.width(64.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) c.accent else Color.Transparent),
                    Alignment.Center,
                ) { Icon(tab.icon, null, tint = if (on) c.onAccent else c.railText, modifier = Modifier.size(22.dp)) }
                Text(tab.label, color = if (on) Color.White else c.railText, fontSize = 12.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold)
            }
        }
    }
}

/** A pill-shaped filter/choice chip, 44 dp tall. */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Ink.colors
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (selected) c.primary else c.surface)
            .border(2.dp, if (selected) c.primary else c.border, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (selected) contentDescription = "$label, selected" }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        if (dot != null) Box(Modifier.size(14.dp).background(dot, CircleShape).border(2.dp, c.surface, CircleShape))
        Text(label, color = if (selected) c.onPrimary else c.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        trailing?.invoke()
    }
}

/** A big rounded button. `primary` = accent fill, else outlined. */
@Composable
fun BigButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ButtonStyle = ButtonStyle.Outlined,
    height: Dp = 56.dp,
    enabled: Boolean = true,
) {
    val c = Ink.colors
    val (bg, fg, border) = when (style) {
        ButtonStyle.Accent -> Triple(c.accent, c.onAccent, c.accent)
        ButtonStyle.Primary -> Triple(c.primary, c.onPrimary, c.primary)
        ButtonStyle.Danger -> Triple(c.danger, c.onDanger, c.danger)
        ButtonStyle.Outlined -> Triple(c.surface, c.text, c.border)
        ButtonStyle.Quiet -> Triple(c.surface2, c.text, c.surface2)
    }
    Row(
        modifier.height(height).clip(RoundedCornerShape(height / 2)).background(bg)
            .border(2.dp, border, RoundedCornerShape(height / 2))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fg.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(22.dp))
        Text(label, color = fg.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

enum class ButtonStyle { Accent, Primary, Danger, Outlined, Quiet }

/** Cancel / "keep it" button style in a sheet: soft grey on phones, outlined on tablets. */
@Composable
@ReadOnlyComposable
fun cancelStyle() = if (isCompact()) ButtonStyle.Quiet else ButtonStyle.Outlined

/** Confirm button style in a sheet: amber on phones, dark on tablets. */
@Composable
@ReadOnlyComposable
fun confirmStyle() = if (isCompact()) ButtonStyle.Accent else ButtonStyle.Primary

/** Round icon button, 52 dp (big touch target). */
@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp, tint: Color? = null) {
    val c = Ink.colors
    Box(
        modifier.size(size).clip(CircleShape).background(c.surface).border(2.dp, c.border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        Alignment.Center,
    ) { Icon(icon, null, tint = tint ?: c.text, modifier = Modifier.size(size / 2.2f)) }
}

@Composable
fun Badge(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, border: Color = Color.Transparent) {
    Text(
        text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun Card3(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Ink.colors
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(c.surface).border(2.dp, c.border, RoundedCornerShape(20.dp))) { content() }
}

/**
 * Modal sheet used by move / new place / dissolve. A centred dialog on tablets, a bottom sheet
 * on phones.
 */
@Composable
fun SheetDialog(onDismiss: () -> Unit, width: Dp = 520.dp, content: @Composable () -> Unit) {
    val c = Ink.colors
    if (isCompact()) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            BoxWithConstraints(
                Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                Alignment.BottomCenter,
            ) {
                val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                Column(
                    Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.94f).clip(shape).background(c.surface)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .navigationBarsPadding().imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.dash))
                    content()
                }
            }
        }
        return
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.width(width).shadow(24.dp, RoundedCornerShape(28.dp), spotColor = c.shadow)
                .clip(RoundedCornerShape(28.dp)).background(c.surface).border(2.dp, c.border, RoundedCornerShape(28.dp)).padding(28.dp),
        ) { content() }
    }
}

/** Sheet heading: 24 sp on phones, 28 sp on tablets. */
@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = if (isCompact()) 24.sp else 28.sp, color = Ink.colors.text, modifier = modifier)

/** Small round cost gem in the ink's colour. */
@Composable
fun CostGem(cost: Int, ink: InkColor, size: Dp = 34.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(ink.color, CircleShape), Alignment.Center) {
        Text(cost.toString(), color = ink.on, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.53f).sp)
    }
}

@Composable
fun InkDot(ink: InkColor, size: Dp = 12.dp) = Box(Modifier.size(size).background(ink.color, CircleShape))

@Composable
fun Title(text: String, modifier: Modifier = Modifier, size: Int = if (isCompact()) 28 else 34) =
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(fontSize = size.sp, lineHeight = (size * 1.15f).sp), color = Ink.colors.text, modifier = modifier, maxLines = 2, overflow = TextOverflow.Ellipsis)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 15) =
    Text(text, color = Ink.colors.muted, fontSize = size.sp, fontWeight = FontWeight.Bold, modifier = modifier)

/** The screen content with the tablet's side rail, or on phones a bottom bar under the content. */
@Composable
fun ScreenWithRail(selected: Tab?, onTab: (Tab) -> Unit, content: @Composable () -> Unit) {
    if (isCompact()) {
        Column(Modifier.fillMaxSize().background(Ink.colors.bg)) {
            Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) { content() }
            BottomNav(selected, onTab)
        }
        return
    }
    androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().background(Ink.colors.bg)) {
        NavRail(selected, onTab)
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

fun Modifier.card(border: Color, bg: Color, radius: Dp = 20.dp, width: Dp = 2.dp): Modifier =
    clip(RoundedCornerShape(radius)).background(bg).border(BorderStroke(width, border), RoundedCornerShape(radius))

/** A dashed rounded border, for "add" tiles. */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 2.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))),
    )
}
