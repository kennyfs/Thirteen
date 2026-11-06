<!-- A chip that opens a list of choices (the app's MenuChip and SelectRow): `options` as [value, label]. -->
<script lang="ts" generics="T">
  import Icon from './Icon.svelte';
  interface Props { label: string; options: [T, string][]; selected?: T; onselect: (v: T) => void; disabled?: boolean; chip?: boolean }
  let { label, options, selected, onselect, disabled = false, chip = true }: Props = $props();
  let open = $state(false);
  let root: HTMLElement;
  $effect(() => {
    if (!open) return;
    const close = (e: Event) => { if (!root.contains(e.target as Node)) open = false; };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') open = false; };
    document.addEventListener('pointerdown', close);
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('pointerdown', close); document.removeEventListener('keydown', esc); };
  });
</script>

<div class="menu" bind:this={root}>
  <button class={chip ? 'chip' : 'btn outlined'} {disabled} aria-haspopup="listbox" aria-expanded={open} onclick={() => (open = !open)}>
    <span class="ellipsis">{label}</span><Icon name="dropDown" />
  </button>
  {#if open}
    <ul role="listbox">
      {#each options as [v, l] (l)}
        <li role="option" aria-selected={v === selected}>
          <button class:on={v === selected} onclick={() => { open = false; onselect(v); }}>{l}</button>
        </li>
      {/each}
    </ul>
  {/if}
</div>

<style>
  .menu { position: relative; display: inline-block; min-width: 0; max-width: 100%; }
  .menu > button { max-width: 100%; }
  ul { position: absolute; z-index: 20; top: calc(100% + 4px); left: 0; margin: 0; padding: 6px 0; list-style: none; min-width: 180px;
    max-width: min(360px, 90vw); max-height: 60vh; overflow: auto; background: var(--surface-container); border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25); }
  li button { display: block; width: 100%; text-align: left; min-height: 48px; padding: 0 16px; border: none; background: none; cursor: pointer; font-size: 1rem; }
  li button:hover { background: var(--surface-container-highest); }
  li button.on { font-weight: 700; color: var(--primary); }
</style>
