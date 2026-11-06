<!--
  A key, as a small framed label: on a button, or in a list of keys (KeyList). Shown only once ? has asked for the
  keys (ui.showKeys), so that the pages stay plain; `always` for the keys that are the way to play (a key a card).
-->
<script lang="ts">
  import { ui } from '../../lib/ui.svelte';
  let { k, inline = false, always = false }: { k: string; inline?: boolean; always?: boolean } = $props();
</script>

{#if always || ui.showKeys}<kbd class:inline>{k}</kbd>{/if}

<style>
  kbd { font-family: inherit; font-size: 0.72rem; font-weight: 600; line-height: 1; padding: 3px 5px; border-radius: 5px; min-width: 1.6em;
    text-align: center; display: inline-block; border: 1px solid currentColor; opacity: 0.8; white-space: nowrap; }
  kbd.inline { margin-left: 6px; }
</style>
