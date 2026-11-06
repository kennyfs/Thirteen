<!--
  A computer's practice: the table (the hand being set, then everyone's rows) beside a side panel with the hand's
  number, the scores, what to do next and the buttons with their keys. On the result the panel compares the player's
  rows with the best ones once analyzed (A); A again opens all the candidates (DeskCandidates), the whole window wide.
-->
<script lang="ts">
  import type { App } from '../../lib/app.svelte';
  import { ROW_SIZES } from '../../lib/cards';
  import { mineIndex, ranks } from '../../lib/compare';
  import { t, ta } from '../../lib/i18n.svelte';
  import { nav } from '../../lib/nav.svelte';
  import { MAX_DESK_PLAY } from '../../lib/sizing';
  import { fmtQ, signed } from '../../lib/ui.svelte';
  import Dialog from '../Dialog.svelte';
  import Icon from '../Icon.svelte';
  import SeatResults from '../SeatResults.svelte';
  import TitleBar from '../TitleBar.svelte';
  import Toggle from '../Toggle.svelte';
  import CompareTable from './CompareTable.svelte';
  import DeskArranging from './DeskArranging.svelte';
  import DeskCandidates from './DeskCandidates.svelte';
  import Kbd from './Kbd.svelte';
  import KeyList from './KeyList.svelte';

  let { app, onrules }: { app: App; onrules: () => void } = $props();
  const seatName = (i: number) => (i === 0 ? t('you') : t('seat_pos', i));
  const wheel = $derived(app.gameValues.wheel ?? 0);
  const saved = $derived(!!app.lastRecord && app.favoriteIds.has(app.lastRecord.id));
  const legacy = $derived(app.gameAnalysis && app.gameAnalysis.legacyModel && app.legacyMismatch(app.gameAnalysis.rules)
    ? t('legacy_warning', app.model(app.anaModel).name) : null);

  let help = $state(false);
  const vm = $derived(app.play);
  const canConfirm = $derived(!app.waiting && vm.rowsEval?.valid === true);
  const hint = $derived.by(() => {
    const picked = vm.selected.size;
    const left = vm.unplaced.length;
    if (vm.armed !== null) return t('hint_armed', ta('row_names')[vm.armed], ROW_SIZES[vm.armed] - vm.rows[vm.armed].length - picked);
    if (picked > 0) return t('hint_selected', picked);
    if (left === 13) return t('hint_start_desk');
    if (left > 0) return t('hint_left', left);
    const ev = vm.rowsEval;
    if (!ev) return '';
    if (ev.valid === true) {
      const sum = ev.royalties.reduce((a, b) => a + b, 0);
      return sum > 0 ? t('ready_royalty', signed(sum).slice(1)) : t('ready_hint');
    }
    return ev.keys[0] <= ev.keys[1] && ev.keys[1] <= ev.keys[2] ? t('fouled_strict') : t('fouled');
  });
  const fouled = $derived(vm.unplaced.length === 0 && vm.rowsEval?.valid === false);
  const analysisPage = $derived(!!app.result && app.resultTab === 1);
  const favorite = () => app.lastRecord && app.toggleFavorite(app.lastRecord);
  /** A: the summary first (analyzed here), then all the candidates, then back. */
  function analysisKey() {
    if (analysisPage) nav.back();
    else if (app.gameAnalysis) app.showGameAnalysis();
    else app.computeGameAnalysis();
  }

  // The player's rows among the candidates, once analyzed.
  const cmp = $derived.by(() => {
    const a = app.gameAnalysis;
    const r = app.result;
    if (!a || !r) return null;
    const i = mineIndex(a, r.rows[0]);
    return { a, i, rank: i === null ? null : ranks(a)[i] };
  });

  // The result's keys (the arranging table's are DeskArranging's).
  function onKey(e: KeyboardEvent) {
    if (!app.result || nav.sub === 'rules') return;
    const el = e.target as HTMLElement | null;
    if (el?.closest('input, textarea, select, [contenteditable]') || document.querySelector('dialog[open]')) return;
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    const k = e.key.toLowerCase();
    if ((k === 'enter' && !el?.closest('button')) || k === 'n') app.dealNow();
    else if (k === 'a') analysisKey();
    else if (k === 'escape' && analysisPage) nav.back();
    else if (k === 'f') favorite();
    else if (k === 'r') app.replay();
    else return;
    e.preventDefault();
  }
</script>

<svelte:window onkeydown={onKey} />

