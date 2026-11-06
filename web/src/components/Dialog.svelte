<!-- A modal dialog: a title, its content, and buttons; Escape or a click outside closes it. -->
<script lang="ts">
  import type { Snippet } from 'svelte';
  let { title = '', onclose, children, actions, wide = false }: { title?: string; onclose: () => void; children: Snippet; actions?: Snippet; wide?: boolean } = $props();
  let dialog: HTMLDialogElement;
  $effect(() => { dialog.showModal(); return () => dialog.close(); });
</script>

<dialog bind:this={dialog} class:wide onclose={onclose} onclick={(e) => { if (e.target === dialog) onclose(); }}>
  <div class="inner">
    {#if title}<h2 class="headline-small">{title}</h2>{/if}
    <div class="content">{@render children()}</div>
    {#if actions}<div class="actions">{@render actions()}</div>{/if}
  </div>
</dialog>

<style>
  dialog { border: none; border-radius: 28px; padding: 0; background: var(--surface-container-high); color: var(--on-surface);
    max-width: min(560px, calc(100vw - 32px)); width: max-content; min-width: min(280px, calc(100vw - 32px)); }
  dialog.wide { width: min(560px, calc(100vw - 16px)); max-width: calc(100vw - 16px); }
  dialog::backdrop { background: rgba(0, 0, 0, 0.4); }
  .inner { padding: 24px 24px 18px; display: flex; flex-direction: column; gap: 14px; max-height: calc(100dvh - 48px); }
  dialog.wide .inner { padding: 16px 12px 12px; }
  h2 { margin: 0; font-weight: 500; }
  .content { overflow: auto; min-height: 0; }
  .actions { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
</style>
