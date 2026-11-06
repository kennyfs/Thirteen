<!-- The three rows of a play side by side, each card as large as `max` allows in the width there is. -->
<script lang="ts">
  import RowCards from './RowCards.svelte';
  import { LINE_GAP, LINE_GROUP_GAP, lineCard } from '../lib/sizing';

  let { rows, categories, max, valid = true }: { rows: number[][]; categories: number[] | null; max: number; valid?: boolean } = $props();
  let width = $state(0);
  const card = $derived(Math.min(max, lineCard(width)));
</script>

<div class="three" bind:clientWidth={width} style:gap="{LINE_GROUP_GAP}px">
  {#if width > 0}
    {#each [0, 1, 2] as r (r)}
      <RowCards cards={rows[r]} category={valid ? categories?.[r] ?? 0 : 0} width={card} gap={LINE_GAP} />
    {/each}
  {/if}
</div>

<style>
  .three { display: flex; width: 100%; min-height: 1px; }
</style>
