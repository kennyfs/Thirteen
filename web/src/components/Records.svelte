<!-- The records: the score chart and the list of practice hands, the saved hands, saving them to a file, and clearing. -->
<script lang="ts">
  import type { App } from '../lib/app.svelte';
  import { parseCards } from '../lib/cards';
  import { t } from '../lib/i18n.svelte';
  import * as R from '../lib/rules';
  import type { Rec } from '../lib/store';
  import { localTime, signed, ui } from '../lib/ui.svelte';
  import Dialog from './Dialog.svelte';
  import Icon from './Icon.svelte';
  import Menu from './Menu.svelte';
  import RecordLine from './RecordLine.svelte';
  import RecordList from './RecordList.svelte';
  import ScoreChart from './ScoreChart.svelte';
  import TitleBar from './TitleBar.svelte';

  let { app }: { app: App } = $props();
  let shown = $state(20);
  let acting = $state<Rec | null>(null);
  let confirmClear = $state(false);
  let confirmReset = $state(false);
  let chosen = $state('');

  // Newest first; re-read whenever the count changes. Hands played again by an older app version are in none.
  const recs = $derived.by(() => { void app.recordCount; return app.records.all().filter((r) => r.label !== 'replay').reverse(); });
  const practice = $derived(recs.filter((r) => r.label !== 'battle' && r.label !== 'puzzle'));
  const saved = $derived.by(() => { void app.favoriteIds; return app.favorites.all().reverse(); });

  // The chart: the totals over the records of one set of rules (without the dealer's cost), oldest first.
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
  const list = $derived(groups.get(key) ?? []);
  const totals = $derived.by(() => { let s = 0; return list.map((r) => (s += r.result)); });
  const label = (spec: string) => app.rulesName(spec.includes('=') ? R.values(spec) : app.presetValues[spec] ?? {});

  function download() {
    const blob = new Blob([app.records.text()], { type: 'application/x-ndjson' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `thirteen-records-${new Date().toISOString().slice(0, 10)}.jsonl`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }
</script>

{#snippet chart()}
      <div class="section-header">{t('chart_title')}</div>
      <div class="pad">
        {#if list.length === 0}
          <p class="body-large dim">{t('chart_empty')}</p>
        {:else}
          {#if groups.size > 1}
            <div class="line"><span class="body-large">{t('chart_rules')}</span>
              <Menu label={label(key)} options={[...groups.keys()].map((k) => [k, label(k)] as [string, string])} selected={key} onselect={(k) => (chosen = k)} />
            </div>
          {/if}
          <div class="title-medium">{t('chart_summary', list.length, signed(totals[totals.length - 1]), signed(totals[totals.length - 1] / list.length))}</div>
          <ScoreChart {totals} firstDate={localTime(list[0].timestamp).slice(0, 10)} lastDate={localTime(list[list.length - 1].timestamp).slice(0, 10)}
            description={t('chart_desc', list.length, signed(totals[totals.length - 1]))} />
        {/if}
      </div>

{/snippet}
{#snippet records()}
      {#if practice.length}
        <div class="section-header">{t('records_list')}</div>
        <div class="pad">
          <RecordList recs={practice.slice(0, shown)} {app} onpick={(r) => (acting = r)} />
          {#if practice.length > shown}<button class="btn text" onclick={() => (shown += 50)}>{t('show_more')}</button>{/if}
        </div>
      {/if}

{/snippet}
{#snippet favorites()}
      <div class="section-header">{t('favorites_title')}</div>
      <div class="pad">
        {#if saved.length === 0}<p class="body-large dim">{t('favorites_empty')}</p>
        {:else}<RecordList recs={saved} {app} onpick={(r) => (acting = r)} />{/if}
      </div>

{/snippet}
{#snippet share()}
      <div class="section-header">{t('records_share_title')}</div>
      <div class="pad col">
        <p class="body-large">{t('records_info')}</p>
        <div class="title-medium">{t('n_recorded', app.recordCount)}</div>
        <div><button class="btn outlined big" disabled={app.recordCount === 0} onclick={download}><Icon name="download" />{t('export')}</button></div>
      </div>
      <div class="danger">
        <button class="btn text dim" onclick={() => (confirmReset = true)}>{t('reset_scores_short')}</button>
        {#if recs.length}<button class="btn text bad" onclick={() => (confirmClear = true)}>{t('clear_records')}</button>{/if}
      </div>
{/snippet}

<div class="page">
  <TitleBar title={t('records_title')} />
  {#if ui.desk}
    <!-- A computer's: the chart, the saved hands and the file at the left, the records at the right, each scrolled on its own. -->
    <div class="desk">
      <div class="scroll">{@render chart()}{@render favorites()}{@render share()}</div>
      <div class="scroll">{#if practice.length}{@render records()}{:else}<div class="section-header">{t('records_list')}</div><p class="pad body-large dim">{t('records_none')}</p>{/if}</div>
    </div>
  {:else}
    <div class="scroll">
      <div class="readable">{@render chart()}{@render records()}{@render favorites()}{@render share()}</div>
    </div>
  {/if}
</div>

{#if acting}
  {@const r = acting}
  <Dialog wide onclose={() => (acting = null)}>
    <RecordLine {r} max={ui.sizes.record} onclick={() => {}} />
    <button class="btn text action" onclick={() => { app.analyzeRecordOnScreen(r); acting = null; }}>{t('analyze_this')}</button>
    <button class="btn text action" onclick={() => { app.replay(parseCards(r.hand)); acting = null; }}>{t('replay_hand')}</button>
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
  .scroll { flex: 1; overflow: auto; padding-bottom: 24px; }
  .readable { max-width: 840px; margin: 0 auto; }
  .desk { flex: 1; min-height: 0; display: grid; grid-template-columns: minmax(360px, 1fr) minmax(0, 1.3fr); gap: 8px; padding: 0 8px; }
  .desk .scroll { min-height: 0; }
  .pad { padding: 0 16px; }
  .col { display: flex; flex-direction: column; gap: 12px; }
  .col p, .pad > p { margin: 0; }
  .line { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
  .danger { display: flex; gap: 4px; padding: 8px; }
  .action { width: 100%; justify-content: flex-start; min-height: 52px; font-size: 1rem; }
  .msg { margin: 0; }
</style>
