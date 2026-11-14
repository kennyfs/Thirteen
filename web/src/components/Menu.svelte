<!--
  A chip that opens a list of choices (the app's MenuChip and SelectRow): `options` as [value, label]. The list is a
  popover, in the top layer, so that no scrolling column clips it; the browser closes it at a tap outside or Escape.
  Floating UI puts it under the chip (above it when there is no room), as wide as its longest option and inside the
  window, and keeps it there while the page scrolls.
-->
<script lang="ts" generics="T">
  import { autoUpdate, computePosition, flip, shift, size } from '@floating-ui/dom';
  import Icon from './Icon.svelte';
  interface Props { label: string; options: [T, string][]; selected?: T; onselect: (v: T) => void; disabled?: boolean; chip?: boolean }
  let { label, options, selected, onselect, disabled = false, chip = true }: Props = $props();
  // Its own constant: Svelte takes $props.id() only at the top level.
  const uid = $props.id();
  const id = `menu-${uid}`;
  let open = $state(false);
  let button: HTMLButtonElement;
  let list: HTMLUListElement;

  function place() {
    computePosition(button, list, {
      strategy: 'fixed',
      placement: 'bottom-start',
      middleware: [
        flip({ padding: 8 }),
        shift({ padding: 8 }),
        size({ padding: 8, apply: ({ availableHeight }) => { list.style.maxHeight = `${Math.max(120, Math.min(availableHeight, innerHeight * 0.6))}px`; } }),
      ],
    }).then(({ x, y }) => { list.style.left = `${x}px`; list.style.top = `${y}px`; });
  }

  // Placed while open: at once (before the list's first frame, as the state is set before it shows), then as the page
  // scrolls or the chip moves.
  $effect(() => { if (open) return autoUpdate(button, list, place); });
</script>

<div class="menu">
  <button bind:this={button} class={chip ? 'chip' : 'btn outlined'} {disabled} popovertarget={id} aria-haspopup="listbox" aria-expanded={open}>
    <span class="ellipsis">{label}</span><Icon name="dropDown" />
  </button>
  <ul bind:this={list} {id} popover="auto" role="listbox" onbeforetoggle={(e) => (open = (e as ToggleEvent).newState === 'open')}>
    {#each options as [v, l] (l)}
      <li role="option" aria-selected={v === selected}>
        <button class:on={v === selected} onclick={() => { list.hidePopover(); onselect(v); }}>{l}</button>
      </li>
    {/each}
  </ul>
</div>

<style>
  .menu { display: inline-block; min-width: 0; max-width: 100%; }
  .menu > button { max-width: 100%; }
  /* The UA's popover sits in the middle of the window with a border: here it sits where Floating UI puts it. */
  ul { position: fixed; inset: auto; margin: 0; border: none; padding: 6px 0; list-style: none; width: max-content; max-width: calc(100vw - 16px);
    max-height: 60vh; overflow: auto; color: inherit; background: var(--surface-container); border-radius: 8px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25); }
  li button { display: block; width: 100%; text-align: left; min-height: 48px; padding: 0 16px; border: none; background: none; cursor: pointer;
    font-size: 1rem; white-space: nowrap; color: inherit; }
  li button:hover { background: var(--surface-container-highest); }
  li button.on { font-weight: 700; color: var(--primary); }
</style>
