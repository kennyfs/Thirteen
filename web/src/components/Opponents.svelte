<!-- The three computer players: each a network, Monte Carlo or indep. They take effect from the next hand. -->
<script lang="ts">
  import { KINDS, type App } from '../lib/app.svelte';
  import { t, ta } from '../lib/i18n.svelte';
  import Menu from './Menu.svelte';
  import TitleBar from './TitleBar.svelte';
  import Toggle from './Toggle.svelte';
  let { app, onback }: { app: App; onback: () => void } = $props();
  const modelLabel = (m: { name: string; builtIn: boolean }) => (m.builtIn ? t('built_in', m.name) : m.name);
</script>

<div class="page">
  <TitleBar title={t('opponents_title')} {onback} />
  <div class="scroll"><div class="readable">
    <p class="body-medium dim note">{t('next_hand_note')}</p>
    {#each app.seatChoices as seat, i (i)}
      <div class="seat">
        <div class="title-medium">{t('seat_pos', i + 1)}</div>
        <Toggle options={KINDS.map((k, j) => [k, ta('kinds_short')[j]] as [typeof k, string])} selected={seat.kind} onselect={(k) => app.setSeat(i, { ...seat, kind: k })} />
        <div class="line"><span class="body-large grow" class:off={seat.kind !== 'NET'}>{t('network')}</span>
          <Menu label={modelLabel(app.model(seat.model))} disabled={seat.kind !== 'NET'} options={app.models.map((m) => [m.path, modelLabel(m)] as [string, string])}
            selected={app.model(seat.model).path} onselect={(p) => app.setSeat(i, { ...seat, model: p })} /></div>
      </div>
    {/each}
    <div class="line pad"><span class="body-large grow" class:off={!app.seatChoices.some((s) => s.kind === 'MC')}>{t('mc_samples')}</span>
      <Menu label={String(app.mcSamples)} disabled={!app.seatChoices.some((s) => s.kind === 'MC')} options={[100, 200, 500, 1000].map((n) => [n, String(n)] as [number, string])}
        selected={app.mcSamples} onselect={(n) => app.chooseMcSamples(n)} /></div>
    <p class="body-medium dim note">{t('opponents_info')}</p>
  </div></div>
</div>

<style>
  .page { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .scroll { flex: 1; overflow: auto; padding-bottom: 24px; }
  .readable { max-width: 720px; margin: 0 auto; }
  .note { padding: 4px 16px; margin: 8px 0; }
  .seat { margin: 6px 12px; padding: 12px; border-radius: 16px; background: var(--surface-container); display: flex; flex-direction: column; gap: 8px; }
  .line { display: flex; align-items: center; gap: 12px; min-height: 48px; }
  .pad { padding: 8px 16px; }
  .grow { flex: 1; min-width: 0; }
  .off { color: var(--outline); }
</style>
