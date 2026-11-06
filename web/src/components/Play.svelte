<!--
  Practice: the hand being set, then its result and the analysis of the player's hand. A computer's is DeskPlay.
-->
<script lang="ts">
  import type { App } from '../lib/app.svelte';
  import { t } from '../lib/i18n.svelte';
  import { signed, ui } from '../lib/ui.svelte';
  import Arranging from './Arranging.svelte';
  import CandidateList from './CandidateList.svelte';
  import { nav } from '../lib/nav.svelte';
  import Icon from './Icon.svelte';
  import Rules from './Rules.svelte';
  import SeatResults from './SeatResults.svelte';
  import TitleBar from './TitleBar.svelte';
  import DeskPlay from './desk/DeskPlay.svelte';

  let { app, onrules }: { app: App; onrules: () => void } = $props();
  const seatName = (i: number) => (i === 0 ? t('you') : t('seat_pos', i));
  // The finished hand shown: the one just played, or (peek) the one before the hand in play.
  const peek = $derived(app.peek);
  const f = $derived(peek ? app.prev : app.current);
  const wheel = $derived((f?.values ?? app.gameValues).wheel ?? 0);
  const saved = $derived(!!f?.record && app.favoriteIds.has(f.record.id));
  const legacy = $derived(f?.analysis && f.analysis.legacyModel && app.legacyMismatch(f.analysis.rules)
    ? t('legacy_warning', app.model(app.anaModel).name) : null);
  const favorite = () => { if (f?.record) app.toggleFavorite(f.record); };
  const showAnalysis = () => (peek ? app.showPrevAnalysis() : app.showGameAnalysis());

</script>


{#snippet keepers(iconsOnly: boolean)}
  {#if iconsOnly}
    <button class="btn icon text" aria-label={t(saved ? 'favorite_remove' : 'favorite_add')} disabled={!f?.record}
      onclick={favorite}><span class:star={saved}><Icon name={saved ? 'star' : 'starBorder'} /></span></button>
    {#if !peek}<button class="btn icon text" aria-label={t('replay_hand')} onclick={() => app.replay()}><Icon name="replay" /></button>{/if}
  {:else}
    <button class="btn outlined grow" disabled={!f?.record} onclick={favorite}>
      <span class:star={saved}><Icon name={saved ? 'star' : 'starBorder'} /></span>{t(saved ? 'favorite_saved' : 'favorite_short')}
    </button>
    {#if !peek}<button class="btn outlined grow" onclick={() => app.replay()}><Icon name="replay" />{t('replay_short')}</button>{/if}
  {/if}
{/snippet}

{#snippet resultActions(compact: boolean)}
  <button class="btn tonal" class:big={!compact} class:grow={!compact} onclick={showAnalysis}>
    {#if compact}<Icon name="analytics" />{/if}{t('analyze_my_hand')}
  </button>
  {@render onward(compact)}
{/snippet}

{#snippet onward(compact: boolean)}
  {#if peek}
    <button class="btn filled" class:big={!compact} class:grow={!compact} onclick={() => nav.back()}>{t('back_to_hand')}</button>
  {:else}
    <button class="btn filled" class:big={!compact} class:grow={!compact} onclick={() => app.dealNow()}>{t('next_hand')}</button>
  {/if}
{/snippet}

{#if nav.sub === 'rules'}
  <Rules {app} onback={() => nav.back()} />
{:else if ui.desk}
<DeskPlay {app} {onrules} />
{:else}
<div class="page">
  {#if f && (peek ? peek === 2 : app.resultTab === 1)}
    <TitleBar title={peek ? `${t('analyze_my_hand')} · ${t('last_hand')}` : t('analyze_my_hand')} onback={() => nav.back()}>
      {@render onward(true)}
    </TitleBar>
    {#if f.analysis}<CandidateList a={f.analysis} mine={f.result.rows[0]} legacyNote={legacy} />
    {:else}<div class="loading"><div class="spinner"></div><div class="body-large dim">{t('analyzing')}</div></div>{/if}
  {:else if f}
    {@const r = f.result}
    {@const mine = r.score.result[0]}
    <div class="result-head" class:wide={ui.wide}>
      {#if peek}<span class="chip static">{t('last_hand')}</span>{/if}
      <span class="title-large nowrap">{f.replaying ? t('replay_title') : t('hand_n', f.handNo)}</span>
      <span class="headline-small nowrap" class:good={mine > 0} class:bad={mine < 0}>{t('this_hand', signed(mine))}</span>
      <span class="body-large dim ellipsis grow">{t('total_so_far', signed(f.totals[0]))}</span>
      {#if ui.wide}{@render keepers(true)}{@render resultActions(true)}{/if}
    </div>
    <div class="felt result-felt" class:wide={ui.wide}>
      <SeatResults rows={r.rows} score={r.score} {wheel} names={[0, 1, 2, 3].map(seatName)}
        notes={[null, ...f.seats.map((s) => app.seatName(s))]} totals={f.totals} />
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
          <button class="chip" data-tour="rules" onclick={onrules}>{app.rulesName(app.gameValues)}<Icon name="dropDown" /></button>
        {/if}
        {#if app.prev}
          <button class="btn text lastbtn" data-tour="last" onclick={() => app.stepPrev()}><Icon name="history" />{t('last_hand')}</button>
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
  .chip.static { cursor: default; background: var(--secondary-container); color: var(--on-secondary-container); border: none; }
  .lastbtn { padding: 0 10px; min-height: 36px; }
  .loading { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; padding: 32px; }
  .spinner { width: 48px; height: 48px; border-radius: 50%; border: 5px solid var(--surface-container-highest); border-top-color: var(--primary); animation: spin 0.9s linear infinite; }
  @keyframes spin { to { transform: rotate(360deg); } }
</style>
