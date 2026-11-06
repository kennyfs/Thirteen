<!-- The networks: the built-in one and those loaded from .onnx files (kept in this browser), which can be deleted. -->
<script lang="ts">
  import type { App, Model } from '../lib/app.svelte';
  import { t } from '../lib/i18n.svelte';
  import Dialog from './Dialog.svelte';
  import Icon from './Icon.svelte';
  import TitleBar from './TitleBar.svelte';
  let { app, onback }: { app: App; onback: () => void } = $props();
  let deleting = $state<Model | null>(null);
  let input: HTMLInputElement;
</script>

<div class="page">
  <TitleBar title={t('networks')} {onback} />
  <div class="scroll"><div class="readable">
    {#each app.models as m (m.path)}
      <div class="row">
        <span class="grow"><span class="body-large">{m.name}</span>
          {#if m.builtIn}<br /><span class="body-medium dim">{t('built_in_note')}</span>
          {:else if app.legacyModels.has(m.path)}<br /><span class="body-medium dim">{t('legacy_note')}</span>{/if}</span>
        {#if !m.builtIn}<button class="btn icon text" aria-label={t('delete')} onclick={() => (deleting = m)}><Icon name="delete" /></button>{/if}
      </div>
    {/each}
    <div class="pad">
      <input type="file" accept=".onnx" hidden bind:this={input} onchange={() => { const f = input.files?.[0]; if (f) app.importModel(f); input.value = ''; }} />
      <button class="btn outlined big" onclick={() => input.click()}><Icon name="folder" />{t('load_weights')}</button>
    </div>
    <p class="body-medium dim note">{t('networks_info')}</p>
  </div></div>
</div>

{#if deleting}
  {@const m = deleting}
  <Dialog onclose={() => (deleting = null)}>
    <p class="body-large" style="margin:0">{t('delete_network_confirm', m.name)}</p>
    {#snippet actions()}
      <button class="btn text" onclick={() => (deleting = null)}>{t('cancel')}</button>
      <button class="btn text" onclick={() => { app.deleteModel(m); deleting = null; }}>{t('delete')}</button>
    {/snippet}
  </Dialog>
{/if}

<style>
  .page { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .scroll { flex: 1; overflow: auto; padding-bottom: 24px; }
  .readable { max-width: 720px; margin: 0 auto; }
  .row { display: flex; align-items: center; min-height: 64px; padding: 0 4px 0 16px; border-bottom: 1px solid var(--outline-variant); margin: 0 16px 0 0; }
  .grow { flex: 1; }
  .pad { padding: 16px; }
  .note { padding: 0 16px; margin: 0; }
</style>
