package io.github.kennyfs.thirteen.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kennyfs.thirteen.Commentary
import io.github.kennyfs.thirteen.Commentary.MistakeKind
import io.github.kennyfs.thirteen.Commentary.RowsKind
import io.github.kennyfs.thirteen.Commentary.StyleKind
import io.github.kennyfs.thirteen.R

/** The remarks of Commentary.remarks: the player's in its own paragraph (red for a mistake), then the best one's. */
@Composable
fun Remarks(remarks: List<Commentary.Remark>, modifier: Modifier = Modifier) {
  if (remarks.isEmpty()) return
  val mine = remarks.firstOrNull { it is Commentary.Same || it is Commentary.Mistake }
  val style = MaterialTheme.typography.bodySmall
  Column(modifier.padding(vertical = 2.dp)) {
    if (mine != null)
      Text(remarkText(mine), style = style, color = if (mine is Commentary.Mistake) BAD else GOOD)
    Text((remarks - setOfNotNull(mine)).map { remarkText(it) }.joinToString(stringResource(R.string.remark_sep)), style = style)
  }
}

@Composable
fun shapeName(shape: Commentary.Shape): String = stringArrayResource(R.array.shape_names)[shape.ordinal]


@Composable
private fun remarkText(r: Commentary.Remark): String {
  val cats = stringArrayResource(R.array.category_names)
  return when (r) {
    is Commentary.Same -> if (r.loss <= 0.0) stringResource(R.string.remark_same_best) else stringResource(R.string.remark_same_close)
    is Commentary.Mistake -> {
      val names = if (r.categories.size == 2 && r.categories[0] == r.categories[1]) stringResource(R.string.remark_times_two, cats[r.categories[0]])
      else r.categories.joinToString("、") { cats[it] }
      val what = when (r.kind) {
        MistakeKind.MISSED -> stringResource(R.string.remark_missed, names)
        MistakeKind.SWAPPED -> stringResource(R.string.remark_swapped, names, r.replaced.joinToString("、") { cats[it] })
        MistakeKind.BROKE -> stringResource(R.string.remark_broke, names)
        MistakeKind.PAIR_TO_FRONT -> stringResource(R.string.remark_pair_to_front)
        MistakeKind.PAIR_OUT_OF_FRONT -> stringResource(R.string.remark_pair_out_of_front)
        MistakeKind.STRONGER_FRONT -> stringResource(R.string.remark_stronger_front)
        MistakeKind.DETAIL -> stringResource(R.string.remark_detail)
        MistakeKind.OTHER -> stringResource(R.string.remark_other)
      }
      val vs = when (r.vsMine) {
        StyleKind.BOTH -> stringResource(R.string.remark_vs_mine_both)
        StyleKind.ATTACK -> stringResource(R.string.remark_vs_mine_attack)
        StyleKind.DEFENSE -> stringResource(R.string.remark_vs_mine_defense)
        null -> ""
      }
      stringResource(R.string.remark_mistake, what) + vs
    }
    Commentary.OnlyChoice -> stringResource(R.string.remark_only)
    is Commentary.Close -> stringResource(R.string.remark_close)
    is Commentary.Style -> {
      val row = stringArrayResource(R.array.row_names)[r.row]
      when (r.kind) {
        StyleKind.BOTH -> stringResource(R.string.remark_both)
        StyleKind.ATTACK -> stringResource(R.string.remark_attack, row)
        StyleKind.DEFENSE -> stringResource(R.string.remark_defense, row)
      }
    }
    is Commentary.RowsOrScoops -> when (r.kind) {
      RowsKind.BOTH -> stringResource(R.string.remark_rows_both)
      RowsKind.ROWS -> stringResource(R.string.remark_rows)
      RowsKind.SCOOPS -> stringResource(R.string.remark_scoops_first)
    }
    is Commentary.Clear -> stringResource(R.string.remark_clear)
    is Commentary.Strong -> stringResource(R.string.remark_strong)
    is Commentary.Weak -> stringResource(R.string.remark_weak)
    is Commentary.HomeRunChance -> stringResource(R.string.remark_home_run)
  }
}
