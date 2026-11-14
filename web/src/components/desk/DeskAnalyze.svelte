<!--
  A computer's analysis: the cards picked in large on top (a click puts one back) with where to take a hand from and
  Analyze beside them, all 52 under them (a click picks one, a sweep with the button held picks or unpicks each
  card it passes); then the candidates (DeskCandidates). Keys: the arrows go along the 52 cards
  and Space picks, R deals a random hand, Delete clears, Enter analyzes; Esc goes back from the candidates.
-->
<script lang="ts">
  import type { App } from '../../lib/app.svelte';
  import { t } from '../../lib/i18n.svelte';
  import { nav } from '../../lib/nav.svelte';
  import * as R from '../../lib/rules';
  import { sweep } from '../../lib/sweep';
  import { ui } from '../../lib/ui.svelte';
  import Card from '../Card.svelte';
  import Dialog from '../Dialog.svelte';
  import Icon from '../Icon.svelte';
  import InfoButton from '../InfoButton.svelte';
  import Menu from '../Menu.svelte';
  import RecordLine from '../RecordLine.svelte';
  import TitleBar from '../TitleBar.svelte';
  import DeskCandidates from './DeskCandidates.svelte';
  import Kbd from './Kbd.svelte';
  import KeyHint from './KeyHint.svelte';

  let { app, onrules }: { app: App; onrules: () => void } = $props();
  let pickRecord = $state(false);
  let pageW = $state(0);
  let pageH = $state(0);
  let legacyH = $state(0);

  const sorted = $derived([...app.anaHand].sort((a, b) => b - a));
  const model = $derived(app.model(app.anaModel));
  const rulesOptions = $derived.by(() => {
    const o: [number, string][] = [[0, t('this_game_rules_named', app.rulesName(app.anaRuleValues))]];
    if (!R.same(app.anaRuleValues, app.ruleValues)) o.push([1, t('use_current_rules_named', app.rulesName(app.ruleValues))]);
    return o;
  });
  const modelLabel = (m: { name: string; builtIn: boolean }) => (m.builtIn ? t('built_in', m.name) : m.name);
  const legacy = $derived(app.legacyModels.has(model.path) && app.legacyMismatch(app.anaRuleValues) ? t('legacy_warning', modelLabel(model)) : null);
  const resultLegacy = $derived(app.analysis && app.analysis.legacyModel && app.legacyMismatch(app.analysis.rules) ? t('legacy_warning', modelLabel(model)) : null);
  const GAP = 6;
  // One size for the picked cards and the 52: the picked line, then four lines of the 52 (1.3 as tall as wide), in the
  // height left by the header, the picked cards' header, a warning on the network and the keys; at most 13 a line in
  // the width.
  const card = $derived(Math.max(30, Math.min(
    (pageW - 32 - 24 - GAP * 12) / 13,
    (pageH - 56 - 52 - 40 - 34 - 24 - GAP * 3 - (legacy ? legacyH + 10 : 0)) / (1.4 + 1.3 * 4),
    104,
  )));
  const records = $derived.by(() => { void app.recordCount; return app.records.all().reverse(); });
  const favorites = $derived.by(() => { void app.favoriteIds; return app.favorites.all().reverse(); });
  const candidates = $derived(!!app.analysis && app.anaPage === 1);
  const canGo = $derived(app.anaHand.size === 13 && !app.analyzing);

  function onKey(e: KeyboardEvent) {
    if (nav.sub === 'rules' || document.querySelector('dialog[open]')) return;
    const el = e.target as HTMLElement | null;
    if (el?.closest('input, textarea, select, [contenteditable], .menu')) return;
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    const k = e.key;
    if (candidates) {
      if (k === 'Escape') { nav.back(); e.preventDefault(); }
      return;
    }
    if (k === 'Enter' && canGo) { app.analyze(); e.preventDefault(); return; }
    if (k.toLowerCase() === 'r') { app.randomAnalyzeHand(); e.preventDefault(); return; }
    if (k === 'Delete' || k === 'Backspace') { app.setAnalyzeHand(new Set()); e.preventDefault(); return; }
    if (k.startsWith('Arrow')) {
      const cards = [...document.querySelectorAll<HTMLElement>('.deck [data-card]')];
      const i = cards.indexOf(document.activeElement as HTMLElement);
      const step = k === 'ArrowRight' ? 1 : k === 'ArrowLeft' ? -1 : k === 'ArrowDown' ? 13 : -13;
      const next = i < 0 ? 0 : i + step;
      if (next >= 0 && next < cards.length) cards[next].focus();
      e.preventDefault();
    }
  }
</script>

<svelte:window onkeydown={onKey} />

