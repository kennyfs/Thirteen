package io.github.kennyfs.thirteen.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.kennyfs.thirteen.Glossary
import io.github.kennyfs.thirteen.R
import kotlinx.coroutines.launch

/** Whether this language has the glossary (res/values-xx/bools.xml). */
@Composable
fun hasGlossary(): Boolean = booleanResource(R.bool.show_glossary)

/** The glossary's links in the app's language (its glossary_p_<id> arrays). */
@Composable
private fun glossaryLinker(): Glossary.Linker {
  val res = LocalResources.current
  val locale = res.configuration.locales[0]
  return remember(res, locale) { Glossary.Linker(Glossary.ENTRIES.associate { it.id to res.getStringArray(it.phrases).toList() }) }
}

/**
 * `text` with the glossary's terms as links that call `onTerm` (none in a language without the glossary). `beforeId`: a
 * definition's own, to link only the terms before it; `once`: each term at its first appearance only.
 */
@Composable
fun linkedGlossaryText(text: String, onTerm: (String) -> Unit, beforeId: String? = null, linkColor: Color = MaterialTheme.colorScheme.primary,
  once: Boolean = false): AnnotatedString {
  val on = hasGlossary()
  val linker = glossaryLinker()
  val parts = remember(text, on, linker, beforeId, once) {
    val all = if (on) linker.link(text, beforeId) else listOf(io.github.kennyfs.thirteen.TermPart(text))
    if (!once) all else {
      val seen = HashSet<String>()
      all.map { p -> if (p.termId == null || seen.add(p.termId)) p else p.copy(termId = null) }
    }
  }
  val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
  return remember(parts, linkColor) {
    buildAnnotatedString {
      for (p in parts) {
        val id = p.termId
        if (id != null) withLink(LinkAnnotation.Clickable(tag = id, styles = style) { onTerm(id) }) { append(p.text) } else append(p.text)
      }
    }
  }
}

/**
 * Words with the glossary's terms linked, each at its first appearance only, so that a paragraph is not all
 * underlines; a tap opens the glossary at the term. The web page's Linked.svelte.
 */
@Composable
fun LinkedText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified,
  fontWeight: FontWeight? = null) {
  var term by rememberSaveable { mutableStateOf<String?>(null) }
  Text(linkedGlossaryText(text, onTerm = { term = it }, once = true), modifier, color = color, style = style, fontWeight = fontWeight)
  term?.let { GlossaryDialog(focusId = it, onDismiss = { term = null }) }
}

/** The glossary in a dialog, at `focusId` (scrolled to and marked) when a term's link opened it. */
@Composable
fun GlossaryDialog(focusId: String?, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
    modifier = Modifier.padding(horizontal = 12.dp).widthIn(max = 560.dp).testTag("glossary_dialog"),
    title = { Text(stringResource(R.string.glossary_title)) },
    text = { GlossaryList(focusId, Modifier.fillMaxWidth().heightIn(max = 460.dp)) },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
  )
}

/** Each term and its definition, whose earlier terms are links (each at its first appearance) that jump up to theirs. */
@Composable
private fun GlossaryList(focusId: String?, modifier: Modifier) {
  val cs = MaterialTheme.colorScheme
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  var activeId by remember(focusId) { mutableStateOf(focusId) }
  fun jumpTo(id: String) {
    activeId = id
    val i = Glossary.INDEX_BY_ID[id] ?: return
    scope.launch { listState.animateScrollToItem(i) }
  }
  LaunchedEffect(focusId) { Glossary.INDEX_BY_ID[focusId]?.let { listState.scrollToItem(it) } }
  LazyColumn(modifier, state = listState, contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    itemsIndexed(Glossary.ENTRIES, key = { _, e -> e.id }) { i, entry ->
      val focused = activeId == entry.id
      val fg = if (focused) cs.onPrimaryContainer else cs.onSurface
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (focused) cs.primaryContainer else cs.surfaceContainerLow,
        border = if (focused) BorderStroke(1.dp, cs.primary) else null,
        modifier = Modifier.fillMaxWidth().testTag("glossary_${entry.id}"),
      ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = if (focused) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant)
            Text(stringResource(entry.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = fg)
          }
          val desc = linkedGlossaryText(stringResource(entry.desc), onTerm = ::jumpTo, beforeId = entry.id,
            linkColor = if (focused) cs.onPrimaryContainer else cs.primary, once = true)
          Text(desc, style = MaterialTheme.typography.bodyMedium, color = fg)
        }
      }
    }
  }
}
