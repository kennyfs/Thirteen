<!-- A page's title, with Back when it is a page opened from another, and actions at the right. `max`: the width of
     the page's column of content, px: on a wide window the title and the actions keep to it, not to the window's
     far corners (Mahjong-TW's). -->
<script lang="ts">
  import type { Snippet } from 'svelte';
  import { t } from '../lib/i18n.svelte';
  import Icon from './Icon.svelte';
  let { title, onback = null, children, max = 0 }: { title: string; onback?: (() => void) | null; children?: Snippet; max?: number } = $props();
</script>

<header style:max-width={max ? `${max}px` : undefined} class:column={max > 0}>
  {#if onback}<button class="btn icon text" aria-label={t('back')} onclick={onback}><Icon name="back" /></button>{/if}
  <h1 class="title-large ellipsis" class:pad={!onback}>{title}</h1>
  <div class="actions">{@render children?.()}</div>
</header>

<style>
  header { display: flex; align-items: center; gap: 4px; min-height: 56px; padding: 4px 12px 4px 4px; flex: none; }
  header.column { width: 100%; margin: 0 auto; box-sizing: border-box; }
  h1 { margin: 0; flex: 1; }
  h1.pad { padding-left: 12px; }
  .actions { display: flex; align-items: center; gap: 8px; }
</style>
