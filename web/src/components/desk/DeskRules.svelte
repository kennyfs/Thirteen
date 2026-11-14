<!--
  A computer's rules page: the presets side by side, the two rules that change the play on one line, and the points in
  a grid of fields that take a number typed (or − and +). Rules.svelte shows it, and puts the rules into practice when
  it is left.
-->
<script lang="ts">
  import type { App } from '../../lib/app.svelte';
  import { t, ta } from '../../lib/i18n.svelte';
  import * as R from '../../lib/rules';
  import InfoButton from '../InfoButton.svelte';
  import Switch from '../Switch.svelte';
  import TitleBar from '../TitleBar.svelte';

  let { app, onback = null }: { app: App; onback?: (() => void) | null } = $props();
  const spec = $derived(app.ruleSpec);
  const values = $derived(app.ruleValues);
  const presets = $derived([...R.OFFERED, ...(R.OFFERED.includes(spec.preset) ? [] : [spec.preset])]);
  const legacy = $derived([...new Set([...app.seatChoices.filter((s) => s.kind === 'NET').map((s) => s.model), app.anaModel])]
    .map((p) => app.model(p)).filter((m) => app.legacyModels.has(m.path)));
  const mismatch = $derived(app.legacyMismatch(values));
  const modelLabel = (m: { name: string; builtIn: boolean }) => (m.builtIn ? t('built_in', m.name) : m.name);

  function typed(name: string, e: Event) {
    const el = e.currentTarget as HTMLInputElement;
    const [lo, hi] = R.range(name, values);
    const v = Number(el.value);
    if (el.value.trim() === '' || !Number.isFinite(v)) { el.value = R.fmt(values[name] ?? 0); return; }
    const x = Math.max(lo, Math.min(hi, Math.round(v)));
    el.value = R.fmt(x);
    if (x !== values[name]) app.setRule(name, x);
  }
</script>

