<!--
  Practice: the hand being set, then its result and the analysis of the player's hand. On a computer (ui.desk) the
  table takes the window beside a side panel, which has the header, the scores and the buttons, with their keys.
-->
<script lang="ts">
  import type { App } from '../lib/app.svelte';
  import { ROW_SIZES } from '../lib/cards';
  import { t, ta } from '../lib/i18n.svelte';
  import { signed, ui } from '../lib/ui.svelte';
  import Arranging from './Arranging.svelte';
  import CandidateList from './CandidateList.svelte';
  import Dialog from './Dialog.svelte';
  import Toggle from './Toggle.svelte';
  import { nav } from '../lib/nav.svelte';
  import Icon from './Icon.svelte';
  import Rules from './Rules.svelte';
  import SeatResults from './SeatResults.svelte';
  import TitleBar from './TitleBar.svelte';

  let { app, onrules }: { app: App; onrules: () => void } = $props();
  const seatName = (i: number) => (i === 0 ? t('you') : t('seat_pos', i));
  const wheel = $derived(app.gameValues.wheel ?? 0);
  const saved = $derived(!!app.lastRecord && app.favoriteIds.has(app.lastRecord.id));
  const legacy = $derived(app.gameAnalysis && app.gameAnalysis.legacyModel && app.legacyMismatch(app.gameAnalysis.rules)
    ? t('legacy_warning', app.model(app.anaModel).name) : null);

  // ---- A computer's side panel ----
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
  const toggleAnalysis = () => (app.resultTab === 1 ? nav.back() : app.showGameAnalysis());
  const favorite = () => app.lastRecord && app.toggleFavorite(app.lastRecord);

  // The result's keys (the arranging page's are in Arranging.svelte).
  function onKey(e: KeyboardEvent) {
    if (!app.result || !ui.desk || nav.sub === 'rules') return;
    const el = e.target as HTMLElement | null;
    if (el?.closest('input, textarea, select, [contenteditable]') || document.querySelector('dialog[open]')) return;
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    const k = e.key.toLowerCase();
    if (k === 'enter' || k === 'n') app.dealNow();
    else if (k === 'a') toggleAnalysis();
    else if (k === 'f') favorite();
    else if (k === 'r') app.replay();
    else return;
    e.preventDefault();
  }
</script>

<svelte:window onkeydown={onKey} />

