<!-- The keys of a page, each with what it does; `mouse`: what the mouse does too, under them. -->
<script lang="ts">
  import { t } from '../../lib/i18n.svelte';
  import Kbd from './Kbd.svelte';
  let { keys, mouse = null }: { keys: [string[], string][]; mouse?: string | null } = $props();
</script>

<section class="keys">
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
</section>

<style>
  .keys { display: flex; flex-direction: column; gap: 4px; color: var(--on-surface-variant); }
  .head { text-transform: uppercase; letter-spacing: 0.04em; margin-top: 4px; }
  .grid { display: grid; grid-template-columns: auto 1fr; gap: 4px 10px; align-items: center; }
  .ks { display: flex; gap: 3px; justify-content: flex-end; }
</style>
