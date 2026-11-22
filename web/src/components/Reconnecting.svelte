<!--
  In a room whose connection dropped, while the page tries to take its seat back: a strip above the room's page (the
  app's ReconnectingBar). It goes when the seat is back, or when the room is said to be gone.
-->
<script lang="ts">
  import { t } from '../lib/i18n.svelte';
</script>

<div class="reconnecting" role="status" aria-live="polite">
  <span class="label-large">{t('battle_reconnecting')}</span>
  <span class="track" aria-hidden="true"><span class="bar"></span></span>
</div>

<style>
  .reconnecting { flex: none; display: flex; flex-direction: column; gap: 6px; padding: 8px 16px 0; background: var(--secondary-container);
    color: var(--on-secondary-container); }
  .track { height: 3px; overflow: hidden; position: relative; margin: 0 -16px; }
  .bar { position: absolute; inset: 0 auto 0 0; width: 40%; background: var(--primary); animation: slide 1.1s ease-in-out infinite; }
  @keyframes slide { from { left: -40%; } to { left: 100%; } }
  @media (prefers-reduced-motion: reduce) { .bar { animation: none; width: 100%; opacity: 0.6; } }
</style>