{#snippet kbd(k: string)}<kbd>{k}</kbd>{/snippet}

{#snippet scoreboard()}
  {@const r = app.result}
  <div class="board">
    {#if r}<div class="seat cols body-small dim"><span class="who"></span><span class="delta">{t('col_hand')}</span><span class="total">{t('col_total')}</span></div>{/if}
    {#each app.session.totals as x, i (i)}
      {@const d = r ? r.score.result[i] : null}
      <div class="seat" class:me={i === 0}>
        <span class="who"><span class="title-small">{seatName(i)}</span>
          {#if i > 0 && app.seats[i - 1]}<span class="body-small dim ellipsis">{app.seatName(app.seats[i - 1])}</span>{/if}</span>
        {#if d !== null}<span class="title-medium bold delta" class:good={d > 0} class:bad={d < 0}>{signed(d)}</span>{/if}
        <span class="body-large total" class:good={i === 0 && x > 0} class:bad={i === 0 && x < 0}>{signed(x)}</span>
      </div>
    {/each}
  </div>
{/snippet}

{#snippet side()}
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
      <p class="hint body-large" class:bad={fouled} aria-live="polite">{hint}</p>
      <Toggle options={[[false, t('sort_rank')], [true, t('sort_suit')]] as [boolean, string][]} selected={vm.sortBySuit} onselect={(v) => (vm.sortBySuit = v)} />
      <div class="tools">
        <button class="btn outlined" disabled={!vm.canUndo} title={t('key_hint', t('undo'), 'Z')} onclick={() => vm.undo()}><Icon name="undo" />{t('undo')}</button>
        <button class="btn outlined" disabled={!vm.canClear} title={t('key_hint', t('take_all'), 'Delete')} onclick={() => vm.clearRows()}><Icon name="clearAll" />{t('take_all')}</button>
        <button class="btn outlined icon" aria-label={t('help')} title={t('key_hint', t('help'), '?')} onclick={() => (help = true)}><Icon name="help" /></button>
      </div>
      <span class="grow"></span>
      <button class="btn filled big" disabled={!canConfirm} onclick={() => app.confirm()}>{app.waiting ? '…' : t('confirm')}{@render kbd('Enter')}</button>
      <p class="keys body-small dim">{t('keys_arrange')}</p>
    {:else}
      <div class="tools col">
        <button class="btn tonal" onclick={toggleAnalysis}><Icon name="analytics" />{app.resultTab === 1 ? t('show_result') : t('analyze_my_hand')}{@render kbd('A')}</button>
        <div class="tools">
          <button class="btn outlined grow" disabled={!app.lastRecord} onclick={favorite}>
            <span class:star={saved}><Icon name={saved ? 'star' : 'starBorder'} /></span>{t(saved ? 'favorite_saved' : 'favorite_short')}{@render kbd('F')}
          </button>
          <button class="btn outlined grow" onclick={() => app.replay()}><Icon name="replay" />{t('replay_short')}{@render kbd('R')}</button>
        </div>
      </div>
      <span class="grow"></span>
      <button class="btn filled big" onclick={() => app.dealNow()}>{t('next_hand')}{@render kbd('Enter')}</button>
      <p class="keys body-small dim">{t('keys_result')}</p>
    {/if}
  </aside>
{/snippet}

{#snippet keepers(iconsOnly: boolean)}
  {#if iconsOnly}
    <button class="btn icon text" aria-label={t(saved ? 'favorite_remove' : 'favorite_add')} disabled={!app.lastRecord}
      onclick={() => app.lastRecord && app.toggleFavorite(app.lastRecord)}><span class:star={saved}><Icon name={saved ? 'star' : 'starBorder'} /></span></button>
    <button class="btn icon text" aria-label={t('replay_hand')} onclick={() => app.replay()}><Icon name="replay" /></button>
  {:else}
    <button class="btn outlined grow" disabled={!app.lastRecord} onclick={() => app.lastRecord && app.toggleFavorite(app.lastRecord)}>
      <span class:star={saved}><Icon name={saved ? 'star' : 'starBorder'} /></span>{t(saved ? 'favorite_saved' : 'favorite_short')}
    </button>
    <button class="btn outlined grow" onclick={() => app.replay()}><Icon name="replay" />{t('replay_short')}</button>
  {/if}
{/snippet}

{#snippet resultActions(compact: boolean)}
  <button class="btn tonal" class:big={!compact} class:grow={!compact} onclick={() => app.showGameAnalysis()}>
    {#if compact}<Icon name="analytics" />{/if}{t('analyze_my_hand')}
  </button>
  <button class="btn filled" class:big={!compact} class:grow={!compact} onclick={() => app.dealNow()}>{t('next_hand')}</button>
{/snippet}

{#if nav.sub === 'rules'}
  <Rules {app} onback={() => nav.back()} />
{:else if ui.desk}
<div class="desk">
  <div class="desk-main">
    {#if app.result && app.resultTab === 1}
      <TitleBar title={t('analyze_my_hand')} onback={() => nav.back()} />
      {#if app.gameAnalysis}<CandidateList a={app.gameAnalysis} mine={app.result.rows[0]} legacyNote={legacy} />
      {:else}<div class="loading"><div class="spinner"></div><div class="body-large dim">{t('analyzing')}</div></div>{/if}
    {:else if app.result}
      <div class="felt desk-result">
        <SeatResults rows={app.result.rows} score={app.result.score} {wheel} names={[0, 1, 2, 3].map(seatName)}
          notes={[null, ...app.seats.map((s) => app.seatName(s))]} totals={app.session.totals} />
      </div>
    {:else if app.ready}
      <Arranging vm={app.play} {wheel} busy={app.waiting} confirmLabel={t('confirm')} onconfirm={() => app.confirm()} bare bind:help />
    {:else}
      <div class="loading"><div class="spinner"></div></div>
    {/if}
  </div>
  {@render side()}
</div>
{#if help}
  <Dialog title={t('arrange_help_title')} onclose={() => (help = false)}>
    <p class="body-large help-text">{t('arrange_help_desk')}</p>
    {#snippet actions()}<button class="btn text" onclick={() => (help = false)}>{t('ok')}</button>{/snippet}
  </Dialog>
{/if}
{:else}
<div class="page">
  {#if app.result && app.resultTab === 1}
    <TitleBar title={t('analyze_my_hand')} onback={() => nav.back()}>
      <button class="btn filled" onclick={() => app.dealNow()}>{t('next_hand')}</button>
    </TitleBar>
    {#if app.gameAnalysis}<CandidateList a={app.gameAnalysis} mine={app.result.rows[0]} legacyNote={legacy} />
    {:else}<div class="loading"><div class="spinner"></div><div class="body-large dim">{t('analyzing')}</div></div>{/if}
  {:else if app.result}
    {@const r = app.result}
    {@const mine = r.score.result[0]}
    <div class="result-head" class:wide={ui.wide}>
      <span class="title-large nowrap">{app.replaying ? t('replay_title') : t('hand_n', app.session.hands)}</span>
      <span class="headline-small nowrap" class:good={mine > 0} class:bad={mine < 0}>{t('this_hand', signed(mine))}</span>
      <span class="body-large dim ellipsis grow">{t('total_so_far', signed(app.session.totals[0]))}</span>
      {#if ui.wide}{@render keepers(true)}{@render resultActions(true)}{/if}
    </div>
    <div class="felt result-felt" class:wide={ui.wide}>
      <SeatResults rows={r.rows} score={r.score} {wheel} names={[0, 1, 2, 3].map(seatName)}
        notes={[null, ...app.seats.map((s) => app.seatName(s))]} totals={app.session.totals} />
    </div>
    {#if !ui.wide}
      <div class="result-actions">
        <div class="arow">{@render keepers(false)}</div>
        <div class="arow">{@render resultActions(false)}</div>
      </div>
    {/if}
  {:else}
    <div class="practice-head" class:one={ui.w >= 600}>
      <div class="titleline">
        <span class="title-large">{app.replaying ? t('replay_title') : t('hand_n', app.session.hands + 1)}</span>
        {#if Object.keys(app.gameValues).length}
          <button class="chip" onclick={onrules}>{app.rulesName(app.gameValues)}<Icon name="dropDown" /></button>
        {/if}
      </div>
      <div class="scores">
        {#each app.session.totals as x, i (i)}
          <span class="score" class:me={i === 0}>
            <span class="body-medium dim">{seatName(i)}</span>
            <span class="title-medium bold" class:bad={i === 0 && x < 0} class:good={i === 0 && x > 0}>{signed(x)}</span>
          </span>
        {/each}
      </div>
    </div>
    {#if app.ready}
      <Arranging vm={app.play} {wheel} busy={app.waiting} confirmLabel={t('confirm')} onconfirm={() => app.confirm()} />
    {:else}
      <div class="loading"><div class="spinner"></div></div>
    {/if}
  {/if}
</div>
{/if}

<style>
  .page { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .practice-head { padding: 2px 12px 4px 16px; display: flex; flex-direction: column; flex: none; }
  .practice-head.one { flex-direction: row; align-items: center; justify-content: space-between; gap: 10px; }
  .titleline { display: flex; align-items: center; gap: 10px; min-height: 48px; }
  .scores { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; }
  .score { display: inline-flex; align-items: baseline; gap: 4px; padding: 3px 2px; border-radius: 10px; }
  .score.me { background: var(--secondary-container); padding: 3px 8px; }
  .result-head { display: flex; align-items: center; gap: 10px; padding: 4px 12px 4px 16px; min-height: 56px; flex: none; }
  .result-head.wide { min-height: 48px; padding: 0 12px 0 16px; }
  .grow { flex: 1; min-width: 0; }
  .result-felt { flex: 1; min-height: 0; margin: 0 4px; padding: 4px 2px; }
  .result-felt.wide { margin: 0 12px 6px 8px; padding: 6px; }
  .result-actions { padding: 8px 12px; display: flex; flex-direction: column; gap: 4px; flex: none; }
  .arow { display: flex; gap: 8px; }
  .star { color: var(--highlight); display: contents; }
  .loading { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; padding: 32px; }
  .spinner { width: 48px; height: 48px; border-radius: 50%; border: 5px solid var(--surface-container-highest); border-top-color: var(--primary); animation: spin 0.9s linear infinite; }
  @keyframes spin { to { transform: rotate(360deg); } }
  .desk { height: 100%; display: flex; min-height: 0; }
  .desk-main { flex: 1; min-width: 0; display: flex; flex-direction: column; padding-top: 12px; }
  .desk-result { flex: 1; min-height: 0; margin: 0 12px 12px; padding: 8px; }
  .side { flex: none; width: 320px; display: flex; flex-direction: column; gap: 12px; padding: 12px 16px 16px 4px; min-height: 0; overflow: auto; }
  .side-head { display: flex; align-items: center; gap: 10px; min-height: 40px; flex-wrap: wrap; }
  .board { display: flex; flex-direction: column; gap: 2px; background: var(--surface-container-low); border-radius: 16px; padding: 6px; }
  .seat { display: flex; align-items: center; gap: 8px; padding: 6px 10px; border-radius: 10px; }
  .seat.me { background: var(--secondary-container); }
  .who { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  .delta { min-width: 3.2em; text-align: right; }
  .total { min-width: 3em; text-align: right; }
  .hint { margin: 0; min-height: 2.9em; }
  .tools { display: flex; gap: 8px; }
  .tools.col { flex-direction: column; }
  .tools .btn:not(.icon) { flex: 1; min-width: 0; }
  .side .big { width: 100%; justify-content: center; }
  .keys { margin: 0; }
  kbd { font-family: inherit; font-size: 0.72rem; font-weight: 500; line-height: 1; padding: 3px 5px; border-radius: 5px; margin-left: 6px;
    opacity: 0.75; border: 1px solid currentColor; }
  .seat.cols { padding-top: 2px; padding-bottom: 0; }
  .desk-result :global(.results) { display: flex; flex-direction: column; justify-content: safe center; }
  .help-text { white-space: pre-line; margin: 0; }
</style>
