// The words of the remarks (commentary.ts), as the app's Remarks.kt.
import type { Remark } from './commentary';
import { t, ta } from './i18n.svelte';

export function remarkText(r: Remark): string {
  const cats = ta('category_names');
  switch (r.kind) {
    case 'same': return r.loss <= 0 ? t('remark_same_best') : t('remark_same_close');
    case 'mistake': {
      const names = r.categories.length === 2 && r.categories[0] === r.categories[1]
        ? t('remark_times_two', cats[r.categories[0]]) : r.categories.map((c) => cats[c]).join('、');
      const what = {
        MISSED: () => t('remark_missed', names),
        SWAPPED: () => t('remark_swapped', names, r.replaced.map((c) => cats[c]).join('、')),
        BROKE: () => t('remark_broke', names),
        PAIR_TO_FRONT: () => t('remark_pair_to_front'),
        PAIR_OUT_OF_FRONT: () => t('remark_pair_out_of_front'),
        STRONGER_FRONT: () => t('remark_stronger_front'),
        DETAIL: () => t('remark_detail'),
        OTHER: () => t('remark_other'),
      }[r.what]();
      const vs = r.vsMine === 'BOTH' ? t('remark_vs_mine_both') : r.vsMine === 'ATTACK' ? t('remark_vs_mine_attack')
        : r.vsMine === 'DEFENSE' ? t('remark_vs_mine_defense') : '';
      return t('remark_mistake', what) + vs;
    }
    case 'only': return t('remark_only');
    case 'close': return t('remark_close');
    case 'style': {
      const row = ta('row_names')[r.row];
      return r.style === 'BOTH' ? t('remark_both') : r.style === 'ATTACK' ? t('remark_attack', row) : t('remark_defense', row);
    }
    case 'rows': return r.rows === 'BOTH' ? t('remark_rows_both') : r.rows === 'ROWS' ? t('remark_rows') : t('remark_scoops_first');
    case 'strong': return t('remark_strong');
    case 'weak': return t('remark_weak');
    case 'homeRun': return t('remark_home_run');
  }
}
