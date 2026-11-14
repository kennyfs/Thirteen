<!--
  A list's place while it loads (an analysis's candidates, a hand's analysis on the way): a heading bar and `rows` rows
  in the shape of a candidate's, a mark, its words and its figure over a line of cards, as pulsing blocks. `label`
  says what is loading to a screen reader.
-->
<script lang="ts">
  let { rows = 5, label = '' }: { rows?: number; label?: string } = $props();
</script>

<div class="sk-panel" role="status" aria-busy="true">
  {#if label}<span class="sr-only">{label}</span>{/if}
  <div class="head sk sk-block" aria-hidden="true"></div>
  {#each Array(rows) as _, i (i)}
    <div class="row sk" aria-hidden="true">
      <div class="line"><span class="mark sk-block"></span><span class="words sk-block"></span><span class="fig sk-block"></span></div>
      <div class="cards sk-block"></div>
    </div>
  {/each}
</div>

<style>
  .sk-panel { flex: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column; gap: 8px; padding: 12px; }
  .head { flex: none; width: min(60%, 320px); height: 20px; border-radius: 10px; margin: 4px 4px 8px; }
  .row { flex: none; display: flex; flex-direction: column; gap: 8px; padding: 10px; border-radius: 12px; background: var(--surface-container-high); }
  .line { display: flex; align-items: center; gap: 8px; }
  .line span { height: 18px; border-radius: 9px; }
  .mark { flex: none; width: 52px; }
  .words { flex: 1; max-width: 240px; }
  .fig { flex: none; width: 44px; margin-left: auto; }
  .cards { height: 44px; border-radius: 8px; max-width: 420px; }
</style>
