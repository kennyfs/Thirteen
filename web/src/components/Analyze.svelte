<!-- Analyze: pick 13 cards (or take them from somewhere), then the candidates. -->
<script lang="ts">
  import type { App } from '../lib/app.svelte';
  import { t } from '../lib/i18n.svelte';
  import * as R from '../lib/rules';
  import { BIG_BUTTON } from '../lib/sizing';
  import { ui } from '../lib/ui.svelte';
  import Card from './Card.svelte';
  import CandidateList from './CandidateList.svelte';
  import Dialog from './Dialog.svelte';
  import { nav } from '../lib/nav.svelte';
  import Icon from './Icon.svelte';
  import Rules from './Rules.svelte';
  import Menu from './Menu.svelte';
  import RecordLine from './RecordLine.svelte';
  import TitleBar from './TitleBar.svelte';
  import DeskAnalyze from './desk/DeskAnalyze.svelte';

  let { app, onrules }: { app: App; onrules: () => void } = $props();
  let pickRecord = $state(false);
  let info = $state(false);
  let gridW = $state(0);
  let gridH = $state(0);
  let pageW = $state(0);
  let pageH = $state(0);
  const wide = $derived(pageW > pageH * 1.15);
  const perLine = $derived(wide ? 13 : 7);
  const lines = $derived(wide ? 4 : 8);
  // The 52 cards as large as the width and the height allow: 1.3 times as tall as wide, or down to 1.15 on a wide page; 4 between cards and lines, more between the suits on a tall page.
  const gap = 4;
  const suitGap = $derived(wide ? 4 : 8);
  const lineH = $derived((gridH - 2 - gap * (lines - 4) - suitGap * 3) / lines);
  // (A tall page's lines are as wide as it, the cards shorter when the height is short; the grid is padded 12 at each side.)
  const cell = $derived(Math.max(20, Math.min((gridW - 24 - gap * (perLine - 1)) / perLine, wide ? lineH / 1.15 : Infinity, 110)));
  const cellH = $derived(Math.max(26, Math.min(cell * 1.3, lineH)));
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

  const records = $derived.by(() => { void app.recordCount; return app.records.all().reverse(); });
  const favorites = $derived.by(() => { void app.favoriteIds; return app.favorites.all().reverse(); });
</script>

