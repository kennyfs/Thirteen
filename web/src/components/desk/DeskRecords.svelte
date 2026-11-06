<!--
  A computer's records: the running total's chart and four figures (of the rules chosen) on top; under them the
  practice hands of those rules in a table that sorts by a column's header and filters (won, lost, saved), and beside it
  the hand picked (from the table or the chart) in large, to analyze, play again or save. Saving to a file, resetting
  the scores and clearing are at the top.
-->
<script lang="ts">
  import type { App } from '../../lib/app.svelte';
  import { guessCategory, parseCards, rowLabel, type Rows } from '../../lib/cards';
  import * as engine from '../../lib/engine';
  import type { RowsEval } from '../../lib/engine';
  import { t, ta } from '../../lib/i18n.svelte';
  import * as R from '../../lib/rules';
  import type { Rec } from '../../lib/store';
  import { localTime, signed } from '../../lib/ui.svelte';
  import Dialog from '../Dialog.svelte';
  import Icon from '../Icon.svelte';
  import Menu from '../Menu.svelte';
  import RowCards from '../RowCards.svelte';
  import TitleBar from '../TitleBar.svelte';
  import DeskRows from './DeskRows.svelte';
  import DeskScoreChart from './DeskScoreChart.svelte';

  let { app }: { app: App } = $props();
  let confirmClear = $state(false);
  let confirmReset = $state(false);
  let chosen = $state('');
  type Filter = 'all' | 'won' | 'lost' | 'saved';
  let filter = $state<Filter>('all');
  type Sort = 'time' | 'score' | 'took';
  let sort = $state<Sort>('time');
  let ascending = $state(false);
  let shown = $state(100);
  let pickedId = $state<string | null>(null);

  // As the phone's page: the practice hands, newest first (not the battles', nor hands played again).
  const recs = $derived.by(() => { void app.recordCount; return app.records.all().filter((r) => r.label !== 'replay').reverse(); });
  const practice = $derived(recs.filter((r) => r.label !== 'battle' && r.label !== 'puzzle'));
  const noC = (s: string) => (s || 'family').split(',').filter((p) => !p.startsWith('c=')).join(',');
  const groups = $derived.by(() => {
    const g = new Map<string, Rec[]>();
    for (const r of [...practice].reverse()) if (typeof r.result === 'number') {
      const k = noC(r.rules);
      if (!g.has(k)) g.set(k, []);
      g.get(k)!.push(r);
    }
    return g;
  });
  const key = $derived.by(() => {
    if (groups.has(chosen)) return chosen;
    const cur = noC(app.gameRules);
    if (groups.has(cur)) return cur;
    let best = '';
    for (const [k, v] of groups) if (!best || v.length > groups.get(best)!.length) best = k;
    return best;
  });
  const label = (spec: string) => app.rulesName(spec.includes('=') ? R.values(spec) : app.presetValues[spec] ?? {});
  /** The chart's hands, oldest first, and their running totals. */
  const list = $derived(groups.get(key) ?? []);
  const totals = $derived.by(() => { let s = 0; return list.map((r) => (s += r.result)); });
  const total = $derived(totals.length ? totals[totals.length - 1] : 0);
  const savedCount = $derived.by(() => { void app.favoriteIds; return list.filter((r) => app.favoriteIds.has(r.id)).length; });

  const filtered = $derived.by(() => {
    void app.favoriteIds;
    const keep = (r: Rec) => filter === 'all' || (filter === 'won' ? r.result > 0 : filter === 'lost' ? r.result < 0 : app.favoriteIds.has(r.id));
    const by = (r: Rec) => (sort === 'time' ? Date.parse(r.timestamp) || 0 : sort === 'score' ? r.result : r.time_ms ?? 0);
    const out = list.filter(keep);
    out.sort((a, b) => (by(a) - by(b)) * (ascending ? 1 : -1));
    return out;
  });
  const visible = $derived(filtered.slice(0, shown));
  const picked = $derived(list.find((r) => r.id === pickedId) ?? null);

  // The rows' hands and special rows, from the engine, for the hands shown (one at a time, kept).
  const rowsOf = (r: Rec): Rows => ['front', 'middle', 'back'].map((k) => parseCards(r[k] ?? ''));
  let evals = $state<Record<string, RowsEval>>({});
  let busy = false;
  $effect(() => {
    const need = [...visible, ...(picked ? [picked] : [])].filter((r) => !evals[r.id]);
    if (need.length === 0 || busy) return;
    busy = true;
    (async () => {
      const got: Record<string, RowsEval> = {};
      for (const r of need.slice(0, 40)) {
        try { got[r.id] = await engine.rows(rowsOf(r), r.rules || 'family'); } catch { /* an old record's rules: its guessed hands stay */ }
      }
      busy = false;
      evals = { ...evals, ...got };
    })();
  });
  const wheel = (r: Rec) => (R.values(r.rules || 'family').wheel ?? 0);
  function hands(r: Rec): string[] {
    const e = evals[r.id];
    if (e && e.categories.every((c) => c > 0)) return e.keys.map((k) => rowLabel(k, ta('row_labels'), wheel(r)));
    return rowsOf(r).map((cards) => ta('category_names')[guessCategory(cards)] ?? '');
  }
  const royalty = (r: Rec) => { const e = evals[r.id]; return e && e.valid ? e.royalties.reduce((a, b) => a + b, 0) : null; };
  const took = (ms: number | undefined) => (ms == null ? '' : ms >= 60000 ? `${Math.floor(ms / 60000)}:${String(Math.round((ms % 60000) / 1000)).padStart(2, '0')}` : `${(ms / 1000).toFixed(0)}s`);

  function pick(r: Rec) { pickedId = r.id; }
  function sortBy(s: Sort) { if (sort === s) ascending = !ascending; else { sort = s; ascending = false; } }
  function pickIndex(i: number) {
    const r = list[i];
    if (!r) return;
    pickedId = r.id;
    filter = 'all';
    const at = filtered.findIndex((x) => x.id === r.id);
    if (at >= shown) shown = at + 20;
    requestAnimationFrame(() => document.querySelector(`.records tr[data-id="${r.id}"]`)?.scrollIntoView({ block: 'nearest' }));
  }
  function onKey(e: KeyboardEvent) {
    if (document.querySelector('dialog[open]') || (e.target as HTMLElement | null)?.closest('input, textarea, select, .menu')) return;
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    const i = visible.findIndex((r) => r.id === pickedId);
    const next = visible[Math.max(0, Math.min(visible.length - 1, i + (e.key === 'ArrowDown' ? 1 : -1)))];
    if (!next) return;
    e.preventDefault();
    pickedId = next.id;
    document.querySelector(`.records tr[data-id="${next.id}"]`)?.scrollIntoView({ block: 'nearest' });
  }

  function download() {
    const blob = new Blob([app.records.text()], { type: 'application/x-ndjson' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `thirteen-records-${new Date().toISOString().slice(0, 10)}.jsonl`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }
  const FILTERS: [Filter, string][] = [['all', 'f_all'], ['won', 'f_wins'], ['lost', 'f_losses'], ['saved', 'f_saved']];
</script>

<svelte:window onkeydown={onKey} />

{#snippet sortHead(s: Sort, name: string, cls: string)}
  <th class={cls} aria-sort={sort === s ? (ascending ? 'ascending' : 'descending') : 'none'}>
    <button class="sorter" title={t('sort_by', name)} onclick={() => sortBy(s)}>{name}{#if sort === s}<span class="arrow">{ascending ? '▲' : '▼'}</span>{/if}</button>
  </th>
{/snippet}

<div class="page records">
  <TitleBar title={t('records_title')}>
    <span class="body-small dim">{t('n_recorded', app.recordCount)}</span>
    <button class="btn outlined" disabled={app.recordCount === 0} onclick={download}><Icon name="download" />{t('export')}</button>
    <button class="btn text" onclick={() => (confirmReset = true)}>{t('reset_scores_short')}</button>
    {#if recs.length}<button class="btn text bad" onclick={() => (confirmClear = true)}>{t('clear_records')}</button>{/if}
  </TitleBar>
  {#if list.length === 0}
    <div class="empty body-large dim">
      <p>{t('chart_empty')}</p>
      <p class="body-medium">{t('records_info')}</p>
    </div>
  {:else}
    <div class="top">
      <section class="card chart-card">
        <div class="chart-head">
          <span class="title-small">{t('chart_title')}</span>
          <span class="grow"></span>
          {#if groups.size > 1}
            <span class="body-small dim">{t('chart_rules')}</span>
            <Menu label={label(key)} options={[...groups.keys()].map((k) => [k, label(k)] as [string, string])} selected={key} onselect={(k) => { chosen = k; pickedId = null; }} />
          {:else}
            <span class="body-small dim">{label(key)}</span>
          {/if}
        </div>
        <div class="chart-box">
          <DeskScoreChart {totals} results={list.map((r) => r.result)} times={list.map((r) => localTime(r.timestamp ?? ''))}
            description={t('chart_desc', list.length, signed(total))} picked={picked ? list.indexOf(picked) : null} onpick={pickIndex} />
        </div>
      </section>
      <div class="kpis">
        <div class="kpi"><span class="body-small dim">{t('kpi_hands')}</span><span class="headline-small">{list.length}</span></div>
        <div class="kpi"><span class="body-small dim">{t('kpi_total')}</span><span class="headline-small" class:good={total > 0} class:bad={total < 0}>{signed(total)}</span></div>
        <div class="kpi"><span class="body-small dim">{t('kpi_average')}</span><span class="headline-small">{signed(Math.round((total / list.length) * 100) / 100)}</span></div>
        <div class="kpi"><span class="body-small dim">{t('kpi_saved')}</span><span class="headline-small">{savedCount}</span></div>
      </div>
    </div>
    <div class="bottom">
      <section class="card table-card">
        <div class="filters">
          {#each FILTERS as [f, name] (f)}
            <button class="fchip" class:on={filter === f} onclick={() => { filter = f; shown = 100; }}>{t(name)}</button>
          {/each}
          <span class="grow"></span>
          <span class="body-small dim">{t('shown_n', visible.length, filtered.length)}</span>
        </div>
        <div class="scroll">
          <table>
            <thead>
              <tr>
                {@render sortHead('time', t('col_time'), '')}
                <th>{t('yours')}</th>
                <th class="num">{t('col_royalties')}</th>
                {@render sortHead('took', t('col_took'), 'num')}
                {@render sortHead('score', t('col_score'), 'num')}
                <th></th>
              </tr>
            </thead>
            <tbody>
              {#each visible as r (r.id)}
                {@const rows = rowsOf(r)}
                {@const cats = rows.map(guessCategory)}
                {@const roy = royalty(r)}
                {@const fav = app.favoriteIds.has(r.id)}
                <tr data-id={r.id} class:on={r.id === pickedId} onclick={() => pick(r)}>
                  <td class="time body-small">{localTime(r.timestamp ?? '')}</td>
                  <td>
                    <div class="mini">{#each [0, 1, 2] as i (i)}<RowCards cards={rows[i]} category={evals[r.id]?.categories[i] ?? cats[i]} width={20} gap={1} />{/each}</div>
                    <div class="body-small dim hands ellipsis">{hands(r).join(' · ')}</div>
                  </td>
                  <td class="num" class:bad={evals[r.id]?.valid === false}>{evals[r.id]?.valid === false ? t('fouled_short') : roy === null ? '' : roy > 0 ? `+${roy}` : '—'}</td>
                  <td class="num body-small dim">{took(r.time_ms)}</td>
                  <td class="num title-medium" class:good={r.result > 0} class:bad={r.result < 0}>{signed(r.result)}</td>
                  <td class="act">
                    <button class="btn icon text star" class:fav aria-label={t(fav ? 'favorite_remove' : 'favorite_add')} onclick={(e) => { e.stopPropagation(); app.toggleFavorite(r); }}>
                      <Icon name={fav ? 'star' : 'starBorder'} /></button>
                  </td>
                </tr>
              {/each}
            </tbody>
          </table>
          {#if filtered.length > shown}
            <button class="btn text more" onclick={() => (shown += 100)}>{t('show_more_n', Math.min(100, filtered.length - shown))}</button>
          {/if}
          {#if filtered.length === 0}<p class="body-large dim pad">{t('records_none')}</p>{/if}
        </div>
      </section>
      <section class="card detail">
        {#if picked}
          {@const e = evals[picked.id]}
          {@const fav = app.favoriteIds.has(picked.id)}
          <div class="detail-head">
            <span class="headline-small" class:good={picked.result > 0} class:bad={picked.result < 0}>{signed(picked.result)}</span>
            <span class="body-medium dim">{localTime(picked.timestamp ?? '')}</span>
          </div>
          <div class="body-small dim">{label(noC(picked.rules))}{picked.time_ms != null ? ` · ${t('col_took')} ${took(picked.time_ms)}` : ''}</div>
          <DeskRows rows={rowsOf(picked)} keys={e?.keys ?? null} categories={e?.categories ?? rowsOf(picked).map(guessCategory)} width={52} wheel={wheel(picked)} />
          <div class="actions">
            <button class="btn tonal" onclick={() => app.analyzeRecordOnScreen(picked)}><Icon name="analytics" />{t('analyze_this')}</button>
            <button class="btn outlined" onclick={() => app.replay(parseCards(picked.hand))}><Icon name="replay" />{t('replay_hand')}</button>
            <button class="btn outlined" onclick={() => app.toggleFavorite(picked)}><span class="star" class:fav><Icon name={fav ? 'star' : 'starBorder'} /></span>{t(fav ? 'favorite_saved' : 'favorite_short')}</button>
          </div>
        {:else}
          <p class="body-medium dim">{t('record_none_picked')}</p>
        {/if}
      </section>
    </div>
  {/if}
</div>

{#if confirmReset}
  <Dialog onclose={() => (confirmReset = false)}>
    <p class="body-large msg">{t('reset_confirm')}</p>
    {#snippet actions()}
      <button class="btn text" onclick={() => (confirmReset = false)}>{t('cancel')}</button>
      <button class="btn text" onclick={() => { app.resetSession(); confirmReset = false; }}>{t('reset')}</button>
    {/snippet}
  </Dialog>
{/if}
{#if confirmClear}
  <Dialog onclose={() => (confirmClear = false)}>
    <p class="body-large msg">{t('clear_records_confirm', app.recordCount)}</p>
    {#snippet actions()}
      <button class="btn text" onclick={() => (confirmClear = false)}>{t('cancel')}</button>
      <button class="btn text bad" onclick={() => { app.clearRecords(); confirmClear = false; }}>{t('delete')}</button>
    {/snippet}
  </Dialog>
{/if}

<style>
  .page { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .empty { padding: 24px 32px; max-width: 760px; }
  .top { display: grid; grid-template-columns: 1fr 300px; gap: 12px; padding: 0 12px 12px; height: clamp(200px, 32%, 320px); flex: none; }
  .card { background: var(--surface-container-low); border-radius: 16px; padding: 12px 14px; min-height: 0; min-width: 0; }
  .chart-card { display: flex; flex-direction: column; gap: 6px; }
  .chart-head { display: flex; align-items: center; gap: 8px; min-height: 40px; }
  .chart-box { flex: 1; min-height: 0; }
  .grow { flex: 1; }
  .kpis { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .kpi { background: var(--surface-container-low); border-radius: 16px; padding: 12px 14px; display: flex; flex-direction: column; justify-content: center; gap: 2px; }
  .bottom { flex: 1; min-height: 0; display: grid; grid-template-columns: 1fr 340px; gap: 12px; padding: 0 12px 12px; }
  .table-card { display: flex; flex-direction: column; gap: 8px; padding-bottom: 4px; }
  .filters { display: flex; align-items: center; gap: 6px; }
  .fchip { border: 1px solid var(--outline); background: transparent; border-radius: 8px; padding: 5px 12px; cursor: pointer; font-size: 0.875rem; font-weight: 500; }
  .fchip.on { background: var(--secondary-container); color: var(--on-secondary-container); border-color: transparent; }
  .scroll { flex: 1; min-height: 0; overflow: auto; }
  table { width: 100%; border-collapse: collapse; }
  thead th { position: sticky; top: 0; background: var(--surface-container-low); z-index: 1; text-align: left; font-size: 0.8125rem; font-weight: 500;
    color: var(--on-surface-variant); padding: 6px 8px; border-bottom: 1px solid var(--outline-variant); white-space: nowrap; }
  th.num, td.num { text-align: right; font-variant-numeric: tabular-nums; }
  .sorter { border: none; background: none; padding: 0; cursor: pointer; font: inherit; color: inherit; display: inline-flex; gap: 4px; align-items: center; }
  .sorter:hover { color: var(--on-surface); }
  .arrow { font-size: 0.7em; }
  tbody tr { cursor: pointer; }
  tbody tr:hover { background: var(--surface-container); }
  tbody tr.on { background: var(--secondary-container); }
  td { padding: 6px 8px; border-bottom: 1px solid color-mix(in srgb, var(--outline-variant) 50%, transparent); vertical-align: middle; }
  .time { white-space: nowrap; color: var(--on-surface-variant); }
  .mini { display: flex; gap: 6px; }
  .hands { max-width: 420px; margin-top: 2px; }
  .act { width: 48px; }
  .star { color: var(--on-surface-variant); }
  .star.fav, .fav :global(.icon-svg) { color: #e0a100; }
  .more { margin: 8px; }
  .pad { padding: 12px; margin: 0; }
  .detail { display: flex; flex-direction: column; gap: 10px; overflow: auto; }
  .detail-head { display: flex; align-items: baseline; gap: 12px; }
  .actions { display: flex; flex-direction: column; gap: 8px; }
  .actions .btn { justify-content: flex-start; }
  .msg { margin: 0; }
</style>
