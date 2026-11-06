<!-- A label and a switch, the whole row toggling it. -->
<script lang="ts">
  let { label, on, onchange }: { label: string; on: boolean; onchange: (v: boolean) => void } = $props();
</script>

<button class="row" role="switch" aria-checked={on} onclick={() => onchange(!on)}>
  <span class="body-large grow">{label}</span><span class="track" class:on><span class="thumb"></span></span>
</button>

<style>
  .row { display: flex; align-items: center; gap: 12px; min-height: 56px; padding: 6px 16px; width: 100%; background: none; border: none;
    text-align: left; color: inherit; cursor: pointer; font: inherit; }
  .grow { flex: 1; }
  .track { flex: none; width: 52px; height: 32px; border-radius: 16px; border: 2px solid var(--outline); box-sizing: border-box;
    background: var(--surface-container-highest); position: relative; transition: background 0.15s; }
  .thumb { position: absolute; left: 6px; top: 6px; width: 16px; height: 16px; border-radius: 50%; background: var(--outline); transition: all 0.15s; }
  .track.on { background: var(--primary); border-color: var(--primary); }
  .track.on .thumb { left: 22px; top: 2px; width: 24px; height: 24px; background: var(--on-primary); }
</style>
