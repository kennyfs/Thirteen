<!--
  A computer's practice: the table (the hand being set, then everyone's rows) beside a side panel with the hand's
  number, the scores, what to do next and the buttons with their keys. On the result the panel compares the player's
  rows with the best ones once analyzed (A); A again opens all the candidates (DeskCandidates), the whole window wide.
-->
<script lang="ts">
  import type { App } from '../../lib/app.svelte';
  import { ROW_SIZES } from '../../lib/cards';
  import { mineIndex, ranks } from '../../lib/compare';
  import { t, ta, td } from '../../lib/i18n.svelte';
  import { nav } from '../../lib/nav.svelte';
  import { MAX_DESK_PLAY } from '../../lib/sizing';
  import { signed } from '../../lib/ui.svelte';
  import Dialog from '../Dialog.svelte';
  import Icon from '../Icon.svelte';
  import SeatResults from '../SeatResults.svelte';
  import TitleBar from '../TitleBar.svelte';
  import Toggle from '../Toggle.svelte';
  import ProbTiles from './ProbTiles.svelte';
  import { remarks as remarksOf } from '../../lib/commentary';
  import { remarkText } from '../../lib/remarks';
  import DeskArranging from './DeskArranging.svelte';
  import DeskCandidates from './DeskCandidates.svelte';
  import Kbd from './Kbd.svelte';
  import KeyList from './KeyList.svelte';
  import { keyList, keyMap, startHint } from '../../lib/desk-keys';

  let { app, onrules }: { app: App; onrules: () => void } = $props();
  const seatName = (i: number) => (i === 0 ? t('you') : t('seat_pos', i));
  // The finished hand shown: the one just played, or (peek, L) the one before the hand in play.
  const peek = $derived(app.peek);
  const f = $derived(peek ? app.prev : app.current);
  const wheel = $derived((f?.values ?? app.gameValues).wheel ?? 0);
  const saved = $derived(!!f?.record && app.favoriteIds.has(f.record.id));
  const legacy = $derived(f?.analysis && f.analysis.legacyModel && app.legacyMismatch(f.analysis.rules)
    ? t('legacy_warning', app.model(app.anaModel).name) : null);

  let help = $state(false);
  const vm = $derived(app.play);
  const km = $derived(keyMap(app.deskKeys));
  const canConfirm = $derived(!app.waiting && vm.rowsEval?.valid === true);
  const hint = $derived.by(() => {
    const picked = vm.selected.size;
    const left = vm.unplaced.length;
    if (vm.armed !== null) return td('hint_armed', ta('row_names')[vm.armed], ROW_SIZES[vm.armed] - vm.rows[vm.armed].length - picked);
    if (picked > 0) return td('hint_selected', picked);
    if (left === 13) return startHint(app.deskKeys);
    if (left > 0) return td('hint_left', left);
    const ev = vm.rowsEval;
    if (!ev) return '';
    if (ev.valid === true) {
      const sum = ev.royalties.reduce((a, b) => a + b, 0);
      return sum > 0 ? t('ready_royalty', signed(sum).slice(1)) : t('ready_hint');
    }
    return ev.keys[0] <= ev.keys[1] && ev.keys[1] <= ev.keys[2] ? t('fouled_strict') : t('fouled');
  });
  const fouled = $derived(vm.unplaced.length === 0 && vm.rowsEval?.valid === false);
  const analysisPage = $derived(!!f && (peek ? peek === 2 : app.resultTab === 1));
  const favorite = () => { if (f?.record) app.toggleFavorite(f.record); };
  const showAnalysis = () => (peek ? app.showPrevAnalysis() : app.showGameAnalysis());
  /** A: all the candidates, and back. */
  function analysisKey() {
    if (analysisPage) nav.back();
    else showAnalysis();
  }
  // Each result is analyzed at once: the remarks and the figures are in the panel.
  $effect(() => { if (app.result && !app.gameAnalysis) app.computeGameAnalysis(); });
  $effect(() => { if (peek) app.computePrevAnalysis(); });

  // The player's rows among the candidates, once analyzed.
  const cmp = $derived.by(() => {
    const a = f?.analysis;
    const r = f?.result;
    if (!a || !r) return null;
    const i = mineIndex(a, r.rows[0]);
    return { a, i, rank: i === null ? null : ranks(a)[i] };
  });

  const rem = $derived(cmp ? remarksOf(cmp.a, f?.result.rows[0] ?? null) : []);
  const mineRemark = $derived(rem.find((r) => r.kind === 'same' || r.kind === 'mistake') ?? null);
  const restRemarks = $derived(rem.filter((r) => r !== mineRemark).map(remarkText).join(t('remark_sep')));

  // The result's keys (the arranging table's are DeskArranging's).
  function onKey(e: KeyboardEvent) {
    if (nav.sub === 'rules') return;
    const el = e.target as HTMLElement | null;
    if (el?.closest('input, textarea, select, [contenteditable]') || document.querySelector('dialog[open]')) return;
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    // L: the previous hand's result, its analysis, and back to the hand in play (by where the key is, as the table's).
    if (e.code === 'KeyL' && !app.result && app.prev) { app.stepPrev(); e.preventDefault(); return; }
    if (!f) return;
    const k = e.key.toLowerCase();
    if (peek) {
      if (k === 'escape' || (k === 'enter' && !el?.closest('button'))) nav.leave(peek === 2 ? 'lastanalysis' : 'last');
      else if (k === 'a') analysisKey();
      else if (k === 'f') favorite();
      else return;
      e.preventDefault();
      return;
    }
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
  {@const r = f?.result}
  {@const seats = f?.seats ?? app.seats}
  <div class="board">
    {#if r}<div class="seat cols body-small dim"><span class="who"></span><span class="delta">{t('col_hand')}</span><span class="total">{t('col_total')}</span></div>{/if}
    {#each f?.totals ?? app.session.totals as x, i (i)}
      {@const d = r ? r.score.result[i] : null}
      <div class="seat" class:me={i === 0}>
        <span class="who"><span class="title-small nowrap">{seatName(i)}</span>
          {#if i > 0 && seats[i - 1]}<span class="body-small dim ellipsis">{app.seatName(seats[i - 1])}</span>{/if}</span>
        {#if d !== null}<span class="title-medium bold delta" class:good={d > 0} class:bad={d < 0}>{signed(d)}</span>{/if}
        <span class="body-large total" class:good={i === 0 && x > 0} class:bad={i === 0 && x < 0}>{signed(x)}</span>
      </div>
    {/each}
  </div>
{/snippet}

{#snippet keepers()}
  <div class="tools">
    <button class="btn outlined" disabled={!f?.record} onclick={favorite}>
      <span class:star={saved}><Icon name={saved ? 'star' : 'starBorder'} /></span>{t(saved ? 'favorite_saved' : 'favorite_short')}<Kbd k="F" inline />
    </button>
    {#if !peek}<button class="btn outlined" onclick={() => app.replay()}><Icon name="replay" />{t('replay_short')}<Kbd k="R" inline /></button>{/if}
  </div>
{/snippet}

{#snippet onward()}
  {#if peek}
    <button class="btn filled big" onclick={() => nav.leave(peek === 2 ? 'lastanalysis' : 'last')}>{t('back_to_hand')}<Kbd k="Enter" inline /></button>
  {:else}
    <button class="btn filled big" onclick={() => app.dealNow()}>{t('next_hand')}<Kbd k="Enter" inline /></button>
  {/if}
{/snippet}

{#snippet summary()}
  {#if cmp}
    {@const best = cmp.a.cands[0]}
    {@const mine = cmp.i !== null ? cmp.a.cands[cmp.i] : null}
    {#if cmp.a.dragon}<div class="body-large good">{t('dragon')}</div>{/if}
    {#if rem.length}
      <section class="card remarks">
        <div class="title-small">{t('remarks_title')}</div>
        {#if mineRemark}<p class="title-medium bold" class:mistake={mineRemark.kind === 'mistake'}>{remarkText(mineRemark)}</p>{/if}
        {#if restRemarks}<p class="body-large">{restRemarks}</p>{/if}
      </section>
    {/if}
    <section class="card">
      <div class="yours-head">
        <span class="badge strong">{cmp.i === 0 ? `${t('best')} · ${t('yours')}` : cmp.rank !== null && mine && !mine.extra ? t('yours_rank', t('yours'), cmp.rank) : t('yours')}</span>
      </div>
      {#if mine}<ProbTiles a={cmp.a} x={mine} best={cmp.i === 0 ? null : best} />
      {:else}<p class="body-medium">{t('cmp_extra')}</p>{/if}
      {#if legacy}<p class="body-small bad">{legacy}</p>{/if}
      <button class="btn tonal" onclick={showAnalysis}><Icon name="analytics" />{t('all_candidates')}<Kbd k="A" inline /></button>
    </section>
  {:else}
    <section class="card"><div class="thinking body-medium dim"><span class="spinner small"></span>{t('analyzing')}</div></section>
  {/if}
{/snippet}

{#if analysisPage}
  <div class="page">
    <TitleBar title={peek ? `${t('analyze_my_hand')} · ${t('last_hand')}` : t('analyze_my_hand')} onback={() => nav.back()}>
      <span class="body-small dim keys"><Kbd k="Esc" />{t('k_back')}</span>
      <button class="btn outlined" disabled={!f?.record} onclick={favorite}>
        <span class:star={saved}><Icon name={saved ? 'star' : 'starBorder'} /></span>{t(saved ? 'favorite_saved' : 'favorite_short')}<Kbd k="F" inline />
      </button>
      {#if peek}
        <button class="btn filled" onclick={() => nav.leave('lastanalysis')}>{t('back_to_hand')}<Kbd k="Enter" inline /></button>
      {:else}
        <button class="btn outlined" onclick={() => app.replay()}><Icon name="replay" />{t('replay_short')}<Kbd k="R" inline /></button>
        <button class="btn filled" onclick={() => app.dealNow()}>{t('next_hand')}<Kbd k="Enter" inline /></button>
      {/if}
    </TitleBar>
    {#if f?.analysis}<DeskCandidates a={f.analysis} mine={f.result.rows[0]} legacyNote={legacy} />
    {:else}<div class="loading"><div class="spinner"></div><div class="body-large dim">{t('analyzing')}</div></div>{/if}
  </div>
{:else}
  <div class="desk">
    <div class="main">
      {#if f}
        <div class="felt result">
          <SeatResults rows={f.result.rows} score={f.result.score} {wheel} names={[0, 1, 2, 3].map(seatName)}
            notes={[null, ...f.seats.map((s) => app.seatName(s))]} totals={f.totals} max={MAX_DESK_PLAY} />
        </div>
      {:else if app.ready}
        <DeskArranging vm={app.play} {wheel} busy={app.waiting} onconfirm={() => app.confirm()} keys={app.deskKeys} bind:help />
      {:else}
        <div class="loading"><div class="spinner"></div></div>
      {/if}
    </div>
    <aside class="side" class:wide={!!f}>
      <div class="side-head">
        {#if peek}<span class="chip static">{t('last_hand')}</span>{/if}
        <span class="title-large">{(f ?? app).replaying ? t('replay_title') : t('hand_n', f ? f.handNo : app.session.hands + 1)}</span>
        {#if !peek && Object.keys(app.gameValues).length}
          <button class="chip" data-tour="rules" onclick={onrules}>{app.rulesName(app.gameValues)}<Icon name="dropDown" /></button>
        {/if}
      </div>
      {#if (f ?? app).replaying}<div class="body-medium dim">{t('replay_note')}</div>{/if}
      {@render scoreboard()}
      {#if !f}
        <p class="hint title-medium" class:bad={fouled} aria-live="polite">{hint}</p>
        <div class="sortline"><Toggle options={[[false, t('sort_rank')], [true, t('sort_suit')]] as [boolean, string][]} selected={vm.sortBySuit} onselect={(v) => (vm.sortBySuit = v)} /><Kbd k={km.sortLabel} /></div>
        <div class="tools">
          <button class="btn outlined" disabled={!vm.canUndo} onclick={() => vm.undo()}><Icon name="undo" />{t('undo')}<Kbd k={km.undoLabel} inline /></button>
          <button class="btn outlined" disabled={!vm.canClear} onclick={() => vm.clearRows()}><Icon name="clearAll" />{t('take_all')}<Kbd k="Del" inline /></button>
          {#if app.prev}<button class="btn outlined" data-tour="last" onclick={() => app.stepPrev()}><Icon name="history" />{t('last_hand')}<Kbd k="L" inline /></button>{/if}
        </div>
        <button class="btn filled big" data-tour="confirm" disabled={!canConfirm} onclick={() => app.confirm()}>{app.waiting ? '…' : t('confirm')}<Kbd k="Enter" inline /></button>
        <span class="grow"></span>
        <KeyList keys={keyList(app.deskKeys)} mouse={t('mouse_tips')} />
      {:else}
        {@render onward()}
        {@render keepers()}
        {@render summary()}
      {/if}
    </aside>
  </div>
{/if}

{#if help}
  <Dialog title={t('arrange_help_title')} onclose={() => (help = false)}>
    <p class="body-large help-text">{t('arrange_help_desk', keyMap(app.deskKeys).rowLabels.join(t('list_sep')))}</p>
    <p class="body-medium dim help-text">{t('mouse_tips')}</p>
    {#snippet actions()}
      <button class="btn text" onclick={() => { help = false; nav.open('tutorial'); }}>{t('tut_title')}</button>
      <button class="btn text" onclick={() => (help = false)}>{t('ok')}</button>
    {/snippet}
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
  .remarks { background: var(--secondary-container); color: var(--on-secondary-container); }
  .remarks .mistake { background: var(--error-container); color: var(--on-error-container); border-radius: 8px; padding: 2px 8px; align-self: flex-start; }
  .yours-head { display: flex; align-items: center; gap: 8px; }
  .side.wide { width: 400px; }
  .thinking { display: flex; align-items: center; gap: 10px; min-height: 40px; }
  .star { color: var(--highlight); display: contents; }
  .chip.static { cursor: default; background: var(--secondary-container); color: var(--on-secondary-container); border: none; }
  .keys { display: inline-flex; align-items: center; gap: 4px; margin-right: 8px; }
  .loading { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; padding: 32px; }
  .spinner { width: 48px; height: 48px; border-radius: 50%; border: 5px solid var(--surface-container-highest); border-top-color: var(--primary); animation: spin 0.9s linear infinite; }
  .spinner.small { width: 22px; height: 22px; border-width: 3px; }
  @keyframes spin { to { transform: rotate(360deg); } }
  .help-text { white-space: pre-line; margin: 0; }
</style>
