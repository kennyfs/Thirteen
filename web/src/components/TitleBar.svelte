<!-- A page's title, with Back when it is a page opened from another, and actions at the right. -->
<script lang="ts">
  import type { Snippet } from 'svelte';
  import { t } from '../lib/i18n.svelte';
  import Icon from './Icon.svelte';
  let { title, onback = null, children }: { title: string; onback?: (() => void) | null; children?: Snippet } = $props();
</script>

<header>
  {#if onback}<button class="btn icon text" aria-label={t('back')} onclick={onback}><Icon name="back" /></button>{/if}
  <h1 class="title-large ellipsis" class:pad={!onback}>{title}</h1>
  <div class="actions">{@render children?.()}</div>
</header>

<style>
  header { display: flex; align-items: center; gap: 4px; min-height: 56px; padding: 4px 12px 4px 4px; flex: none; }
  h1 { margin: 0; flex: 1; }
  h1.pad { padding-left: 12px; }
  .actions { display: flex; align-items: center; gap: 8px; }
</style>
