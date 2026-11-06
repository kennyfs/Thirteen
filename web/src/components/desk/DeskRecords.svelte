<!--
  A computer's records, as the app's by kind (practice, table games, puzzle contests): the chart of the running total
  (of the rules chosen) with its figures, then the list, newest first: a practice hand a tile, its rows one under
  another in large cards; a battle a line that opens its results (a battle hand kept without its battle, a tile). The
  saved battles and hands are at the right. A hand (from the list or the chart) opens what to do with it: analyze it or
  play it again, as the app's. Saving to a file, resetting the scores and clearing are at the top.
-->
<script lang="ts">
  import type { App } from '../../lib/app.svelte';
  import type { BattleRecord } from '../../lib/battle.svelte';
  import { guessCategory, parseCards, type Rows } from '../../lib/cards';
  import { t } from '../../lib/i18n.svelte';
  import { chartGroups, entries, KIND_NAMES, kindOf, noC, RECORD_KINDS, type RecordKind } from '../../lib/records';
  import * as R from '../../lib/rules';
  import { CARD_GAP } from '../../lib/sizing';
  import type { Rec } from '../../lib/store';
  import { localTime, signed } from '../../lib/ui.svelte';
  import BattleLine from '../BattleLine.svelte';
  import Dialog from '../Dialog.svelte';
  import Icon from '../Icon.svelte';
  import Menu from '../Menu.svelte';
  import RowCards from '../RowCards.svelte';
  import TitleBar from '../TitleBar.svelte';
  import Toggle from '../Toggle.svelte';
  import DeskRows from './DeskRows.svelte';
  import DeskScoreChart from './DeskScoreChart.svelte';

  let { app }: { app: App } = $props();
  let confirmClear = $state(false);
  let confirmReset = $state(false);
  let chosen = $state('');
  let kind = $state<RecordKind>('PRACTICE');
  let shown = $state(60);
  let acting = $state<Rec | null>(null);

  // Newest first; re-read whenever the count changes.
  const recs = $derived.by(() => { void app.recordCount; return app.records.all().filter((r) => r.label !== 'replay').reverse(); });
  const ofKind = $derived(recs.filter((r) => kindOf(r) === kind));
  const battles = $derived.by(() => { void app.recordCount; return app.battles.all() as BattleRecord[]; });
  const list_ = $derived(entries(kind, recs, battles));
  const saved = $derived.by(() => { void app.favoriteIds; return app.favorites.all().reverse(); });
  const savedBattles = $derived.by(() => { void app.favoriteBattleIds; return app.favoriteBattles.all() as BattleRecord[]; });

  const groups = $derived(chartGroups(ofKind));
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

  const rowsOf = (r: Rec): Rows => ['front', 'middle', 'back'].map((k) => parseCards(r[k] ?? ''));
  const wheel = (r: Rec) => (R.values(r.rules || 'family').wheel ?? 0);
  function setKind(k: RecordKind) { kind = k; shown = 60; chosen = ''; }

  function download() {
    const blob = new Blob([app.records.text()], { type: 'application/x-ndjson' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `thirteen-records-${new Date().toISOString().slice(0, 10)}.jsonl`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }
  /** A tile's card: the rows of five one under another. */
  const CARD = 44;
  const tileMin = CARD * 5 + CARD_GAP * 4 + 24;
</script>

{#snippet tile(r: Rec)}
  {@const rows = rowsOf(r)}
  {@const fav = app.favoriteIds.has(r.id)}
  <div class="tile" role="button" tabindex="0" onclick={() => (acting = r)} onkeydown={(e) => { if (e.key === 'Enter') acting = r; }}>
    <div class="tile-head">
      <span class="title-medium nowrap" class:good={r.result > 0} class:bad={r.result < 0}>{typeof r.result === 'number' ? signed(r.result) : ''}</span>
      <span class="body-small dim ellipsis grow">{localTime(r.timestamp ?? '')}</span>
      <button class="btn icon text star" class:on={fav} aria-label={t(fav ? 'favorite_remove' : 'favorite_add')}
        onclick={(e) => { e.stopPropagation(); app.toggleFavorite(r); }}><Icon name={fav ? 'star' : 'starBorder'} /></button>
    </div>
    <div class="stack">{#each [0, 1, 2] as i (i)}<RowCards cards={rows[i]} category={guessCategory(rows[i])} width={CARD} gap={CARD_GAP} />{/each}</div>
  </div>
{/snippet}

<div class="page records">
  <TitleBar title={t('records_title')}>
    <span class="body-small dim">{t('n_recorded', app.recordCount)}</span>
    <button class="btn outlined" disabled={app.recordCount === 0} onclick={download}><Icon name="download" />{t('export')}</button>
    <button class="btn text" onclick={() => (confirmReset = true)}>{t('reset_scores_short')}</button>
    {#if recs.length}<button class="btn text bad" onclick={() => (confirmClear = true)}>{t('clear_records')}</button>{/if}
  </TitleBar>
  <div class="body">
    <div class="main">
      <div class="kinds"><Toggle options={RECORD_KINDS.map((k, i) => [k, t(KIND_NAMES[i])] as [RecordKind, string])} selected={kind} onselect={setKind} /></div>
      {#if list.length === 0}
        <section class="card"><p class="body-large dim">{t('chart_empty')}</p></section>
      {:else}
        <section class="card chart-card">
          <div class="chart-head">
            <span class="title-medium">{t('chart_summary', list.length, signed(total), signed(total / list.length))}</span>
            <span class="grow"></span>
            {#if groups.size > 1}
              <span class="body-small dim">{t('chart_rules')}</span>
              <Menu label={label(key)} options={[...groups.keys()].map((k) => [k, label(k)] as [string, string])} selected={key} onselect={(k) => (chosen = k)} />
            {:else}
              <span class="body-small dim">{label(key)}</span>
            {/if}
          </div>
          {#if kind === 'PUZZLE'}<p class="body-medium dim">{t('chart_puzzle_note')}</p>{/if}
          <div class="chart-box">
            <DeskScoreChart {totals} results={list.map((r) => r.result)} times={list.map((r) => localTime(r.timestamp ?? ''))}
              description={t('chart_desc', list.length, signed(total))} picked={null} onpick={(i) => (acting = list[i] ?? null)} />
          </div>
        </section>
      {/if}
      {#if list_.length}
        <div class="section-header">{t(kind === 'PRACTICE' ? 'records_list' : 'records_battles')}</div>
        {#if kind === 'PRACTICE'}
          <div class="tiles" style:grid-template-columns="repeat(auto-fill, minmax({tileMin}px, 1fr))">{#each ofKind.slice(0, shown) as r (r.id)}{@render tile(r)}{/each}</div>
        {:else}
          <div class="tiles" style:grid-template-columns="repeat(auto-fill, minmax({Math.max(tileMin, 360)}px, 1fr))">
            {#each list_.slice(0, shown) as e, i (i)}
              {#if 'battle' in e}<div class="bline"><BattleLine b={e.battle} {app} onclick={() => app.openBattleRecord(e.battle)} /></div>
              {:else}{@render tile(e.hand)}{/if}
            {/each}
          </div>
        {/if}
        {#if list_.length > shown}<button class="btn text more" onclick={() => (shown += 60)}>{t('show_more')}</button>{/if}
      {/if}
    </div>
    <aside class="side">
      {#if savedBattles.length}
        <div class="section-header">{t('favorite_battles_title')}</div>
        {#each savedBattles as b (b.id)}<div class="bline"><BattleLine {b} {app} onclick={() => app.openBattleRecord(b)} /></div>{/each}
      {/if}
      <div class="section-header">{t('favorites_title')}</div>
      {#if saved.length === 0}<p class="body-medium dim pad">{t('favorites_empty')}</p>
      {:else}<div class="saved">{#each saved as r (r.id)}{@render tile(r)}{/each}</div>{/if}
      <p class="body-small dim pad info">{t('records_info')}</p>
    </aside>
  </div>
</div>

{#if acting}
  {@const r = acting}
  <Dialog wide onclose={() => (acting = null)}>
    <div class="act-head">
      <span class="headline-small" class:good={r.result > 0} class:bad={r.result < 0}>{typeof r.result === 'number' ? signed(r.result) : ''}</span>
      <span class="body-medium dim">{localTime(r.timestamp ?? '')} · {label(noC(r.rules))}</span>
    </div>
    <DeskRows rows={rowsOf(r)} keys={null} categories={rowsOf(r).map(guessCategory)} width={64} wheel={wheel(r)} />
    <button class="btn text action" onclick={() => { app.analyzeRecordOnScreen(r); acting = null; }}><Icon name="analytics" />{t('analyze_this')}</button>
    <button class="btn text action" onclick={() => { app.replay(parseCards(r.hand)); acting = null; }}><Icon name="replay" />{t('replay_hand')}</button>
    {#snippet actions()}<button class="btn text" onclick={() => (acting = null)}>{t('cancel')}</button>{/snippet}
  </Dialog>
{/if}
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
  .body { flex: 1; min-height: 0; display: grid; grid-template-columns: 1fr 340px; gap: 12px; padding: 0 12px 12px; }
  .main { min-height: 0; overflow: auto; display: flex; flex-direction: column; gap: 10px; padding-right: 4px; }
  .side { min-height: 0; overflow: auto; background: var(--surface-container-low); border-radius: 16px; padding: 0 10px 12px; }
  .kinds { max-width: 520px; flex: none; }
  .card { background: var(--surface-container-low); border-radius: 16px; padding: 12px 14px; }
  .card p { margin: 0; }
  .chart-card { display: flex; flex-direction: column; gap: 6px; flex: none; }
  .chart-head { display: flex; align-items: center; gap: 8px; min-height: 40px; flex-wrap: wrap; }
  .chart-box { height: 220px; }
  .grow { flex: 1; min-width: 0; }
  .section-header { padding-left: 4px; }
  .tiles { display: grid; gap: 10px; align-items: start; }
  .tile { background: var(--surface-container-low); border-radius: 14px; padding: 4px 12px 12px; cursor: pointer; display: flex; flex-direction: column; gap: 4px; min-width: 0; }
  .tile:hover { background: var(--surface-container); }
  .tile-head { display: flex; align-items: center; gap: 8px; min-height: 40px; }
  .stack { display: flex; flex-direction: column; gap: 4px; }
  .bline { background: var(--surface-container-low); border-radius: 14px; padding: 0 8px; }
  .side .bline { background: var(--surface); margin-bottom: 6px; }
  .saved { display: flex; flex-direction: column; gap: 8px; }
  .side .tile { background: var(--surface); }
  .star { width: 40px; min-height: 40px; color: var(--on-surface-variant); }
  .star.on { color: #e0a100; }
  .more { align-self: flex-start; }
  .pad { padding: 0 4px; }
  .info { margin-top: 16px; }
  .act-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 8px; }
  .action { width: 100%; justify-content: flex-start; min-height: 52px; font-size: 1rem; }
  .msg { margin: 0; }
</style>
