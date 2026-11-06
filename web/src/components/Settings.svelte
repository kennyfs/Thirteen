<!-- The settings: the look, the language, the game (rules, computer players, networks), and about. -->
<script lang="ts">
  import { TEXT_SCALE, type App, type ColorStyle, type DarkMode, type TextSize } from '../lib/app.svelte';
  import { t, ta, type Lang } from '../lib/i18n.svelte';
  import Menu from './Menu.svelte';
  import Networks from './Networks.svelte';
  import Opponents from './Opponents.svelte';
  import Rules from './Rules.svelte';
  import SettingRow from './SettingRow.svelte';
  import TitleBar from './TitleBar.svelte';
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
  const back = () => (app.settingsPage = null);
</script>

{#if app.settingsPage === 'RULES'}
  <Rules {app} onback={back} />
{:else if app.settingsPage === 'OPPONENTS'}
  <Opponents {app} onback={back} />
{:else if app.settingsPage === 'NETWORKS'}
  <Networks {app} onback={back} />
{:else}
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

      <div class="section-header">{t('settings_game')}</div>
      <SettingRow title={t('nav_rules')} value={Object.keys(app.ruleValues).length ? app.rulesName(app.ruleValues) : null} onclick={() => (app.settingsPage = 'RULES')} />
      <SettingRow title={t('opponents_title')} value={opponents} onclick={() => (app.settingsPage = 'OPPONENTS')} />
      <SettingRow title={t('networks')} value={t('networks_summary', app.models.length)} onclick={() => (app.settingsPage = 'NETWORKS')} />

      <div class="section-header">{t('settings_about')}</div>
      <SettingRow title={t('app_name')} value={t('about_source')} href="https://github.com/kennyfs/Thirteen" />
      <SettingRow title={t('privacy_policy')} href="https://github.com/kennyfs/Thirteen/blob/main/docs/privacy.md" />
    </div></div>
  </div>
{/if}

<style>
  .page { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .scroll { flex: 1; overflow: auto; padding-bottom: 24px; }
  .readable { max-width: 720px; margin: 0 auto; }
  .block { padding: 8px 16px; display: flex; flex-direction: column; gap: 8px; }
</style>
