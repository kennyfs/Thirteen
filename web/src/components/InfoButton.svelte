<!--
  An (i) button that shows `text` in a dialog, under `title` (a setting's name) when there is one; the text's line
  breaks stay. `small`: a setting's own, after its name (a 20 px icon in a 36 px round button, in the quiet color). The
  app's InfoButton.
-->
<script lang="ts">
  import { t } from '../lib/i18n.svelte';
  import Dialog from './Dialog.svelte';
  import Icon from './Icon.svelte';
  let { text, title = null, small = false }: { text: string; title?: string | null; small?: boolean } = $props();
  let open = $state(false);
</script>

<button class="btn icon text" class:small aria-label={title ? t('help_about', title) : t('info')} aria-haspopup="dialog" onclick={() => (open = true)}>
  <Icon name="info" size={small ? 20 : 24} />
</button>
{#if open}
  <Dialog title={title ?? t('info')} onclose={() => (open = false)}>
    <p class="body-large">{text}</p>
    {#snippet actions()}<button class="btn text" onclick={() => (open = false)}>{t('close')}</button>{/snippet}
  </Dialog>
{/if}

<style>
  p { margin: 0; white-space: pre-line; }
  .small { flex: none; width: 36px; min-height: 36px; height: 36px; border-radius: 18px; color: var(--on-surface-variant); }
</style>
