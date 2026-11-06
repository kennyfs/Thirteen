<!--
  The page: the screens, with a bar of them under on a tall window and a rail at the side on a wide one, as the app.
  The colors and the text size go on <html>, so that everything, dialogs too, follows them.
-->
<script lang="ts">
  import { onMount } from 'svelte';
  import { App, TEXT_SCALE, type Screen } from './lib/app.svelte';
  import { nav } from './lib/nav.svelte';
  import * as engine from './lib/engine';
  import { t } from './lib/i18n.svelte';
  import { measure, ui } from './lib/ui.svelte';
  import Analyze from './components/Analyze.svelte';
  import Dialog from './components/Dialog.svelte';
  import Icon, { type IconName } from './components/Icon.svelte';
  import Play from './components/Play.svelte';
  import Records from './components/Records.svelte';
  import Settings from './components/Settings.svelte';

  const app = new App();
  // For the page's screenshot script (scripts/shots.py): the state and the engine, only when asked.
  if (new URLSearchParams(location.search).has('test')) Object.assign(window, { thirteen: { app, engine } });
  // As the app's: an outlined icon, filled when the screen is shown.
  const tabs: [Screen, string, IconName, IconName][] = [
    ['PLAY', 'nav_play', 'styleOutlined', 'style'], ['ANALYZE', 'nav_analyze', 'insightsOutlined', 'insights'],
    ['RECORDS', 'nav_records', 'historyOutlined', 'historyFilled'], ['SETTINGS', 'nav_settings', 'settingsOutlined', 'settingsFilled'],
  ];
  let systemDark = $state(matchMedia('(prefers-color-scheme: dark)').matches);
  const dark = $derived(app.darkMode === 'DARK' || (app.darkMode === 'SYSTEM' && systemDark));

  $effect(() => {
    const root = document.documentElement;
    root.classList.toggle('dark', dark);
    root.classList.toggle('minimal', app.colorStyle === 'MINIMAL');
    root.style.setProperty('--text-scale', String(TEXT_SCALE[app.textSize]));
    document.querySelector('meta[name=theme-color]')?.setAttribute('content', dark ? '#0f1512' : '#f5fbf6');
    measure(TEXT_SCALE[app.textSize]);
  });
  $effect(() => { document.title = t('app_name'); });

  onMount(() => {
    const mq = matchMedia('(prefers-color-scheme: dark)');
    const onDark = () => (systemDark = mq.matches);
    const onResize = () => measure(TEXT_SCALE[app.textSize]);
    mq.addEventListener('change', onDark);
    addEventListener('resize', onResize);
    // A phone held sideways loses much of its height to the browser's bars: the page goes full screen at the first tap
    // (a browser allows it only on one), and back when the phone is upright again. Not on a tablet, and not where the
    // browser cannot (an iPhone's Safari: there, Add to Home Screen opens the page without the bars, manifest.webmanifest).
    const phone = () => matchMedia('(pointer: coarse)').matches && Math.min(screen.width, screen.height) < 600;
    const sideways = () => innerWidth > innerHeight;
    let ours = false;
    const onTap = () => {
      if (!phone() || !sideways() || document.fullscreenElement || !document.documentElement.requestFullscreen) return;
      if (matchMedia('(display-mode: fullscreen), (display-mode: standalone)').matches) return;
      document.documentElement.requestFullscreen({ navigationUI: 'hide' }).then(() => { ours = true; }, () => {});
    };
    const onTurn = () => {
      if (ours && !sideways() && document.fullscreenElement) document.exitFullscreen().catch(() => {});
      if (!document.fullscreenElement) ours = false;
    };
    addEventListener('click', onTap, true);
    addEventListener('resize', onTurn);
    return () => {
      mq.removeEventListener('change', onDark);
      removeEventListener('resize', onResize);
      removeEventListener('click', onTap, true);
      removeEventListener('resize', onTurn);
    };
  });

  function go(s: Screen) {
    // The screen shown again: back to its top.
    nav.go(s);
  }
  const openRules = () => nav.open('rules');
</script>

<div class="shell" class:wide={ui.wide}>
  <nav aria-label={t('app_name')}>
    {#each tabs as [s, label, icon, chosen] (s)}
      <button class:on={app.screen === s} aria-current={app.screen === s ? 'page' : undefined} onclick={() => go(s)}>
        <span class="pill"><Icon name={app.screen === s ? chosen : icon} flip={s === 'PLAY'} /></span><span class="label">{t(label)}</span>
      </button>
    {/each}
  </nav>
  <main>
    {#if !app.ready}
      <div class="loading"><span class="spinner"></span></div>
    {:else if app.screen === 'PLAY'}
      <Play {app} onrules={openRules} />
    {:else if app.screen === 'ANALYZE'}
      <Analyze {app} onrules={openRules} />
    {:else if app.screen === 'RECORDS'}
      <Records {app} />
    {:else}
      <Settings {app} />
    {/if}
  </main>
</div>

{#if app.error}
  <Dialog onclose={() => (app.error = null)}>
    <p class="body-large" style="margin:0">{app.error}</p>
    {#snippet actions()}<button class="btn text" onclick={() => (app.error = null)}>{t('ok')}</button>{/snippet}
  </Dialog>
{/if}

<style>
  /* Clear of a notch and the rounded corners, which a page in full screen (or from the home screen) reaches. */
  .shell { height: 100dvh; display: flex; flex-direction: column-reverse;
    padding: env(safe-area-inset-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left); }
  .shell.wide { flex-direction: row; }
  main { flex: 1; min-height: 0; min-width: 0; position: relative; }
  nav { flex: none; display: flex; justify-content: space-around; height: 80px; background: var(--surface-container); }
  .wide nav { flex-direction: column; justify-content: flex-start; gap: 12px; height: auto; width: 88px; padding-top: 12px; background: var(--surface); }
  nav button { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; border: none; background: none;
    color: var(--on-surface-variant); cursor: pointer; font-size: 0.75rem; font-weight: 500; padding: 0; min-width: 0; }
  .wide nav button { flex: none; padding: 4px 0; }
  nav button.on { color: var(--on-surface); }
  .pill { width: 64px; height: 32px; border-radius: 16px; display: flex; align-items: center; justify-content: center; }
  .wide .pill { width: 56px; }
  nav button.on .pill { background: var(--secondary-container); color: var(--on-secondary-container); }
  nav button:hover .pill { background: var(--surface-container-highest); }
  .label { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }
  .loading { height: 100%; display: flex; align-items: center; justify-content: center; }
  .spinner { width: 40px; height: 40px; border-radius: 50%; border: 4px solid transparent; border-top-color: var(--primary); animation: spin 0.9s linear infinite; }
  @keyframes spin { to { transform: rotate(360deg); } }
</style>
