<!--
  The scoring rules: a preset, then each value. The two that change the play (strict rows and the wheel) are kept apart
  from the ones that only change the points. Leaving the page puts the rules into practice at once.
-->
<script lang="ts">
  import { onDestroy } from 'svelte';
  import type { App } from '../lib/app.svelte';
  import { t, ta } from '../lib/i18n.svelte';
  import * as R from '../lib/rules';
  import Switch from './Switch.svelte';
  import TitleBar from './TitleBar.svelte';

  let { app, onback }: { app: App; onback: () => void } = $props();
  onDestroy(() => app.rulesClosed());
  const spec = $derived(app.ruleSpec);
  const values = $derived(app.ruleValues);
  const presets = $derived([...R.OFFERED, ...(R.OFFERED.includes(spec.preset) ? [] : [spec.preset])]);
  const legacy = $derived([...new Set([...app.seatChoices.filter((s) => s.kind === 'NET').map((s) => s.model), app.anaModel])]
    .map((p) => app.model(p)).filter((m) => app.legacyModels.has(m.path)));
  const mismatch = $derived(app.legacyMismatch(values));
  const modelLabel = (m: { name: string; builtIn: boolean }) => (m.builtIn ? t('built_in', m.name) : m.name);
</script>

{#snippet numberRow(label: string, name: string)}
  {@const v = values[name] ?? 0}
  {@const [lo, hi] = R.range(name, values)}
  <div class="num">
    <span class="grow"><span class="body-large">{label}</span><br /><span class="body-small dim">{t('rule_range', R.fmt(lo), R.fmt(hi))}</span></span>
    <button class="btn outlined icon" aria-label={t('decrease')} disabled={v <= lo} onclick={() => app.setRule(name, Math.max(lo, v - 1))}>−</button>
    <span class="title-medium value">{R.fmt(v)}</span>
    <button class="btn outlined icon" aria-label={t('increase')} disabled={v >= hi} onclick={() => app.setRule(name, Math.min(hi, v + 1))}>+</button>
  </div>
{/snippet}

<div class="page">
  <TitleBar title={t('nav_rules')} {onback} />
  <div class="scroll"><div class="readable">
    <p class="body-medium dim note">{t('rules_next_hand')}</p>
    <div class="presets" role="radiogroup">
      {#each presets as p (p)}
        {@const i = R.PRESETS.indexOf(p)}
        {@const on = spec.preset === p}
        <button class="preset" class:on role="radio" aria-checked={on} onclick={() => app.choosePreset(p)}>
          <span class="radio" class:on></span>
          <span class="grow"><span class="title-medium">{ta('preset_names')[i]}{on && Object.keys(spec.overrides).length ? t('rules_changed') : ''}</span><br />
            <span class="body-medium dim">{ta('preset_notes')[i]}</span></span>
        </button>
      {/each}
    </div>
    <div class="note"><button class="btn outlined" disabled={Object.keys(spec.overrides).length === 0} onclick={() => app.choosePreset(spec.preset)}>{t('restore_preset')}</button></div>
    {#each legacy as m (m.path)}<p class="body-medium note" class:bad={mismatch} class:dim={!mismatch}>{t('legacy_warning', modelLabel(m))}</p>{/each}
    {#if Object.keys(values).length}
      <div class="section-header">{t('rules_play')}</div>
      <p class="body-medium dim note">{t('rules_play_note')}</p>
      <Switch label={t('strict_rows')} on={values.strict === 1} onchange={(v) => app.setRule('strict', v ? 1 : 0)} />
      <div class="body-large note">{t('wheel')}</div>
      <div role="radiogroup">
        {#each ta('wheel_options') as label, i (i)}
          <button class="opt" role="radio" aria-checked={(values.wheel ?? 0) === i} onclick={() => app.setRule('wheel', i)}>
            <span class="radio" class:on={(values.wheel ?? 0) === i}></span><span class="body-large">{label}</span>
          </button>
        {/each}
      </div>
      <div class="section-header">{t('rules_scoring')}</div>
      {#each ta('point_labels') as label, i (i)}{@render numberRow(label, R.POINTS[i])}{/each}
      <div class="section-header">{t('rules_royalties')}</div>
      {#each ta('royalty_labels') as label, i (i)}{@render numberRow(label, R.ROYALTIES[i])}{/each}
      <Switch label={t('on_win')} on={values.on_win === 1} onchange={(v) => app.setRule('on_win', v ? 1 : 0)} />
    {/if}
  </div></div>
</div>

<style>
  .page { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .scroll { flex: 1; overflow: auto; padding-bottom: 24px; }
  .readable { max-width: 720px; margin: 0 auto; }
  .note { padding: 4px 16px; margin: 4px 0; }
  .presets { display: flex; flex-direction: column; gap: 8px; padding: 0 12px; }
  .preset { display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: 12px; border: 2px solid transparent; text-align: left;
    background: var(--surface-container-low); color: inherit; cursor: pointer; font: inherit; }
  .preset.on { background: var(--secondary-container); border-color: var(--primary); }
  .grow { flex: 1; min-width: 0; }
  .radio { flex: none; width: 20px; height: 20px; border-radius: 50%; border: 2px solid var(--on-surface-variant); box-sizing: border-box; }
  .radio.on { border-color: var(--primary); background: radial-gradient(var(--primary) 0 4.5px, transparent 5px); }
  .opt { display: flex; align-items: center; gap: 12px; min-height: 52px; padding: 0 16px; width: 100%; background: none; border: none; color: inherit; cursor: pointer; font: inherit; }
  .num { display: flex; align-items: center; gap: 8px; min-height: 56px; padding: 0 12px 0 16px; }
  .value { min-width: 2.5em; text-align: center; }
</style>
