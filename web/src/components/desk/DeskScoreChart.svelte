<!--
  A computer's chart of the running total: as ScoreChart, filling its box, with the hand under the mouse marked and
  told (its number, when, its points and the total then); a click picks that hand (`onpick`, its index in `totals`).
-->
<script lang="ts">
  import { t } from '../../lib/i18n.svelte';
  import { signed } from '../../lib/ui.svelte';
  interface Props { totals: number[]; results: number[]; times: string[]; description: string; picked?: number | null; onpick: (i: number) => void }
  let { totals, results, times, description, picked = null, onpick }: Props = $props();
  let w = $state(0);
  let h = $state(0);
  let hover = $state<number | null>(null);
  const axis = (v: number) => (Math.abs(v) >= 10 || Number.isInteger(v) ? v.toFixed(0) : v.toFixed(1));
  const points = $derived([0, ...totals]);
  const hi = $derived(Math.max(...points, 0));
  const lo = $derived(Math.min(...points, 0));
  const left = $derived(Math.max(axis(hi).length, axis(lo).length) * 8 + 16);
  const right = 16;
  const top = 14;
  const bottom = 26;
  const x = (i: number) => left + ((w - left - right) * i) / Math.max(points.length - 1, 1);
  const y = (v: number) => top + (h - top - bottom) * (1 - (v - lo) / (hi - lo || 1));
  const path = $derived(points.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(''));
  const ticks = $derived.by(() => {
    const span = hi - lo || 1;
    const step = [1, 2, 5, 10, 20, 50, 100, 200, 500, 1000].find((s) => span / s <= 5) ?? 1000;
    const out: number[] = [];
    for (let v = Math.ceil(lo / step) * step; v <= hi; v += step) out.push(v);
    return out;
  });

  function at(e: MouseEvent): number | null {
    const r = (e.currentTarget as SVGElement).getBoundingClientRect();
    const i = Math.round(((e.clientX - r.left - left) / Math.max(1, w - left - right)) * (points.length - 1));
    return i >= 1 && i < points.length ? i : null;
  }
  const shown = $derived(hover ?? (picked !== null ? picked + 1 : null));
</script>

<div class="chart" bind:clientWidth={w} bind:clientHeight={h}>
  {#if w > 0 && h > 0}
    <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_noninteractive_element_interactions -->
    <svg width={w} height={h} role="img" aria-label={description}
      onmousemove={(e) => (hover = at(e))} onmouseleave={() => (hover = null)} onclick={(e) => { const i = at(e); if (i !== null) onpick(i - 1); }}>
      {#each ticks as v (v)}
        <line x1={left} x2={w - right} y1={y(v)} y2={y(v)} class:zero={v === 0} class="grid" />
        <text x={left - 6} y={y(v)} text-anchor="end" dominant-baseline="middle" class="label">{axis(v)}</text>
      {/each}
      <path d={path} class="line" />
      {#if shown !== null}
        <line x1={x(shown)} x2={x(shown)} y1={top} y2={h - bottom} class="cross" />
        <circle cx={x(shown)} cy={y(points[shown])} r="6" class="dot" />
      {/if}
      <circle cx={x(points.length - 1)} cy={y(points[points.length - 1])} r="4" class="dot" />
      <text x={left} y={h - 6} class="label">{times[0]?.slice(0, 10)}</text>
      <text x={w - right} y={h - 6} text-anchor="end" class="label">{times[times.length - 1]?.slice(0, 10)}</text>
    </svg>
    {#if shown !== null}
      {@const i = shown - 1}
      <div class="tip" style:left="{Math.min(Math.max(x(shown) - 110, 0), w - 220)}px" style:top="{Math.max(0, y(points[shown]) - 64)}px">
        <div class="label-small dim">{t('point_tip', shown, times[i])}</div>
        <div class="title-small">{t('point_this', signed(results[i]), signed(totals[i]))}</div>
      </div>
    {/if}
  {/if}
</div>

<style>
  .chart { position: relative; width: 100%; height: 100%; min-height: 160px; }
  svg { display: block; cursor: crosshair; }
  .grid { stroke: var(--outline-variant); stroke-width: 1; }
  .grid.zero { stroke: var(--outline); stroke-dasharray: 8 6; stroke-width: 1.5; }
  .line { fill: none; stroke: var(--primary); stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }
  .cross { stroke: var(--outline); stroke-width: 1; }
  .dot { fill: var(--primary); }
  .label { fill: var(--on-surface-variant); font-size: 12px; }
  .tip { position: absolute; width: 220px; pointer-events: none; background: var(--surface-container-highest); border-radius: 8px; padding: 6px 10px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2); }
</style>
