<!-- A row of a settings list: a title, its value under it, and an arrow when it opens a page. -->
<script lang="ts">
  import type { Snippet } from 'svelte';
  import Icon from './Icon.svelte';
  let { title, value = null, onclick = null, href = null, trailing }: { title: string; value?: string | null; onclick?: (() => void) | null; href?: string | null; trailing?: Snippet } = $props();
</script>

{#snippet inner()}
  <span class="text"><span class="body-large">{title}</span>{#if value}<span class="body-medium dim">{value}</span>{/if}</span>
  {#if trailing}{@render trailing()}{:else if onclick || href}<Icon name="chevron" />{/if}
{/snippet}

{#if href}
  <a class="row" {href} target="_blank" rel="noopener">{@render inner()}</a>
{:else if onclick}
  <button class="row" {onclick}>{@render inner()}</button>
{:else}
  <div class="row">{@render inner()}</div>
{/if}

<style>
  .row { display: flex; align-items: center; gap: 12px; min-height: 64px; padding: 8px 16px; width: 100%; box-sizing: border-box;
    background: none; border: none; text-align: left; color: inherit; font: inherit; text-decoration: none; }
  button.row, a.row { cursor: pointer; }
  button.row:hover, a.row:hover { background: var(--surface-container); }
  .text { flex: 1; display: flex; flex-direction: column; min-width: 0; }
</style>
