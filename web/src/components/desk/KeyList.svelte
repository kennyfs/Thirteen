<!--
  The keys of a page, each with what it does; `mouse`: what the mouse does too, under them; `onhelp`: the full help.
  Until ? asks for them (ui.showKeys), only that: "? Keys and help".
-->
<script lang="ts">
  import { t } from '../../lib/i18n.svelte';
  import { ui } from '../../lib/ui.svelte';
  import Kbd from './Kbd.svelte';
  import KeyHint from './KeyHint.svelte';
  let { keys, mouse = null, onhelp = null }: { keys: [string[], string][]; mouse?: string | null; onhelp?: (() => void) | null } = $props();
</script>

{#if !ui.showKeys}
  <KeyHint />
{:else}
<section class="keys">
  <KeyHint />
  <div class="label-small dim head">{t('keys_title')}</div>
  <div class="grid">
    {#each keys as [ks, what] (what)}
      <span class="ks">{#each ks as k (k)}<Kbd {k} />{/each}</span><span class="body-small">{what}</span>
    {/each}
  </div>
  {#if mouse}
    <div class="label-small dim head">{t('mouse_title')}</div>
    <div class="body-small dim">{mouse}</div>
  {/if}
  {#if onhelp}<button class="btn text full" onclick={onhelp}>{t('k_full_help')}</button>{/if}
</section>
{/if}

<style>
  .keys { display: flex; flex-direction: column; gap: 4px; color: var(--on-surface-variant); }
  .head { text-transform: uppercase; letter-spacing: 0.04em; margin-top: 4px; }
  .grid { display: grid; grid-template-columns: auto 1fr; gap: 4px 10px; align-items: center; }
  .ks { display: flex; gap: 3px; justify-content: flex-end; }
  .full { align-self: flex-start; padding: 0 8px; min-height: 32px; }
</style>
