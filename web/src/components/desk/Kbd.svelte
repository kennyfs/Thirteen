<!-- A key, as a small framed label: on a button, or in a list of keys (KeyList). -->
<script lang="ts">
  let { k, inline = false }: { k: string; inline?: boolean } = $props();
</script>

<kbd class:inline>{k}</kbd>

<style>
  kbd { font-family: inherit; font-size: 0.72rem; font-weight: 600; line-height: 1; padding: 3px 5px; border-radius: 5px; min-width: 1.6em;
    text-align: center; display: inline-block; border: 1px solid currentColor; opacity: 0.8; white-space: nowrap; }
  kbd.inline { margin-left: 6px; }
</style>