{#snippet choices()}
  {#if app.anaRules !== null}
    <Menu label={t('this_game_rules_named', app.rulesName(app.anaRuleValues))} options={rulesOptions} selected={0} onselect={(v) => { if (v === 1) app.useCurrentRules(); }} />
  {:else if Object.keys(app.ruleValues).length}
    <button class="chip" onclick={onrules}>{app.rulesName(app.ruleValues)}<Icon name="dropDown" /></button>
  {/if}
  <Menu label={modelLabel(model)} options={app.models.map((m) => [m.path, modelLabel(m)] as [string, string])} selected={model.path} onselect={(p) => app.chooseAnalyzeModel(p)} />
{/snippet}

{#snippet go(full: boolean)}
  <button class="btn filled big go" class:full disabled={app.anaHand.size !== 13 || app.analyzing} onclick={() => app.analyze()}>
    {#if app.analyzing}<span class="spinner"></span>{:else}{app.anaHand.size < 13 ? t('pick_n', 13 - app.anaHand.size) : t('analyze_go')}{/if}
  </button>
{/snippet}

{#if nav.sub === 'rules'}
<Rules {app} onback={() => nav.back()} />
{:else if ui.desk}
<DeskAnalyze {app} {onrules} />
{:else}
<div class="page" bind:clientWidth={pageW} bind:clientHeight={pageH}>
  {#if app.analysis && app.anaPage === 1}
    <TitleBar title={t('candidates')} onback={() => nav.back()}>
      {#if pageW >= 560}<div class="mini">{#each sorted as c (c)}<Card card={c} width={22} />{/each}</div>{/if}
      <button class="btn icon text" aria-label={t('info')} onclick={() => (info = true)}><Icon name="info" /></button>
    </TitleBar>
    <CandidateList a={app.analysis} mine={app.anaMine} legacyNote={resultLegacy} />
  {:else}
    <div class="head">
      <span class="title-large">{t('nav_analyze')}</span>
      {#if wide}<span class="body-medium dim">{t('pick_13_hint')}</span>{/if}
      <span class="grow"></span>
      {@render choices()}
    </div>
    <div class="grid" bind:clientWidth={gridW} bind:clientHeight={gridH}>
      {#if gridW > 0}
        <div class="suits" style:gap="{suitGap}px">
          {#each [3, 2, 1, 0] as s (s)}
            <div class="suit" style:gap="{gap}px">
              {#each wide ? [[12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0]] : [[12, 11, 10, 9, 8, 7, 6], [5, 4, 3, 2, 1, 0]] as line, li (li)}
                <div class="gline" style:gap="{gap}px">
                  {#each line as r (r)}
                    {@const c = r * 4 + s}
                    <Card card={c} width={cell} height={cellH} selected={app.anaHand.has(c)} lift={false}
                      fill={app.anaHand.has(c) ? 'var(--picked)' : null} onclick={() => app.toggleAnalyzeCard(c)} />
                  {/each}
                </div>
              {/each}
            </div>
          {/each}
        </div>
      {/if}
    </div>
    {#if legacy}<div class="body-medium bad legacy">{legacy}</div>{/if}
    <div class="actions" style:min-height="{BIG_BUTTON + 16}px">
      <span class="headline-small" class:good={app.anaHand.size === 13}>{app.anaHand.size} / 13</span>
      {#if wide}<div class="mini grow">{#each sorted as c (c)}<Card card={c} width={22} />{/each}</div>{:else}<span class="grow"></span>{/if}
      <Menu chip={false} label={t('source')} options={[['random', t('random')], ['last', t('last_hand')], ['records', t('from_records')]]}
        onselect={(v) => { if (v === 'random') app.randomAnalyzeHand(); else if (v === 'last') app.analyzeLastHand(); else pickRecord = true; }} />
      <button class="btn outlined icon" aria-label={t('clear')} disabled={app.anaHand.size === 0} onclick={() => app.setAnalyzeHand(new Set())}><Icon name="clear" /></button>
      {#if wide}{@render go(false)}{/if}
    </div>
    {#if !wide}<div class="gorow">{@render go(true)}</div>{/if}
  {/if}
</div>
{/if}

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
{#if info}
  <Dialog title={t('info')} onclose={() => (info = false)}>
    <p class="body-large" style="margin:0">{t('analyze_info')}</p>
    {#snippet actions()}<button class="btn text" onclick={() => (info = false)}>{t('ok')}</button>{/snippet}
  </Dialog>
{/if}

<style>
  .page { height: 100%; display: flex; flex-direction: column; min-height: 0; --picked: #ffe7a6; }
  :global(:root.minimal.dark) .page { --picked: #6b5a24; }
  .head { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 4px 12px 4px 16px; min-height: 56px; flex: none; }
  .grow { flex: 1; min-width: 0; }
  .grid { flex: 1; min-height: 0; padding: 0 12px; overflow: auto; }
  .suits, .suit { display: flex; flex-direction: column; }
  .suits { align-items: center; }
  .suit { align-items: flex-start; }
  .gline { display: flex; }
  .actions { display: flex; align-items: center; gap: 8px; padding: 8px 12px; flex: none; }
  .gorow { padding: 0 12px 8px; flex: none; }
  .go { min-width: 132px; }
  .go.full { width: 100%; }
  .mini { display: flex; gap: 2px; overflow: hidden; }
  .legacy { padding: 0 16px; }
  .picker { max-height: 60vh; min-width: min(480px, 80vw); }
  .spinner { width: 24px; height: 24px; border-radius: 50%; border: 3px solid transparent; border-top-color: currentColor; animation: spin 0.9s linear infinite; }
  @keyframes spin { to { transform: rotate(360deg); } }
</style>
