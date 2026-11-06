<!--
  A record: its result, when, a star to save it or drop it (with `app`), and its three rows. `stacked`: the rows one
  under another, for a grid of two a line; else side by side, as large as `max` and the width allow.
-->
<script lang="ts">
  import type { App } from '../lib/app.svelte';
  import { guessCategory, parseCards } from '../lib/cards';
  import { t } from '../lib/i18n.svelte';
  import type { Rec } from '../lib/store';
  import { LINE_GAP } from '../lib/sizing';
  import { localTime, signed, textRoom } from '../lib/ui.svelte';
  import Icon from './Icon.svelte';
  import RowCards from './RowCards.svelte';
  import ThreeRows from './ThreeRows.svelte';

  let { r, app = null, stacked = false, max, onclick }: { r: Rec; app?: App | null; stacked?: boolean; max: number; onclick: () => void } = $props();
  const result = $derived(typeof r.result === 'number' ? r.result : null);
  const rows = $derived(['front', 'middle', 'back'].map((k) => parseCards(r[k] ?? '')));
  const cats = $derived(rows.map(guessCategory));
  const saved = $derived(!!app && app.favoriteIds.has(r.id));
  let w = $state(0);
  const card = $derived(Math.min(max, (w - LINE_GAP * 4) / 5));
</script>

<div class="rec" role="button" tabindex="0" onclick={onclick} onkeydown={(e) => { if (e.key === 'Enter') onclick(); }} bind:clientWidth={w}>
  <div class="top">
    <span class="title-medium nowrap" style:min-width="{textRoom(44)}px" class:good={result !== null && result > 0} class:bad={result !== null && result < 0}>
      {result === null ? '' : signed(result)}
    </span>
    {#if stacked}<span class="grow"></span>{:else}<span class="body-small dim ellipsis grow">{localTime(r.timestamp ?? '')}</span>{/if}
    {#if app}
      <button class="btn icon text star" class:on={saved} aria-label={t(saved ? 'favorite_remove' : 'favorite_add')}
        onclick={(e) => { e.stopPropagation(); app.toggleFavorite(r); }}><Icon name={saved ? 'star' : 'starBorder'} /></button>
    {/if}
  </div>
  {#if stacked}
    <div class="body-small dim ellipsis">{localTime(r.timestamp ?? '')}</div>
    {#if w > 0}<div class="stack">{#each [0, 1, 2] as i (i)}<RowCards cards={rows[i]} category={cats[i]} width={card} gap={LINE_GAP} />{/each}</div>{/if}
  {:else}
    <ThreeRows {rows} categories={cats} {max} />
  {/if}
</div>

<style>
  .rec { padding: 6px 4px; display: flex; flex-direction: column; gap: 2px; cursor: pointer; border-radius: 8px; min-width: 0; }
  .rec:hover { background: var(--surface-container); }
  .top { display: flex; align-items: center; gap: 8px; min-height: 40px; }
  .grow { flex: 1; }
  .star { width: 40px; min-height: 40px; color: var(--on-surface-variant); }
  .star.on { color: #e0a100; }
  .stack { display: flex; flex-direction: column; gap: 4px; }
</style>