{#snippet scoreboard()}
  {@const r = app.result}
  <div class="board">
    {#if r}<div class="seat cols body-small dim"><span class="who"></span><span class="delta">{t('col_hand')}</span><span class="total">{t('col_total')}</span></div>{/if}
    {#each app.session.totals as x, i (i)}
      {@const d = r ? r.score.result[i] : null}
      <div class="seat" class:me={i === 0}>
        <span class="who"><span class="title-small nowrap">{seatName(i)}</span>
          {#if i > 0 && app.seats[i - 1]}<span class="body-small dim ellipsis">{app.seatName(app.seats[i - 1])}</span>{/if}</span>
        {#if d !== null}<span class="title-medium bold delta" class:good={d > 0} class:bad={d < 0}>{signed(d)}</span>{/if}
        <span class="body-large total" class:good={i === 0 && x > 0} class:bad={i === 0 && x < 0}>{signed(x)}</span>
      </div>
    {/each}
  </div>
{/snippet}

{#snippet keepers()}
  <div class="tools">
    <button class="btn outlined" disabled={!app.lastRecord} onclick={favorite}>
      <span class:star={saved}><Icon name={saved ? 'star' : 'starBorder'} /></span>{t(saved ? 'favorite_saved' : 'favorite_short')}<Kbd k="F" inline />
    </button>
    <button class="btn outlined" onclick={() => app.replay()}><Icon name="replay" />{t('replay_short')}<Kbd k="R" inline /></button>
  </div>
{/snippet}

{#snippet summary()}
  <section class="card">
    <div class="title-small">{t('cmp_title')}</div>
    {#if cmp}
      {@const best = cmp.a.cands[0]}
      {@const mine = cmp.i !== null ? cmp.a.cands[cmp.i] : null}
      {#if cmp.i === 0}<p class="body-medium good bold">{t('cmp_same')}</p>
      {:else if mine && cmp.rank !== null && mine.q !== null && best.q !== null}<p class="body-medium">{t('cmp_behind', cmp.rank, fmtQ(best.q - mine.q).slice(1))}</p>
      {:else}<p class="body-medium">{t('cmp_extra')}</p>{/if}
      {#if mine}<CompareTable a={cmp.a} x={mine} base={cmp.i === 0 ? null : best} xLabel={t('yours')} baseLabel={t('best')} compact />{/if}
      {#if legacy}<p class="body-small bad">{legacy}</p>{/if}
      <button class="btn tonal" onclick={() => app.showGameAnalysis()}><Icon name="analytics" />{t('all_candidates')}<Kbd k="A" inline /></button>
    {:else if app.gameAnalyzing}
      <div class="thinking body-medium dim"><span class="spinner small"></span>{t('analyzing')}</div>
    {:else}
      <p class="body-medium dim">{t('cmp_pending')}</p>
      <button class="btn tonal" onclick={() => app.computeGameAnalysis()}><Icon name="analytics" />{t('analyze_my_hand')}<Kbd k="A" inline /></button>
    {/if}
  </section>
{/snippet}

{#if analysisPage}
  <div class="page">
    <TitleBar title={t('analyze_my_hand')} onback={() => nav.back()}>
      <span class="body-small dim keys"><Kbd k="Esc" />{t('k_back')}</span>
      <button class="btn outlined" disabled={!app.lastRecord} onclick={favorite}>
        <span class:star={saved}><Icon name={saved ? 'star' : 'starBorder'} /></span>{t(saved ? 'favorite_saved' : 'favorite_short')}<Kbd k="F" inline />
      </button>
      <button class="btn outlined" onclick={() => app.replay()}><Icon name="replay" />{t('replay_short')}<Kbd k="R" inline /></button>
      <button class="btn filled" onclick={() => app.dealNow()}>{t('next_hand')}<Kbd k="Enter" inline /></button>
    </TitleBar>
    {#if app.gameAnalysis && app.result}<DeskCandidates a={app.gameAnalysis} mine={app.result.rows[0]} legacyNote={legacy} />
    {:else}<div class="loading"><div class="spinner"></div><div class="body-large dim">{t('analyzing')}</div></div>{/if}
  </div>
{:else}
  <div class="desk">
    <div class="main">
      {#if app.result}
        <div class="felt result">
          <SeatResults rows={app.result.rows} score={app.result.score} {wheel} names={[0, 1, 2, 3].map(seatName)}
            notes={[null, ...app.seats.map((s) => app.seatName(s))]} totals={app.session.totals} max={MAX_DESK_PLAY} />
        </div>
      {:else if app.ready}
        <DeskArranging vm={app.play} {wheel} busy={app.waiting} onconfirm={() => app.confirm()} bind:help />
      {:else}
        <div class="loading"><div class="spinner"></div></div>
      {/if}
    </div>
    <aside class="side">
      <div class="side-head">
        <span class="title-large">{app.replaying ? t('replay_title') : t('hand_n', app.session.hands + (app.result ? 0 : 1))}</span>
        {#if Object.keys(app.gameValues).length}
          <button class="chip" onclick={onrules}>{app.rulesName(app.gameValues)}<Icon name="dropDown" /></button>
        {/if}
      </div>
      {#if app.replaying}<div class="body-medium dim">{t('replay_note')}</div>{/if}
      {@render scoreboard()}
      {#if !app.result}
        <p class="hint title-medium" class:bad={fouled} aria-live="polite">{hint}</p>
        <div class="sortline"><Toggle options={[[false, t('sort_rank')], [true, t('sort_suit')]] as [boolean, string][]} selected={vm.sortBySuit} onselect={(v) => (vm.sortBySuit = v)} /><Kbd k="S" /></div>
        <div class="tools">
          <button class="btn outlined" disabled={!vm.canUndo} onclick={() => vm.undo()}><Icon name="undo" />{t('undo')}<Kbd k="Z" inline /></button>
          <button class="btn outlined" disabled={!vm.canClear} onclick={() => vm.clearRows()}><Icon name="clearAll" />{t('take_all')}<Kbd k="Del" inline /></button>
        </div>
        <button class="btn filled big" disabled={!canConfirm} onclick={() => app.confirm()}>{app.waiting ? '…' : t('confirm')}<Kbd k="Enter" inline /></button>
        <span class="grow"></span>
        <KeyList keys={[[['1', '2', '3'], t('k_rows')], [['←', '→', 'Space'], t('k_pick')], [['Esc'], t('k_unpick')], [['?'], t('k_help')]]} mouse={t('mouse_tips')} />
      {:else}
        <button class="btn filled big" onclick={() => app.dealNow()}>{t('next_hand')}<Kbd k="Enter" inline /></button>
        {@render keepers()}
        {@render summary()}
      {/if}
    </aside>
  </div>
{/if}

{#if help}
  <Dialog title={t('arrange_help_title')} onclose={() => (help = false)}>
    <p class="body-large help-text">{t('arrange_help_desk')}</p>
    <p class="body-medium dim help-text">{t('mouse_tips')}</p>
    {#snippet actions()}<button class="btn text" onclick={() => (help = false)}>{t('ok')}</button>{/snippet}
  </Dialog>
{/if}

<style>
  .page { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .desk { height: 100%; display: flex; min-height: 0; }
  .main { flex: 1; min-width: 0; display: flex; flex-direction: column; padding-top: 12px; }
  .result { flex: 1; min-height: 0; margin: 0 12px 12px; padding: 12px; }
  .result :global(.results) { display: flex; flex-direction: column; justify-content: safe center; }
  .side { flex: none; width: 320px; display: flex; flex-direction: column; gap: 12px; padding: 12px 16px 16px 4px; min-height: 0; overflow: auto; }
  .side-head { display: flex; align-items: center; gap: 10px; min-height: 40px; flex-wrap: wrap; }
  .board { display: flex; flex-direction: column; gap: 2px; background: var(--surface-container-low); border-radius: 16px; padding: 6px; }
  .seat { display: flex; align-items: center; gap: 8px; padding: 6px 10px; border-radius: 10px; }
  .seat.me { background: var(--secondary-container); }
  .seat.cols { padding-top: 2px; padding-bottom: 0; }
  .who { flex: 1; min-width: 0; display: flex; align-items: baseline; gap: 8px; }
  .delta { min-width: 3.2em; text-align: right; }
  .total { min-width: 3em; text-align: right; }
  .hint { margin: 0; min-height: 2.9em; }
  .sortline { display: flex; align-items: center; gap: 8px; color: var(--on-surface-variant); }
  .sortline :global(.group) { flex: 1; }
  .tools { display: flex; flex-wrap: wrap; gap: 8px; }
  .tools .btn { flex: 1 1 auto; padding: 0 12px; }
  .side .big { width: 100%; }
  .grow { flex: 1; }
  .card { background: var(--surface-container-low); border-radius: 16px; padding: 12px; display: flex; flex-direction: column; gap: 8px; }
  .card p { margin: 0; }
  .thinking { display: flex; align-items: center; gap: 10px; min-height: 40px; }
  .star { color: var(--highlight); display: contents; }
  .keys { display: inline-flex; align-items: center; gap: 4px; margin-right: 8px; }
  .loading { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; padding: 32px; }
  .spinner { width: 48px; height: 48px; border-radius: 50%; border: 5px solid var(--surface-container-highest); border-top-color: var(--primary); animation: spin 0.9s linear infinite; }
  .spinner.small { width: 22px; height: 22px; border-width: 3px; }
  @keyframes spin { to { transform: rotate(360deg); } }
  .help-text { white-space: pre-line; margin: 0; }
</style>
