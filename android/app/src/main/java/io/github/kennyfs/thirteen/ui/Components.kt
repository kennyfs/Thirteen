package io.github.kennyfs.thirteen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import io.github.kennyfs.thirteen.R

/** The height of the primary actions (Confirm, Next hand, Analyze). */
val BIG_BUTTON = 56.dp

/** A page's title row: an optional back button, the title, and actions at the end. */
@Composable
fun TitleBar(title: String, onBack: (() -> Unit)? = null, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
  Row(
    modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (onBack == null) 16.dp else 4.dp, end = 12.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (onBack != null)
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
    Text(
      title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1,
      overflow = TextOverflow.Ellipsis, autoSize = shrinking(MaterialTheme.typography.titleLarge),
    )
    actions()
  }
}

/** A group's heading in a list of settings; with `help`, an (i) after it that shows it. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, help: String? = null) {
  val style = MaterialTheme.typography.titleSmall
  if (help == null) Text(text, modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp), style = style, color = MaterialTheme.colorScheme.primary)
  // The button's 40 dp takes the place of the padding over and under the text.
  else Row(modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(text, Modifier.weight(1f, fill = false), style = style, color = MaterialTheme.colorScheme.primary)
    InfoButton(help, text, small = true)
  }
}

/** A row of a settings list: a title, its current value under it, and an arrow when it opens a page. */
@Composable
fun SettingRow(
  title: String, value: String? = null, modifier: Modifier = Modifier, arrow: Boolean = true, enabled: Boolean = true,
  trailing: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null,
) {
  Row(
    modifier.fillMaxWidth().heightIn(min = 64.dp)
      .let { if (onClick != null) it.clickable(enabled = enabled, onClick = onClick) else it }
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
      if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    trailing?.invoke()
    if (onClick != null && arrow) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/**
 * Two to four choices side by side, one of them on: a connected group of 48 dp buttons. `onFelt` draws it for the
 * table (light on the felt) instead of the page.
 */
@Composable
fun <T> ToggleGroup(
  options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier,
  colors: ToggleColors = ToggleColors.page(), style: TextStyle = MaterialTheme.typography.labelLarge,
) {
  Row(modifier.height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    for ((i, o) in options.withIndex()) {
      val on = o.first == selected
      val shape = RoundedCornerShape(
        topStart = if (i == 0) 24.dp else 6.dp, bottomStart = if (i == 0) 24.dp else 6.dp,
        topEnd = if (i == options.lastIndex) 24.dp else 6.dp, bottomEnd = if (i == options.lastIndex) 24.dp else 6.dp,
      )
      Box(
        Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).clip(shape).background(if (on) colors.onContainer else colors.offContainer)
          .selectable(on, role = Role.RadioButton) { onSelect(o.first) }.padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          o.second, color = if (on) colors.onContent else colors.offContent, style = style,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

data class ToggleColors(val onContainer: Color, val onContent: Color, val offContainer: Color, val offContent: Color) {
  companion object {
    @Composable
    fun page() = MaterialTheme.colorScheme.let { ToggleColors(it.primary, it.onPrimary, it.surfaceContainerHighest, it.onSurface) }
  }
}

/** A labelled value picked from a selection dialog: the whole row is the touch target. */
@Composable
fun <T> SelectRow(
  title: String,
  options: List<Pair<T, String>>,
  value: T,
  onPick: (T) -> Unit,
  modifier: Modifier = Modifier,
  icons: Map<T, ImageVector> = emptyMap(),
  enabled: Boolean = true,
) {
  var open by remember { mutableStateOf(false) }
  val current = options.firstOrNull { it.first == value }?.second ?: options.firstOrNull()?.second
  SettingRow(
    title, current, arrow = false, onClick = { open = true }, modifier = modifier, enabled = enabled,
    trailing = { Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
  )
  if (open) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
      onDismissRequest = { open = false },
      shape = RoundedCornerShape(28.dp),
      containerColor = colors.surfaceContainerHigh,
      title = { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
      text = {
        Column(
          Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          for ((v, text) in options) {
            val isSelected = v == value
            val icon = icons[v]
            Surface(
              onClick = { onPick(v); open = false },
              shape = RoundedCornerShape(16.dp),
              color = if (isSelected) colors.secondaryContainer else Color.Transparent,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                if (icon != null) {
                  Icon(
                    icon, null,
                    tint = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                  )
                }
                Text(
                  text, Modifier.weight(1f),
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  color = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
                )
                RadioButton(selected = isSelected, onClick = null)
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
      },
    )
  }
}

/** A compact button that opens a menu of choices, for title bars: the current choice and a triangle. */
@Composable
fun <T> MenuChip(label: String, options: List<Pair<T, String>>, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
  var open by remember { mutableStateOf(false) }
  Box(modifier) {
    Chip(label, onClick = { open = true }, trailingMenu = true)
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      for ((v, text) in options)
        DropdownMenuItem(text = { Text(text, style = MaterialTheme.typography.bodyLarge) }, onClick = { onPick(v); open = false }, modifier = Modifier.heightIn(min = 52.dp))
    }
  }
}

/** An outlined chip, 40 dp tall within a 48 dp touch target. */
@Composable
fun Chip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailingMenu: Boolean = false) {
  Surface(
    onClick = onClick, modifier = modifier.heightIn(min = 48.dp).padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Row(Modifier.padding(start = 12.dp, end = if (trailingMenu) 6.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
      if (trailingMenu) Icon(Icons.Filled.ArrowDropDown, null)
    }
  }
}

/** A number with − and + buttons, for the rules. Held down, a button repeats, faster and faster. */
@Composable
fun NumberStepper(value: Double, onChange: (Double) -> Unit, step: Double = 1.0, min: Double = 0.0, max: Double = 100.0, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    val current by rememberUpdatedState(value)
    RepeatingButton(stringResource(R.string.decrease), Icons.Filled.Remove, value > min) { onChange((current - step).coerceAtLeast(min)) }
    Text(text, Modifier.widthIn(min = 44.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    RepeatingButton(stringResource(R.string.increase), Icons.Filled.Add, value < max) { onChange((current + step).coerceAtMost(max)) }
  }
}

/**
 * A button that acts once on a tap and, held down, again and again: after 400 ms, then at shorter and shorter
 * intervals, each shortened by more than the last.
 */
@Composable
private fun RepeatingButton(description: String, icon: ImageVector, enabled: Boolean, onStep: () -> Unit) {
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  val step by rememberUpdatedState(onStep)
  val on by rememberUpdatedState(enabled)
  var repeated by remember { mutableStateOf(false) }
  LaunchedEffect(pressed) {
    if (!pressed) return@LaunchedEffect
    repeated = false
    delay(400)
    var interval = 260.0
    var shrink = 0.92
    while (on) {
      step()
      repeated = true
      delay(interval.toLong())
      interval = (interval * shrink).coerceAtLeast(20.0)
      shrink = (shrink - 0.01).coerceAtLeast(0.6)
    }
  }
  FilledTonalIconButton(
    onClick = { if (!repeated) step(); repeated = false }, enabled = enabled, modifier = Modifier.size(48.dp), interactionSource = source,
  ) { Icon(icon, description) }
}

/**
 * An (i) button that shows `text` in a dialog, under `title` (the setting's name) when there is one; the text's line
 * breaks stay. `small`: a setting's own, after its name (a 20 dp icon in a 40 dp button, in the quiet color).
 */
@Composable
fun InfoButton(text: String, title: String? = null, tint: Color = MaterialTheme.colorScheme.primary, small: Boolean = false) {
  var open by remember { mutableStateOf(false) }
  val description = if (title != null) stringResource(R.string.help_about, title) else stringResource(R.string.info)
  IconButton(onClick = { open = true }, Modifier.then(if (small) Modifier.size(40.dp) else Modifier).semantics { contentDescription = description }) {
    Icon(Icons.Outlined.Info, null, if (small) Modifier.size(20.dp) else Modifier, tint = if (small) MaterialTheme.colorScheme.onSurfaceVariant else tint)
  }
  if (open)
    AlertDialog(
      onDismissRequest = { open = false },
      confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
      title = title?.let { { Text(it) } },
      // Its lines are short definitions: Japanese breaks them between phrases, as titles, not inside a word.
      text = {
        val style = MaterialTheme.typography.bodyLarge
        Text(text, Modifier.verticalScroll(rememberScrollState()), style = style.copy(lineBreak = style.lineBreak.copy(wordBreak = LineBreak.WordBreak.Phrase)))
      },
    )
}

/** A small rounded label: a rank ("Best"), a row's result against the player (+1, −1). */
@Composable
fun Badge(text: String, container: Color, content: Color, modifier: Modifier = Modifier, small: Boolean = false) {
  Text(
    text, modifier.background(container, RoundedCornerShape(if (small) 6.dp else 8.dp)).padding(horizontal = if (small) 5.dp else 7.dp, vertical = 1.dp),
    color = content, style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, maxLines = 1,
  )
}

/** A circle with a person's initial, for lists of players. */
@Composable
fun Avatar(name: String, size: Dp = 40.dp) {
  Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
    Text(name.trim().take(1).ifEmpty { "?" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
  }
}

/** Width of a two-pane split, or null when the window is too narrow for two. */
fun twoPane(width: Dp, min: Dp = 720.dp): Boolean = width >= min

/** Keeps a column of text and controls to a readable width on large screens. */
fun Modifier.readable(max: Dp = 720.dp) = this.widthIn(max = max)

/**
 * One line of text, or `short` in its place when the text does not fit the width it has (a large font, a narrow
 * button): never cut off. Screen readers get the full text.
 */
@Composable
fun FitText(
  text: String, short: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current,
  fontWeight: FontWeight? = null, color: Color = Color.Unspecified,
) {
  Layout(
    content = {
      Text(text, style = style, fontWeight = fontWeight, color = color, maxLines = 1, softWrap = false)
      Text(short, style = style, fontWeight = fontWeight, color = color, maxLines = 1, softWrap = false)
    },
    modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
  ) { measurables, constraints ->
    val long = measurables[0].measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val p = if (long.width <= constraints.maxWidth) long else measurables[1].measure(constraints.copy(minWidth = 0))
    val w = p.width.coerceIn(constraints.minWidth, constraints.maxWidth)
    val h = p.height.coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(w, h) { p.placeRelative((w - p.width) / 2, (h - p.height) / 2) }
  }
}
