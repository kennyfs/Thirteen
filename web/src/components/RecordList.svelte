<!-- Records in a list, or on a narrow page two a line with their rows one under another (larger cards). -->
<script lang="ts">
  import type { App } from '../lib/app.svelte';
  import type { Rec } from '../lib/store';
  import { textRoom, ui } from '../lib/ui.svelte';
  import RecordLine from './RecordLine.svelte';
  let { recs, app = null, onpick }: { recs: Rec[]; app?: App | null; onpick: (r: Rec) => void } = $props();
  let w = $state(0);
  const stacked = $derived(w > 0 && w < 560 && (w - 12) / 2 >= textRoom(130));
</script>

<div bind:clientWidth={w} class:grid={stacked}>
  {#each recs as r (r.id)}
    <div class="item"><RecordLine {r} {app} {stacked} max={ui.sizes.record} onclick={() => onpick(r)} /></div>
  {/each}
</div>

<style>
  .item { border-bottom: 1px solid var(--outline-variant); min-width: 0; }
  .grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: 12px; }
</style>
