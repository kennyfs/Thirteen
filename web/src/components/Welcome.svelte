<!-- The welcome card of the first visit: the tutorial, the tour, or straight to play. Any way out counts as seen. -->
<script lang="ts">
  import { t } from '../lib/i18n.svelte';
  import Dialog from './Dialog.svelte';

  let { onlearn, ontour, onclose }: { onlearn: () => void; ontour: () => void; onclose: () => void } = $props();
</script>

<Dialog title={t('welcome_title')} {onclose}>
  <div class="welcome">
    <p class="body-large">{t('welcome_body')}</p>
    <button class="btn filled big" onclick={onlearn}>{t('welcome_learn')}</button>
    <button class="btn tonal big" onclick={ontour}>{t('welcome_tour')}</button>
    <button class="btn text big" onclick={onclose}>{t('welcome_skip')}</button>
  </div>
</Dialog>

<style>
  .welcome { display: flex; flex-direction: column; gap: 10px; max-width: 420px; }
  .welcome p { margin: 0 0 6px; }
</style>
