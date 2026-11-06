<!-- A battle in the records (the app's BattleLine): the player's place and total, when, how many hands and players, and a star. -->
<script lang="ts">
  import type { App } from '../lib/app.svelte';
  import { fmtLoss, recordPlace, type BattleRecord } from '../lib/battle.svelte';
  import { t } from '../lib/i18n.svelte';
  import { localTime, signed } from '../lib/ui.svelte';
  import Icon from './Icon.svelte';

  let { b, app, onclick }: { b: BattleRecord; app: App; onclick: () => void } = $props();
  const puzzle = $derived(b.settings.mode === 'PUZZLE');
  const mine = $derived(b.players.find((p) => p.id === b.me) ?? null);
  const place = $derived(recordPlace(b));
  const humans = $derived(b.players.filter((p) => !p.bot).length);
  const saved = $derived(app.favoriteBattleIds.has(b.id));
</script>

<div class="line" role="button" tabindex="0" {onclick} onkeydown={(e) => { if (e.key === 'Enter') onclick(); }}>
  <div class="text">
    <span class="title-medium ellipsis">
      {#if place !== null && mine}{t('battle_place', place)} · {puzzle ? t('battle_total_loss', fmtLoss(mine.total)) : signed(mine.total)}{:else}—{/if}
    </span>
    <span class="body-small dim ellipsis">
      {localTime(new Date(b.startedAt).toISOString())} · {t('battle_record_size', b.outcomes.length, b.settings.hands, humans)}{b.left ? ' · ' + t('battle_record_left') : ''}
    </span>
  </div>
  <button class="btn icon text star" class:on={saved} aria-label={t(saved ? 'favorite_battle_remove' : 'favorite_battle_add')}
    onclick={(e) => { e.stopPropagation(); app.toggleFavoriteBattle(b); }}><Icon name={saved ? 'star' : 'starBorder'} /></button>
</div>

<style>
  .line { display: flex; align-items: center; gap: 10px; min-height: 56px; padding: 6px 4px; cursor: pointer; border-radius: 8px; }
  .line:hover { background: var(--surface-container); }
  .text { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  .star { width: 40px; min-height: 40px; color: var(--on-surface-variant); }
  .star.on { color: #e0a100; }
</style>
