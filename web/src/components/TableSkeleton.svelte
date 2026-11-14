<!--
  The table before its hand (the engine still loading, or a battle's deal on its way): the arranging table itself, a
  phone's (Arranging) or a computer's (DeskArranging with the keys chosen), in its skeleton shape, so that nothing moves
  when the hand comes. `label` says what is loading to a screen reader.
-->
<script lang="ts">
  import { Arranger } from '../lib/arranger.svelte';
  import type { DeskKeys } from '../lib/desk-keys';
  import { t } from '../lib/i18n.svelte';
  import Arranging from './Arranging.svelte';
  import DeskArranging from './desk/DeskArranging.svelte';

  let { desk = false, keys, label = '' }: { desk?: boolean; keys?: DeskKeys; label?: string } = $props();
  // An empty hand, never dealt: nothing asks the engine.
  const vm = new Arranger(() => {}, () => '');
</script>

<div class="table-skeleton" role="status" aria-busy="true">
  {#if label}<span class="sr-only">{label}</span>{/if}
  {#if desk}<DeskArranging {vm} wheel={0} busy onconfirm={() => {}} {keys} skeleton />
  {:else}<Arranging {vm} wheel={0} busy confirmLabel={t('confirm')} onconfirm={() => {}} skeleton />{/if}
</div>

<style>
  .table-skeleton { flex: 1; min-height: 0; min-width: 0; display: flex; flex-direction: column; }
</style>