{#snippet field(label: string, name: string)}
  {@const v = values[name] ?? 0}
  {@const [lo, hi] = R.range(name, values)}
  <div class="field">
    <span class="body-large">{label}</span>
    <span class="body-small dim">{t('rule_range', R.fmt(lo), R.fmt(hi))}</span>
    <div class="stepper">
      <button class="step" aria-label={t('decrease')} disabled={v <= lo} onclick={() => app.setRule(name, Math.max(lo, v - 1))}>−</button>
      <input type="number" inputmode="numeric" min={lo} max={hi} value={R.fmt(v)} aria-label={label}
        onchange={(e) => typed(name, e)} onkeydown={(e) => { if (e.key === 'Enter') (e.currentTarget as HTMLInputElement).blur(); }} />
      <button class="step" aria-label={t('increase')} disabled={v >= hi} onclick={() => app.setRule(name, Math.min(hi, v + 1))}>+</button>
    </div>
  </div>
{/snippet}

<div class="page">
  <TitleBar title={t('nav_rules')} {onback} />
  <div class="scroll"><div class="content">
    <p class="body-medium dim">{t('rules_next_hand')}</p>
    <div class="presets" role="radiogroup" aria-label={t('presets_title')}>
      {#each presets as p (p)}
        {@const i = R.PRESETS.indexOf(p)}
        {@const on = spec.preset === p}
        <button class="preset" class:on role="radio" aria-checked={on} onclick={() => app.choosePreset(p)}>
          <span class="top"><span class="radio" class:on></span><span class="title-medium">{ta('preset_names')[i]}{on && Object.keys(spec.overrides).length ? t('rules_changed') : ''}</span></span>
          <span class="body-medium dim">{ta('preset_notes')[i]}</span>
        </button>
      {/each}
    </div>
    <div><button class="btn outlined" disabled={Object.keys(spec.overrides).length === 0} onclick={() => app.choosePreset(spec.preset)}>{t('restore_preset')}</button></div>
    {#each legacy as m (m.path)}<p class="body-medium" class:bad={mismatch} class:dim={!mismatch}>{t('legacy_warning', modelLabel(m))}</p>{/each}
    {#if Object.keys(values).length}
      <section>
        <div class="section-title with-help">{t('rules_play')}<InfoButton small text={t('rules_play_note')} title={t('rules_play')} /></div>
        <div class="play">
          <div class="switch"><Switch label={t('strict_rows')} on={values.strict === 1} onchange={(v) => app.setRule('strict', v ? 1 : 0)} /></div>
          <div class="wheel">
            <span class="body-large">{t('wheel')}</span>
            <div class="opts" role="radiogroup">
              {#each ta('wheel_options') as label, i (i)}
                <button class="opt" role="radio" aria-checked={(values.wheel ?? 0) === i} onclick={() => app.setRule('wheel', i)}>
                  <span class="radio" class:on={(values.wheel ?? 0) === i}></span><span class="body-large">{label}</span>
                </button>
              {/each}
            </div>
          </div>
        </div>
      </section>
      <section>
        <div class="section-title">{t('rules_scoring')}</div>
        <div class="grid">{#each ta('point_labels') as label, i (i)}{@render field(label, R.POINTS[i])}{/each}</div>
      </section>
      <section>
        <div class="section-title">{t('rules_royalties')}</div>
        <div class="grid">{#each ta('royalty_labels') as label, i (i)}{@render field(label, R.ROYALTIES[i])}{/each}</div>
        <div class="switch"><Switch label={t('on_win')} on={values.on_win === 1} onchange={(v) => app.setRule('on_win', v ? 1 : 0)} /></div>
      </section>
    {/if}
  </div></div>
</div>

<style>
  .page { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .scroll { flex: 1; overflow: auto; padding-bottom: 32px; }
  .content { max-width: 1040px; padding: 0 24px; display: flex; flex-direction: column; gap: 12px; }
  .content p { margin: 0; }
  .presets { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
  .preset { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; padding: 14px; border-radius: 14px; border: 2px solid transparent;
    text-align: left; background: var(--surface-container-low); color: inherit; cursor: pointer; font: inherit; }
  .preset:hover { background: var(--surface-container); }
  .preset.on { background: var(--secondary-container); border-color: var(--primary); }
  .top { display: flex; align-items: center; gap: 10px; }
  .radio { flex: none; width: 20px; height: 20px; border-radius: 50%; border: 2px solid var(--on-surface-variant); box-sizing: border-box; }
  .radio.on { border-color: var(--primary); background: radial-gradient(var(--primary) 0 4.5px, transparent 5px); }
  section { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; }
  .section-title { color: var(--primary); font-size: 0.875rem; font-weight: 500; }
  .with-help { display: flex; align-items: center; gap: 2px; margin: -8px 0; }
  .play { display: flex; flex-wrap: wrap; gap: 8px 32px; align-items: flex-start; }
  .switch { min-width: 320px; flex: 1; }
  .switch :global(.row) { padding-left: 0; padding-right: 0; }
  .wheel { display: flex; flex-direction: column; gap: 4px; flex: 1; min-width: 320px; }
  .opts { display: flex; flex-wrap: wrap; gap: 4px 20px; }
  .opt { display: flex; align-items: center; gap: 10px; min-height: 40px; padding: 0; background: none; border: none; color: inherit; cursor: pointer; font: inherit; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 10px; }
  .field { background: var(--surface-container-low); border-radius: 12px; padding: 10px 12px; display: flex; flex-direction: column; gap: 2px; }
  .stepper { display: flex; align-items: center; gap: 6px; margin-top: 6px; }
  .step { width: 36px; height: 36px; border-radius: 18px; border: 1px solid var(--outline); background: transparent; color: var(--primary); cursor: pointer; font-size: 1.1rem; }
  .step:disabled { opacity: 0.4; cursor: default; }
  input { width: 72px; height: 36px; border-radius: 8px; border: 1px solid var(--outline); background: var(--surface); color: var(--on-surface);
    font: inherit; font-size: 1rem; font-weight: 600; text-align: center; appearance: textfield; -moz-appearance: textfield; }
  input::-webkit-outer-spin-button, input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
  input:focus { outline: 2px solid var(--primary); outline-offset: 0; }
</style>
