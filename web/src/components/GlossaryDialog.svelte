<!-- The glossary in a dialog, at `focusId` when a term's link opened it (App.svelte shows it, over everything). -->
<script lang="ts">
  import { t } from '../lib/i18n.svelte';
  import Dialog from './Dialog.svelte';
  import GlossaryContent from './GlossaryContent.svelte';

  let { focusId = null, onclose }: { focusId?: string | null; onclose: () => void } = $props();
</script>

<Dialog title={t('glossary_title')} {onclose} wide>
  <GlossaryContent {focusId} />
  {#snippet actions()}<button class="btn text" onclick={onclose}>{t('close')}</button>{/snippet}
</Dialog>
