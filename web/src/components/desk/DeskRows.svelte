<!--
  An arrangement's three rows one above another, each with its hand's name over it; the cards in `outlined` framed
  (those in another row than in the arrangement compared with).
-->
<script lang="ts">
  import { rowLabel, rowLayout, type Rows } from '../../lib/cards';
  import { ta } from '../../lib/i18n.svelte';
  import { CARD_GAP } from '../../lib/sizing';
  import Card from '../Card.svelte';

  interface Props { rows: Rows; keys: number[] | null; categories: number[] | null; width: number; wheel: number; outlined?: Set<number> }
  let { rows, keys, categories, width, wheel, outlined = new Set() }: Props = $props();
</script>

<div class="stack">
  {#each [0, 1, 2] as r (r)}
    <div class="row">
      <div class="label-large name">{keys ? rowLabel(keys[r], ta('row_labels'), wheel) : ta('row_names')[r]}</div>
      <div class="cards" style:gap="{CARD_GAP}px">
        {#each rowLayout(rows[r], categories?.[r] ?? 0) as p (p.card)}
          <Card card={p.card} {width} tint={p.tint} light={p.light} selected={outlined.has(p.card)} lift={false} />
        {/each}
      </div>
    </div>
  {/each}
</div>

<style>
  .stack { display: flex; flex-direction: column; gap: 8px; }
  .row { display: flex; flex-direction: column; gap: 3px; }
  .name { color: var(--on-surface-variant); }
  .cards { display: flex; }
</style>
