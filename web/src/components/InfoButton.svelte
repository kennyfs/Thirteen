<!--
  An (i) button that shows `text` in a dialog, under `title` (a setting's name) when there is one; the text's line
  breaks stay. `small`: a setting's own, after its name (a 20 px icon in the quiet color, in a 44 px round button whose
  negative margins keep its row's height). The app's InfoButton.
-->
<script lang="ts">
  import { t } from '../lib/i18n.svelte';
  import Dialog from './Dialog.svelte';
  import Icon from './Icon.svelte';
  import Linked from './Linked.svelte';
  let { text, title = null, small = false }: { text: string; title?: string | null; small?: boolean } = $props();
  let open = $state(false);
</script>

<button class="btn icon text" class:small aria-label={title ? t('help_about', title) : t('info')} aria-haspopup="dialog" onclick={() => (open = true)}>
  <Icon name="info" size={small ? 20 : 24} />
</button>
{#if open}
  <Dialog title={title ?? t('info')} onclose={() => (open = false)}>
    <p class="body-large">{#each text.split('\n') as line, i (i)}<span><Linked text={line} /></span>{/each}</p>
    {#snippet actions()}<button class="btn text" onclick={() => (open = false)}>{t('close')}</button>{/snippet}
  </Dialog>
{/if}

<style>
  /*
    A line of the text each its own block, which avoids a short last line where the browser can. Japanese, where the
    browser breaks it between phrases, evens its lines out; elsewhere it breaks anywhere, as Japanese body text does.
  */
  p { margin: 0; }
  p span { display: block; text-wrap: pretty; }
  @supports (word-break: auto-phrase) {
    :global(:root:lang(ja)) p span { text-wrap: balance; word-break: auto-phrase; }
  }
  .small { flex: none; width: 44px; min-height: 44px; height: 44px; border-radius: 22px; margin: -4px 0; color: var(--on-surface-variant); }
</style>
