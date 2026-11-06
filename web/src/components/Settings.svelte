<!-- The settings: the look, the language, the game (rules, computer players, networks), and about. -->
<script lang="ts">
  import { TEXT_SCALE, type App, type ColorStyle, type DarkMode, type TextSize } from '../lib/app.svelte';
  import { t, ta, type Lang } from '../lib/i18n.svelte';
  import { DESK_KEYS } from '../lib/desk-keys';
  import { nav } from '../lib/nav.svelte';
  import Menu from './Menu.svelte';
  import Networks from './Networks.svelte';
  import Opponents from './Opponents.svelte';
  import Rules from './Rules.svelte';
  import SettingRow from './SettingRow.svelte';
  import TitleBar from './TitleBar.svelte';
  import { ui } from '../lib/ui.svelte';
  import Toggle from './Toggle.svelte';
  let { app }: { app: App } = $props();
  const sizes = Object.keys(TEXT_SCALE) as TextSize[];
  const styles: ColorStyle[] = ['TABLE', 'MINIMAL'];
  const darks: DarkMode[] = ['SYSTEM', 'LIGHT', 'DARK'];
  const name = (s: { kind: string; model: string }) => (s.kind === 'NET' ? app.model(s.model).name : ta('kinds')[['NET', 'MC', 'INDEP'].indexOf(s.kind)]);
  const opponents = $derived.by(() => {
    const s = app.seatChoices;
    return s.every((x) => x.kind === s[0].kind && x.model === s[0].model) ? t('opponents_all', name(s[0])) : s.map(name).join('、');
  });
  const back = () => nav.back();
</script>

{#snippet list(desk: boolean)}
  <div class="page">
    <TitleBar title={t('nav_settings')} />
    <div class="scroll"><div class="readable">
      <div class="section-header">{t('settings_look')}</div>
      <div class="block">
        <div class="body-large">{t('text_size')}</div>
        <Toggle options={sizes.map((s, i) => [s, ta('text_sizes')[i]] as [TextSize, string])} selected={app.textSize} onselect={(v) => app.chooseTextSize(v)} />
      </div>
      <SettingRow title={t('color_style')}>
        {#snippet trailing()}<Menu label={ta('color_styles')[styles.indexOf(app.colorStyle)]} options={styles.map((s, i) => [s, ta('color_styles')[i]] as [ColorStyle, string])}
          selected={app.colorStyle} onselect={(v) => app.chooseColorStyle(v)} />{/snippet}
      </SettingRow>
      <SettingRow title={t('dark_mode')}>
        {#snippet trailing()}<Menu label={ta('dark_modes')[darks.indexOf(app.darkMode)]} options={darks.map((s, i) => [s, ta('dark_modes')[i]] as [DarkMode, string])}
          selected={app.darkMode} onselect={(v) => app.chooseDarkMode(v)} />{/snippet}
      </SettingRow>
      <SettingRow title="Language · 語言">
        {#snippet trailing()}<Menu label={app.lang === 'zh' ? '繁體中文' : 'English'} options={[['zh', '繁體中文'], ['en', 'English']] as [Lang, string][]}
          selected={app.lang} onselect={(v) => app.chooseLang(v)} />{/snippet}
      </SettingRow>

      {#if desk}
        <div class="block">
          <div class="body-large">{t('desk_keys')}</div>
          <div class="radios" role="radiogroup" aria-label={t('desk_keys')}>
            {#each DESK_KEYS as k, i (k)}
              <button class="radio-row" role="radio" aria-checked={app.deskKeys === k} onclick={() => app.chooseDeskKeys(k)}>
                <span class="radio" class:on={app.deskKeys === k}></span><span class="body-medium">{ta('desk_keys_names')[i]}</span>
              </button>
            {/each}
          </div>
          <div class="body-small dim">{t('desk_keys_note')}</div>
        </div>
      {/if}

      <div class="section-header">{t('settings_game')}</div>
      <SettingRow title={t('nav_rules')} value={Object.keys(app.ruleValues).length ? app.rulesName(app.ruleValues) : null} onclick={() => nav.open('rules')} selected={desk && (nav.sub ?? 'rules') === 'rules'} />
      <SettingRow title={t('opponents_title')} value={opponents} onclick={() => nav.open('opponents')} selected={desk && nav.sub === 'opponents'} />
      <SettingRow title={t('networks')} value={t('networks_summary', app.models.length)} onclick={() => nav.open('networks')} selected={desk && nav.sub === 'networks'} />

      <div class="section-header">{t('settings_about')}</div>
      <SettingRow title={t('app_name')} value={t('about_source')} href="https://github.com/kennyfs/Thirteen" />
      <SettingRow title={t('about_play')} value={t('about_play_note')} href="https://play.google.com/store/apps/details?id=io.github.kennyfs.thirteen" />
      <SettingRow title={t('privacy_policy')} href="https://github.com/kennyfs/Thirteen/blob/main/docs/privacy.md" />
    </div></div>
  </div>
{/snippet}

{#if ui.desk}
  <!-- A computer's: the list at the left, the page chosen in it (the rules at first) at the right. -->
  <div class="desk">
    <div class="desk-list">{@render list(true)}</div>
    <div class="desk-pane">
      {#if nav.sub === 'opponents'}<Opponents {app} />
      {:else if nav.sub === 'networks'}<Networks {app} />
      {:else}<Rules {app} />{/if}
    </div>
  </div>
{:else if nav.sub === 'rules'}
  <Rules {app} onback={back} />
{:else if nav.sub === 'opponents'}
  <Opponents {app} onback={back} />
{:else if nav.sub === 'networks'}
  <Networks {app} onback={back} />
{:else}
  {@render list(false)}
{/if}

<style>
  .page { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .scroll { flex: 1; overflow: auto; padding-bottom: 24px; }
  .readable { max-width: 720px; margin: 0 auto; }
  .desk { height: 100%; display: flex; min-height: 0; }
  .desk-list { flex: 0 0 360px; border-right: 1px solid var(--outline-variant, var(--surface-container-highest)); }
  .desk-pane { flex: 1; min-width: 0; }
  /* The pages beside the list start at its edge, not in the middle of the room. */
  .desk-pane :global(.readable) { margin: 0; max-width: 960px; padding: 0 12px; }
  .block { padding: 8px 16px; display: flex; flex-direction: column; gap: 8px; }
  .radios { display: flex; flex-direction: column; }
  .radio-row { display: flex; align-items: center; gap: 10px; min-height: 36px; padding: 0; background: none; border: none; color: inherit; cursor: pointer; font: inherit; text-align: left; }
  .radio { flex: none; width: 18px; height: 18px; border-radius: 50%; border: 2px solid var(--on-surface-variant); box-sizing: border-box; }
  .radio.on { border-color: var(--primary); background: radial-gradient(var(--primary) 0 4px, transparent 4.5px); }
</style>