<div class="page" bind:clientWidth={pageW} bind:clientHeight={pageH}>
  {#if candidates && app.analysis}
    <TitleBar title={t('candidates')} onback={() => nav.back()}>
      <div class="hand">{#each [...app.analysis.cands[0].arrangement.flat()].sort((a, b) => b - a) as c (c)}<Card card={c} width={30} />{/each}</div>
      {#if ui.showKeys}<span class="body-small dim keys"><Kbd k="Esc" />{t('k_back')}</span>{/if}
      <InfoButton text={t('analyze_info')} />
    </TitleBar>
    <DeskCandidates a={app.analysis} mine={app.anaMine} legacyNote={resultLegacy} />
  {:else if pageW > 0}
    <div class="head">
      <span class="title-large">{t('nav_analyze')}</span>
      <span class="body-medium dim">{t('pick_13_hint')}</span>
      <span class="grow"></span>
      {#if app.anaRules !== null}
        <Menu label={t('this_game_rules_named', app.rulesName(app.anaRuleValues))} options={rulesOptions} selected={0} onselect={(v) => { if (v === 1) app.useCurrentRules(); }} />
      {:else if Object.keys(app.ruleValues).length}
        <button class="chip" onclick={onrules}>{app.rulesName(app.ruleValues)}<Icon name="dropDown" /></button>
      {/if}
      <Menu label={modelLabel(model)} options={app.models.map((m) => [m.path, modelLabel(m)] as [string, string])} selected={model.path} onselect={(p) => app.chooseAnalyzeModel(p)} />
    </div>
    <section class="tray">
      <div class="tray-head">
        <span class="headline-small" class:good={app.anaHand.size === 13}>{app.anaHand.size} / 13</span>
        <span class="body-medium dim">{t('tray_tip')}</span>
        <span class="grow"></span>
        <button class="btn outlined" onclick={() => app.randomAnalyzeHand()}><Icon name="replay" />{t('random')}<Kbd k="R" inline /></button>
        <button class="btn outlined" disabled={!app.result} onclick={() => app.analyzeLastHand()}>{t('from_last')}</button>
        <button class="btn outlined" disabled={app.recordCount === 0} onclick={() => (pickRecord = true)}>{t('from_records')}</button>
        <button class="btn outlined" disabled={app.anaHand.size === 0} onclick={() => app.setAnalyzeHand(new Set())}><Icon name="clear" />{t('clear')}<Kbd k="Del" inline /></button>
        <button class="btn filled go" disabled={!canGo} onclick={() => app.analyze()}>
          {#if app.analyzing}<span class="spinner"></span>{:else}{app.anaHand.size < 13 ? t('pick_n', 13 - app.anaHand.size) : t('analyze_go')}<Kbd k="Enter" inline />{/if}
        </button>
      </div>
      <div class="picked" style:gap="{GAP}px">
        {#each sorted as c (c)}<Card card={c} width={card} onclick={() => app.toggleAnalyzeCard(c)} />{/each}
        {#each Array(13 - sorted.length) as _, i (i)}
          <div class="slot" style:width="{card}px" style:height="{card * 1.4}px" style:border-radius="{card * 0.14}px"></div>
        {/each}
      </div>
      {#if legacy}<div class="body-medium bad" bind:offsetHeight={legacyH}>{legacy}</div>{/if}
    </section>
    <div class="deck" style:gap="{GAP}px" use:sweep={{ isOn: (c) => app.anaHand.has(c), set: (c, on) => app.setAnalyzeCard(c, on) }}>
      {#each [3, 2, 1, 0] as s (s)}
        <div class="line" style:gap="{GAP}px">
          {#each [12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0] as r (r)}
            {@const c = r * 4 + s}
            <Card card={c} width={card} height={card * 1.3} selected={app.anaHand.has(c)} lift={false}
              fill={app.anaHand.has(c) ? 'var(--picked)' : null} onclick={() => app.toggleAnalyzeCard(c)} />
          {/each}
        </div>
      {/each}
    </div>
    <div class="keyline body-small dim">
      <KeyHint />
      {#if ui.showKeys}
      <span><Kbd k="←" /><Kbd k="→" /><Kbd k="↑" /><Kbd k="↓" /><Kbd k="Space" /> {t('k_deck')}</span>
      <span><Kbd k="R" /> {t('k_random')}</span>
      <span><Kbd k="Del" /> {t('k_clear')}</span>
      <span><Kbd k="Enter" /> {t('k_start')}</span>
      {/if}
    </div>
  {/if}
</div>

{#if pickRecord}
  <Dialog title={t('from_records')} onclose={() => (pickRecord = false)}>
    <div class="picker">
      {#if favorites.length}
        <div class="section-header">{t('favorites_title')}</div>
        {#each favorites as r (r.id)}<RecordLine {r} max={ui.sizes.record} onclick={() => { app.analyzeRecord(r); pickRecord = false; }} />{/each}
        <div class="section-header">{t('records_list')}</div>
      {/if}
      {#each records as r (r.id)}<RecordLine {r} max={ui.sizes.record} onclick={() => { app.analyzeRecord(r); pickRecord = false; }} />{/each}
    </div>
    {#snippet actions()}<button class="btn text" onclick={() => (pickRecord = false)}>{t('cancel')}</button>{/snippet}
  </Dialog>
{/if}

<style>
  .page { height: 100%; display: flex; flex-direction: column; min-height: 0; --picked: #ffe7a6; }
  :global(:root.minimal.dark) .page { --picked: #6b5a24; }
  .head { display: flex; align-items: center; gap: 10px; padding: 4px 12px 4px 16px; min-height: 56px; flex: none; }
  .grow { flex: 1; min-width: 0; }
  .tray { margin: 0 12px; padding: 10px 16px 16px; border-radius: 16px; background: var(--surface-container-low); display: flex; flex-direction: column; gap: 10px; flex: none; }
  .tray-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
  .go { min-width: 150px; }
  .picked { display: flex; justify-content: center; }
  .slot { flex: none; border: 1.5px dashed var(--outline); }
  .deck { flex: 1; min-height: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 12px; }
  .line { display: flex; }
  .keyline { display: flex; flex-wrap: wrap; justify-content: center; gap: 6px 22px; padding: 0 12px 12px; flex: none; }
  .keyline span { display: inline-flex; align-items: center; gap: 3px; }
  .hand { display: flex; gap: 3px; margin-right: 12px; }
  .keys { display: inline-flex; align-items: center; gap: 4px; }
  .picker { max-height: 60vh; min-width: min(560px, 80vw); }
  .spinner { width: 22px; height: 22px; border-radius: 50%; border: 3px solid transparent; border-top-color: currentColor; animation: spin 0.9s linear infinite; }
  @keyframes spin { to { transform: rotate(360deg); } }
</style>
