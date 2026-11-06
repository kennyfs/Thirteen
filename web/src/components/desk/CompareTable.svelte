<!--
  Two candidates' figures side by side: the expected points and the chances (and the special rows lost, under rules that
  pay for them), with the difference of the first from the second, green where better and red where worse.
-->
<script lang="ts">
  import { lostShown, pct, pctDiff, ROYALTY_LOST, SHOWN, tone } from '../../lib/compare';
  import type { Analysis, Candidate } from '../../lib/engine';
  import { t, ta } from '../../lib/i18n.svelte';
  import { fmtQ } from '../../lib/ui.svelte';

  interface Props { a: Analysis; x: Candidate; base: Candidate | null; xLabel: string; baseLabel: string; compact?: boolean }
  let { a, x, base, xLabel, baseLabel, compact = false }: Props = $props();
  const names = $derived(ta('prob_names'));
  const lost = $derived(lostShown(a));
  const cls = (v: number) => (v > 0 ? 'good' : v < 0 ? 'bad' : '');
</script>

<table class:compact>
  <thead>
    <tr><th></th><th class="num">{xLabel}</th>{#if base}<th class="num">{baseLabel}</th><th class="num">{t('cmp_diff')}</th>{/if}</tr>
  </thead>
  <tbody>
    {#if x.q !== null}
      {@const d = base?.q != null ? x.q - base.q : 0}
      <tr class="q">
        <td>{t('expected')}</td>
        <td class="num bold" class:good={x.q >= 0} class:bad={x.q < 0}>{fmtQ(x.q)}</td>
        {#if base}
          <td class="num">{base.q !== null ? fmtQ(base.q) : ''}</td>
          <td class="num {cls(Math.abs(d) < 0.005 ? 0 : d)}">{Math.abs(d) < 0.005 ? '—' : fmtQ(d)}</td>
        {/if}
      </tr>
    {/if}
    {#if x.probs}
      {#each [...SHOWN.map((k) => [k, k] as const), ...lost.map((i) => [ROYALTY_LOST + i, 1] as const)] as [k, kind] (k)}
        {@const v = x.probs[k]}
        {@const b = base?.probs?.[k] ?? null}
        {@const tn = b === null ? 0 : tone(kind, v, b)}
        <tr>
          <td>{names[k]}</td>
          <td class="num {cls(tn)}" class:bold={tn !== 0}>{pct(v)}</td>
          {#if base}
            <td class="num">{b === null ? '' : pct(b)}</td>
            <td class="num {cls(tn)}">{b === null || tn === 0 ? '—' : pctDiff(v - b)}</td>
          {/if}
        </tr>
      {/each}
    {/if}
  </tbody>
</table>

<style>
  table { width: 100%; border-collapse: collapse; font-size: 0.9375rem; }
  th { font-size: 0.8125rem; font-weight: 500; color: var(--on-surface-variant); text-align: left; padding: 4px 8px; border-bottom: 1px solid var(--outline-variant); }
  td { padding: 5px 8px; border-bottom: 1px solid color-mix(in srgb, var(--outline-variant) 50%, transparent); white-space: nowrap; }
  .num { text-align: right; font-variant-numeric: tabular-nums; }
  tr.q td { font-size: 1rem; }
  .compact { font-size: 0.8125rem; }
  .compact th { padding: 3px 4px; font-size: 0.75rem; }
  .compact td { padding: 3px 4px; }
  .compact tr.q td { font-size: 0.875rem; }
</style>
