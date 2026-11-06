<!--
  The running total of the points (0 before the first hand): a line over a dashed zero line, with the highest and
  lowest totals on the left, the first and last dates under it, and the last total at the end of the line.
-->
<script lang="ts">
  import { signed } from '../lib/ui.svelte';
  let { totals, firstDate, lastDate, description, height = 200 }: { totals: number[]; firstDate: string; lastDate: string; description: string; height?: number } = $props();
  let w = $state(0);
  const axis = (v: number) => (Math.abs(v) >= 10 || Number.isInteger(v) ? v.toFixed(0) : v.toFixed(1));
  const points = $derived([0, ...totals]);
  const hi = $derived(Math.max(...points, 0));
  const lo = $derived(Math.min(...points, 0));
  const end = $derived(points[points.length - 1]);
  const endClass = $derived(end > 0 ? 'good' : end < 0 ? 'bad' : '');
  // Room for the labels: the scale's at the left, the last total's at the right, the dates under.
  const left = $derived(Math.max(axis(hi).length, axis(lo).length) * 8 + 14);
  const right = $derived(Math.max(44, signed(end).length * 9 + 12));
  const top = 12;
  const bottom = 26;
  const x = (i: number) => left + ((w - left - right) * i) / Math.max(points.length - 1, 1);
  const y = (v: number) => top + (height - top - bottom) * (1 - (v - lo) / (hi - lo || 1));
  const path = $derived(points.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(''));
</script>

<div class="chart" bind:clientWidth={w} style:height="{height}px">
  {#if w > 0}
    <svg width={w} {height} role="img" aria-label={description}>
      <line x1={left} x2={w - right} y1={y(0)} y2={y(0)} class="zero" />
      {#each [...new Set([hi, lo, 0])] as v (v)}
        <text x={left - 6} y={y(v)} text-anchor="end" dominant-baseline="middle" class="label">{axis(v)}</text>
      {/each}
      <path d={path} class="line" />
      <circle cx={x(points.length - 1)} cy={y(end)} r="5" class="dot {endClass}" />
      <text x={x(points.length - 1) + 8} y={Math.min(y(end), height - bottom - 8)} dominant-baseline="middle" class="end {endClass}">{signed(end)}</text>
      <text x={left} y={height - 4} class="label">{firstDate}</text>
      {#if firstDate !== lastDate}<text x={w - right} y={height - 4} text-anchor="end" class="label">{lastDate}</text>{/if}
    </svg>
  {/if}
</div>

<style>
  .chart { width: 100%; }
  svg { display: block; overflow: visible; }
  .zero { stroke: var(--outline); stroke-width: 1.5; stroke-dasharray: 8 6; }
  .line { fill: none; stroke: var(--primary); stroke-width: 3; stroke-linecap: round; stroke-linejoin: round; }
  .label { fill: var(--on-surface-variant); font-size: 13px; }
  .end { font-size: 15px; font-weight: 500; fill: var(--primary); }
  .dot { fill: var(--primary); }
  .good { fill: var(--good); }
  .bad { fill: var(--bad); }
</style>
