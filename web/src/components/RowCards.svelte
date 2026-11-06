<!-- A full or partial row laid out by rowLayout and tinted by its category (0 = unknown), with empty places up to `size`. -->
<script lang="ts">
  import { rowLayout } from '../lib/cards';
  import Card from './Card.svelte';
  import { CARD_GAP } from '../lib/sizing';

  interface Props { cards: number[]; category: number; width: number; gap?: number; size?: number; oncard?: ((c: number) => void) | null }
  let { cards, category, width, gap = CARD_GAP, size = 0, oncard = null }: Props = $props();
  const placed = $derived(rowLayout(cards, category));
</script>

<div class="row" style:gap="{gap}px">
  {#each placed as p (p.card)}
    <Card card={p.card} {width} tint={p.tint} light={p.light} lift={false} onclick={oncard ? () => oncard(p.card) : null} />
  {/each}
  {#each Array(Math.max(0, size - cards.length)) as _, i (i)}
    <div class="slot" style:width="{width}px" style:height="{width * 1.4}px" style:border-radius="{width * 0.14}px"></div>
  {/each}
</div>

<style>
  .row { display: flex; flex: none; }
  .slot { flex: none; border: 1.5px dashed color-mix(in srgb, var(--on-felt) 45%, transparent); }
</style>
