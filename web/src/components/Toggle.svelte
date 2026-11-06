<!-- Two to four choices side by side, one of them on (the app's ToggleGroup); `felt` draws it for the table. -->
<script lang="ts" generics="T">
  interface Props { options: [T, string][]; selected: T; onselect: (v: T) => void; felt?: boolean; height?: number; big?: boolean }
  let { options, selected, onselect, felt = false, height = 48, big = false }: Props = $props();
</script>

<div class="group" class:felt role="radiogroup" style:min-height="{height}px">
  {#each options as [v, l], i (l)}
    <button role="radio" aria-checked={v === selected} class:on={v === selected} class:first={i === 0} class:last={i === options.length - 1}
      class:big onclick={() => onselect(v)}>{l}</button>
  {/each}
</div>

<style>
  .group { display: flex; gap: 2px; }
  button { flex: 1 1 0; min-width: 0; border: none; padding: 6px 10px; cursor: pointer; border-radius: 6px; font-weight: 500; font-size: 0.875rem;
    background: var(--surface-container-highest); color: var(--on-surface); white-space: nowrap; }
  button.big { font-size: 1rem; }
  button.first { border-radius: 24px 6px 6px 24px; }
  button.last { border-radius: 6px 24px 24px 6px; }
  button.first.last { border-radius: 24px; }
  button.on { background: var(--primary); color: var(--on-primary); }
  .felt button { background: color-mix(in srgb, var(--on-felt) 12%, transparent); color: var(--on-felt); }
  .felt button.on { background: var(--on-felt); color: var(--felt); }
</style>
