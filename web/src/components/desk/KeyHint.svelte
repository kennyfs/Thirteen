<!-- "? Keys and help": what a computer's pages show of their keys until ? shows them all (ui.showKeys); a click does the same. -->
<script lang="ts">
  import { t } from '../../lib/i18n.svelte';
  import { ui } from '../../lib/ui.svelte';
  import Kbd from './Kbd.svelte';
</script>

<button class="keyhint body-small" aria-pressed={ui.showKeys} onclick={() => (ui.showKeys = !ui.showKeys)}>
  <Kbd k="?" always />{t(ui.showKeys ? 'k_hide_keys' : 'k_show_keys')}
</button>

<style>
  .keyhint { display: inline-flex; align-items: center; gap: 6px; align-self: flex-start; background: none; border: none; padding: 4px 2px;
    color: var(--on-surface-variant); cursor: pointer; font: inherit; }
  .keyhint:hover { color: var(--on-surface); }
</style>
