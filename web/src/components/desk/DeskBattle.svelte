<!--
  A computer's battle pages (Battle.svelte has their state, Back and the leave dialog): the entry and the lobby in the
  middle of the window; each hand on the practice's table (DeskArranging) beside a panel with the clock and who has
  played; the wait with the rows played in large; each hand's result in large cards; the final standings with each
  hand; the analysis as practice's (DeskCandidates). Keys: Enter starts the game (the host), plays, goes on; A analyzes.
-->
<script lang="ts">
  import type { App } from '../../lib/app.svelte';
  import {
    fmtLoss, HAND_CHOICES, NAME_LENGTH, rankOf, standings, TIME_LIMITS,
    type HandOutcome, type PlayerView,
  } from '../../lib/battle.svelte';
  import { ROW_SIZES } from '../../lib/cards';
  import { t, ta, td } from '../../lib/i18n.svelte';
  import { clock, handClass as handClassOf, handValue, modeName, playerNotes, settingsLine, timedOut, timeText, totalText as totalTextOf } from '../../lib/battle-text';
  import * as R from '../../lib/rules';
  import { MAX_DESK_PLAY } from '../../lib/sizing';
  import { localTime, signed, ui } from '../../lib/ui.svelte';
  import Dialog from '../Dialog.svelte';
  import Icon from '../Icon.svelte';
  import Menu from '../Menu.svelte';
  import SeatResults from '../SeatResults.svelte';
  import ThreeRows from '../ThreeRows.svelte';
  import TitleBar from '../TitleBar.svelte';
  import Toggle from '../Toggle.svelte';
  import DeskArranging from './DeskArranging.svelte';
  import DeskCandidates from './DeskCandidates.svelte';
  import PanelSkeleton from '../PanelSkeleton.svelte';
  import TableSkeleton from '../TableSkeleton.svelte';
  import Kbd from './Kbd.svelte';
  import KeyList from './KeyList.svelte';
  import { keyList, keyMap, startHint } from '../../lib/desk-keys';

  interface Props {
    app: App; page: string; reviewing: HandOutcome | null; left: number | null;
    analysisMode: boolean; onanalyze: () => void;
  }
  let { app, page, reviewing, left, analysisMode, onanalyze }: Props = $props();
  const b = $derived(app.battle);
  const r = $derived(b.review);
  const room = $derived(b.room);
  const puzzle = $derived(room?.settings.mode === 'PUZZLE');
  const values = $derived(room ? R.values(room.rules) : {});
  const wheel = $derived(values.wheel ?? 0);
  const me = $derived(room?.players.find((p) => p.id === b.me) ?? null);
  const totalText = (p: PlayerView) => totalTextOf(p, puzzle);
  const notes = (p: PlayerView) => playerNotes(p, room ?? null, b.me);
  const handClass = (o: HandOutcome, i: number) => handClassOf(o, i, values);
  const humans = $derived(room ? room.players.filter((p) => !p.bot) : []);

  // ---- The entry: six digits typed join at once ----
  let editServer = $state(false);
  function typedPin(e: Event) {
    const el = e.currentTarget as HTMLInputElement;
    const v = el.value.replace(/\D/g, '').slice(0, 6);
    el.value = v;
    b.pin = v;
    if (v.length === 6 && !b.busy) b.join();
  }

  // ---- The lobby ----
  let copied = $state(false);
  function copyPin() {
    if (!room) return;
    navigator.clipboard?.writeText(room.pin).then(() => { copied = true; setTimeout(() => (copied = false), 1500); }, () => {});
  }
  const ruleOptions = $derived.by((): [number, string][] => {
    if (!room) return [];
    const s = room.settings;
    const names = ta('preset_names');
    const current = Object.keys(s.rules.overrides ?? {}).length === 0 ? R.PRESETS.indexOf(s.rules.preset) : -2;
    const offered = R.OFFERED.map((p) => R.PRESETS.indexOf(p));
    if (current >= 0 && !offered.includes(current)) offered.push(current);
    const out: [number, string][] = offered.map((i) => [i, names[i]]);
    out.push([-1, t('battle_rules_page_named', app.rulesName(app.ruleValues))]);
    if (current === -2) out.push([-2, app.rulesName(values)]);
    return out;
  });
  const currentRule = $derived(room && Object.keys(room.settings.rules.overrides ?? {}).length === 0 ? R.PRESETS.indexOf(room.settings.rules.preset) : -2);
  function chooseRule(i: number) {
    if (!room) return;
    if (i >= 0) b.changeSettings({ ...room.settings, rules: { preset: R.PRESETS[i], overrides: {} } });
    else if (i === -1) b.changeSettings({ ...room.settings, rules: b.rulesFromPage() });
  }

  // ---- Setting a hand ----
  let help = $state(false);
  const vm = $derived(b.arranger);
  const km = $derived(keyMap(app.deskKeys));
  const canSubmit = $derived(!b.submitting && vm.rowsEval?.valid === true);
  const hint = $derived.by(() => {
    const picked = vm.selected.size;
    const rest = vm.unplaced.length;
    if (vm.armed !== null) return td('hint_armed', ta('row_names')[vm.armed], ROW_SIZES[vm.armed] - vm.rows[vm.armed].length - picked);
    if (picked > 0) return td('hint_selected', picked);
    if (rest === 13) return startHint(app.deskKeys);
    if (rest > 0) return td('hint_left', rest);
    const ev = vm.rowsEval;
    if (!ev) return '';
    if (ev.valid === true) {
      const sum = ev.royalties.reduce((x, y) => x + y, 0);
      return sum > 0 ? t('ready_royalty', signed(sum).slice(1)) : t('ready_hint');
    }
    return ev.keys[0] <= ev.keys[1] && ev.keys[1] <= ev.keys[2] ? t('fouled_strict') : t('fouled');
  });
  const fouled = $derived(vm.unplaced.length === 0 && vm.rowsEval?.valid === false);
  const myRank = $derived(room && puzzle && room.hand > 0 ? standings(room).findIndex((p) => p.id === b.me) + 1 : 0);

  // ---- Keys: Enter starts (the host), goes on; A analyzes ----
  function onKey(e: KeyboardEvent) {
    const el = e.target as HTMLElement | null;
    if (el?.closest('input, textarea, select, [contenteditable], .menu') || document.querySelector('dialog[open]')) return;
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    const k = e.key.toLowerCase();
    const onButton = !!el?.closest('button');
    if (page === 'lobby' && room?.host === b.me && k === 'enter' && !onButton) b.startGame();
    else if ((page === 'review' || page === 'last') && ((k === 'enter' && !onButton) || k === 'n')) { if (page === 'last') b.toSummary(); else if (!me?.ready) b.ready(); }
    else if ((page === 'review' || page === 'last') && k === 'a') onanalyze();
    else if ((page === 'detail' || page === 'analysis') && k === 'escape') { if (page === 'analysis' && !r.fromReview) r.closeAnalysis(); else r.closeDetail(); }
    else return;
    e.preventDefault();
  }
</script>

<svelte:window onkeydown={onKey} />

{#snippet spinner(size = 28, color: string | null = null)}<span class="spin" style:width="{size}px" style:height="{size}px" style:color={color}></span>{/snippet}

{#snippet clockChip()}
  {#if left !== null}
    <span class="clock title-large" class:bad={left < 15000} class:pulse={left < 15000 && left > 0}><Icon name="timer" size={24} />{clock(left)}</span>
  {/if}
{/snippet}

{#snippet who()}
  <!-- Who has played this hand (or is ready for the next), and the totals. -->
  {#if room}
    <div class="people">
      {#each standings(room) as p, i (p.id)}
        {@const done = room.phase === 'PLAYING' ? p.submitted : p.ready}
        <div class="person" class:me={p.id === b.me}>
          <span class="title-small rank">{i + 1}</span>
          <span class="pname"><span class="body-large ellipsis">{p.name}</span>{#if notes(p)}<span class="body-small dim">{notes(p)}</span>{/if}</span>
          {#if !p.bot}
            <span class="state body-medium" class:good={done} class:dim={!done}>
              {#if done}<Icon name="check" size={18} />{t(room.phase === 'PLAYING' ? 'submitted' : 'battle_done_mark')}{:else}{t('thinking')}{/if}
            </span>
          {/if}
          <span class="body-large nowrap total">{totalText(p)}</span>
        </div>
      {/each}
    </div>
  {/if}
{/snippet}

{#snippet bar(index: number, withClock: boolean, o: HandOutcome | null, actions: boolean)}
  {#if room}
    <div class="bar">
      <span class="title-large nowrap">{t('battle_hand_of', index + 1, room.settings.hands)}</span>
      {#if withClock}{@render clockChip()}{/if}
      {#if o?.score}
        {@const i = o.plays.findIndex((p) => p.player === r.me)}
        {#if i >= 0}{@const x = o.score.result[i]}<span class="headline-small nowrap" class:good={x > 0} class:bad={x < 0}>{t('battle_this_hand', signed(x))}</span>{/if}
      {/if}
      {#if myRank > 0 && page !== 'playing'}<span class="body-large nowrap">{t('battle_your_rank', myRank)}</span>{/if}
      {#if analysisMode && !puzzle}<span class="body-large hl">{td('battle_tap_player')}</span>{/if}
      {#if room.phase === 'REVIEW' && page === 'review'}
        <span class="body-large dim nowrap">{t('battle_ready_count', humans.filter((p) => p.ready).length, humans.length)}</span>
      {/if}
      <span class="grow"></span>
      {#if actions}
        {#if o && (!puzzle || o.plays.some((p) => p.player === b.me))}
          <button class="btn big" class:outlined={!analysisMode || puzzle} class:tonal={analysisMode && !puzzle} onclick={onanalyze}>
            <Icon name="analytics" size={20} />{t('battle_analyze')}<Kbd k="A" inline />
          </button>
        {/if}
        {#if page === 'last'}
          <button class="btn filled big" onclick={() => b.toSummary()}>{t('battle_to_summary')}<Kbd k="Enter" inline /></button>
        {:else}
          <button class="btn filled big" disabled={me?.ready === true} onclick={() => b.ready()}>{t('battle_next')}<Kbd k="Enter" inline /></button>
        {/if}
      {/if}
    </div>
  {/if}
{/snippet}

{#snippet puzzleTable(o: HandOutcome, onplayer: ((player: number) => void) | null)}
  {#if room}
    <div class="table-wrap">
      <table class="hands">
        <thead><tr><th class="num"></th><th>{t('col_player')}</th><th></th>
          <th class="num">{t(puzzle ? 'battle_col_hand_loss' : 'battle_col_hand')}</th><th class="num">{t(puzzle ? 'battle_col_total_loss' : 'battle_col_total')}</th></tr></thead>
        <tbody>
          {#if o.best}
            <tr class="best"><td></td><td class="title-medium">{t('battle_best_rows')}</td>
              <td><ThreeRows rows={o.best} categories={r.categoriesOf(o.best)} max={64} /></td><td></td><td></td></tr>
          {/if}
          {#each standings(room) as p (p.id)}
            {@const i = o.plays.findIndex((x) => x.player === p.id)}
            {#if i >= 0}
              <tr class:me={p.id === r.me} class:pick={!!onplayer} onclick={() => onplayer?.(p.id)}>
                <td class="num title-medium">{t('battle_rank', rankOf(room, p))}</td>
                <td><span class="body-large">{p.name}</span>{#if o.plays[i].timedOut}<br /><span class="body-small dim">{timedOut()}</span>{/if}</td>
                <td><ThreeRows rows={o.plays[i].rows} categories={r.categoriesOf(o.plays[i].rows)} max={64} /></td>
                <td class="num title-large {handClass(o, i)}">{handValue(o, i)}</td>
                <td class="num title-medium">{puzzle ? fmtLoss(p.total) : signed(p.total)}</td>
              </tr>
            {/if}
          {/each}
        </tbody>
      </table>
    </div>
  {/if}
{/snippet}

{#snippet handTable(o: HandOutcome | null, onplayer: ((player: number) => void) | null)}
  {@const table = o ? r.tables[o.index] : undefined}
  {#if o && room && table}
    {@const plays = table.order.map((i) => o.plays[i])}
    {@const byId = new Map(room.players.map((p) => [p.id, p]))}
    {@const totals = plays.map((p) => r.outcomes.filter((h) => h.index <= o.index).reduce((sum, h) => {
      const i = h.plays.findIndex((x) => x.player === p.player);
      return sum + (i < 0 ? 0 : h.score?.result[i] ?? 0);
    }, 0))}
    <div class="felt seat-table">
      <SeatResults rows={plays.map((p) => p.rows)} score={table.score} {wheel}
        names={plays.map((p) => (p.player === r.me ? t('you') : byId.get(p.player)?.name ?? '?'))}
        notes={plays.map((p) => (p.timedOut ? timedOut() : null))} {totals} max={MAX_DESK_PLAY}
        onplayer={onplayer ? (s) => onplayer(plays[s].player) : null} />
    </div>
  {:else if o && room}
    {@render puzzleTable(o, onplayer)}
  {:else}
    <div class="center">{@render spinner(40)}</div>
  {/if}
{/snippet}

<div class="battle">
  {#if page === 'entry'}
    <TitleBar title={t('nav_battle')} />
    <div class="progress">{#if b.busy}<span class="pbar"></span>{/if}</div>
    <div class="scroll middle">
      <div class="entry">
        <section class="card col">
          <label class="field">
            <span class="body-small dim">{t('battle_name')}</span>
            <input class="title-medium" maxlength={NAME_LENGTH} placeholder={b.defaultName} bind:value={b.name} />
          </label>
          {#if editServer}
            <label class="field">
              <span class="body-small dim">{t('battle_server')}</span>
              <input class="body-large" placeholder={t('battle_server_hint')} bind:value={b.server} onblur={() => (b.server = b.server.trim())} />
            </label>
          {:else}
            <div class="server"><span class="body-medium dim grow">{t('battle_server_is', b.server)}</span>
              <button class="btn text" onclick={() => (editServer = true)}>{t('change')}</button></div>
          {/if}
          <div class="join">
            <span class="headline-small">{t('battle_join_room')}</span>
            <span class="body-medium dim">{t('battle_join_room_info')}</span>
            <form class="pin-row" onsubmit={(e) => { e.preventDefault(); if (b.pin.length === 6) b.join(); }}>
              <label class="field pin">
                <span class="body-small dim">{t('battle_pin')}</span>
                <input class="pin-input" inputmode="numeric" autocomplete="off" maxlength={6} value={b.pin} oninput={typedPin} />
              </label>
              <button class="btn filled big" type="submit" disabled={b.busy || b.pin.length !== 6}>{t('battle_join')}</button>
            </form>
          </div>
        </section>
        <div class="col">
          <button class="tile fill" disabled={b.busy} onclick={() => b.create('PUZZLE')}>
            <Icon name="assessment" size={36} />
            <span class="headline-small">{t('battle_new_puzzle')}</span><span class="body-large info">{t('battle_puzzle_info')}</span>
          </button>
          <button class="tile fill" disabled={b.busy} onclick={() => b.create('GAME')}>
            <Icon name="groups" size={36} />
            <span class="headline-small">{t('battle_new_game')}</span><span class="body-large info">{t('battle_game_info')}</span>
          </button>
        </div>
      </div>
    </div>

  {:else if page === 'lobby' && room}
    {@const s = room.settings}
    {@const host = room.host === b.me}
    <TitleBar title={modeName(s.mode)} onback={() => b.leave()}>
      <button class="btn outlined" onclick={() => b.leave()}>{t('battle_leave')}</button>
    </TitleBar>
    <div class="scroll middle">
      <div class="lobby">
        <div class="col">
          <div class="code fill">
            <span class="body-large sub">{t('battle_room_number')}</span>
            <span class="pin-big">{room.pin.slice(0, 3)} {room.pin.slice(3)}</span>
            <button class="btn copy" onclick={copyPin}><Icon name={copied ? 'check' : 'copy'} size={20} />{t(copied ? 'room_copied' : 'room_copy')}</button>
            <span class="body-medium sub">{t('battle_room_number_info')}</span>
          </div>
          <section class="card">
            <h2 class="section">{t('battle_players', room.players.length)}</h2>
            {#each room.players as p (p.id)}
              <div class="player">
                <span class="avatar">{[...p.name][0] ?? '?'}</span>
                <span class="body-large ellipsis grow">{p.name}</span>
                <span class="body-medium dim">{notes(p)}</span>
              </div>
            {/each}
          </section>
        </div>
        <div class="col">
          <section class="card">
            <h2 class="section">{t('battle_settings')}</h2>
            {#if host}
              <div class="setting"><span class="body-large grow">{t('battle_hands')}</span>
                <Menu label={t('battle_hands_n', s.hands)} options={[...new Set([...HAND_CHOICES, s.hands])].sort((x, y) => x - y).map((n) => [n, t('battle_hands_n', n)] as [number, string])}
                  selected={s.hands} onselect={(n) => b.changeSettings({ ...s, hands: n })} /></div>
              <div class="setting"><span class="body-large grow">{t('battle_time')}</span>
                <Menu label={timeText(s.timeLimit)} options={TIME_LIMITS.map((x) => [x, timeText(x)] as [number, string])}
                  selected={s.timeLimit} onselect={(x) => b.changeSettings({ ...s, timeLimit: x })} /></div>
              <div class="setting"><span class="body-large grow">{t('nav_rules')}</span>
                <Menu label={ruleOptions.find(([i]) => i === currentRule)?.[1] ?? app.rulesName(values)} options={ruleOptions}
                  selected={currentRule} onselect={chooseRule} /></div>
            {:else}
              <div class="setting"><span class="body-large grow">{t('battle_hands')}</span><span class="body-large dim">{t('battle_hands_n', s.hands)}</span></div>
              <div class="setting"><span class="body-large grow">{t('battle_time')}</span><span class="body-large dim">{timeText(s.timeLimit)}</span></div>
              <div class="setting"><span class="body-large grow">{t('nav_rules')}</span><span class="body-large dim">{app.rulesName(values)}</span></div>
            {/if}
          </section>
          {#if host}
            <button class="btn filled big start" onclick={() => b.startGame()}>{t('battle_start_mode', modeName(s.mode))}<Kbd k="Enter" inline /></button>
          {:else}
            <div class="waiting card">{@render spinner()}<span class="body-large">{t('battle_wait_host')}</span></div>
          {/if}
        </div>
      </div>
    </div>

  {:else if page === 'playing' && room}
    <div class="desk">
      <div class="main">
        {#if b.deal}
          <DeskArranging {vm} {wheel} busy={b.submitting} onconfirm={() => b.submit()} keys={app.deskKeys} bind:help />
        {:else}<TableSkeleton desk keys={app.deskKeys} />{/if}
      </div>
      <aside class="side">
        <div class="side-head">
          <span class="title-large">{t('battle_hand_of', room.hand + 1, room.settings.hands)}</span>
          {@render clockChip()}
        </div>
        <div class="body-medium dim">{t('players_done', humans.filter((p) => p.submitted).length, humans.length)}{#if myRank > 0} · {t('battle_your_rank', myRank)}{/if}</div>
        {@render who()}
        <p class="hint title-medium" class:bad={fouled} aria-live="polite">{hint}</p>
        <div class="sortline"><Toggle options={[[false, t('sort_rank')], [true, t('sort_suit')]] as [boolean, string][]} selected={vm.sortBySuit} onselect={(v) => (vm.sortBySuit = v)} /><Kbd k={km.sortLabel} /></div>
        <div class="tools">
          <button class="btn outlined" disabled={!vm.canUndo} onclick={() => vm.undo()}><Icon name="undo" />{t('undo')}<Kbd k={km.undoLabel} inline /></button>
          <button class="btn outlined" disabled={!vm.canClear} onclick={() => vm.clearRows()}><Icon name="clearAll" />{t('take_all')}<Kbd k="Del" inline /></button>
        </div>
        <button class="btn filled big" disabled={!canSubmit} onclick={() => b.submit()}>{b.submitting ? '…' : t('battle_submit')}<Kbd k="Enter" inline /></button>
        <span class="grow"></span>
        <KeyList keys={keyList(app.deskKeys)} mouse={t('mouse_tips')} onhelp={() => (help = true)} />
      </aside>
    </div>
    {#if help}
      <Dialog title={t('arrange_help_title')} onclose={() => (help = false)}>
        <p class="body-large help-text">{t('arrange_help_desk', keyMap(app.deskKeys).rowLabels.join(t('list_sep')))}</p>
        <p class="body-medium dim help-text">{t('mouse_tips')}</p>
        {#snippet actions()}<button class="btn text" onclick={() => (help = false)}>{t('ok')}</button>{/snippet}
      </Dialog>
    {/if}

  {:else if page === 'waiting' && room}
    <!-- The table as it was when the hand was handed in, the same size, the hand's place saying what is awaited. -->
    <div class="desk">
      <div class="main">
        <DeskArranging {vm} {wheel} busy onconfirm={() => {}} keys={app.deskKeys} locked>
          {#snippet waiting()}
            <span class="waiting on-felt-text">{@render spinner(28, 'var(--on-felt)')}<span class="headline-small">{t('battle_waiting')}</span></span>
          {/snippet}
        </DeskArranging>
      </div>
      <aside class="side">
        <div class="side-head">
          <span class="title-large">{t('battle_hand_of', room.hand + 1, room.settings.hands)}</span>
          {@render clockChip()}
        </div>
        <div class="body-medium dim">{t('players_done', humans.filter((p) => p.submitted).length, humans.length)}{#if myRank > 0} · {t('battle_your_rank', myRank)}{/if}</div>
        {@render who()}
      </aside>
    </div>

  {:else if (page === 'review' || page === 'last') && room}
    {@const o = reviewing}
    {@render bar(o?.index ?? room.hand, page !== 'last', o, true)}
    {@render handTable(o, analysisMode && !puzzle && o ? (p) => b.analyzeHand(o.index, p) : null)}

  {:else if page === 'summary' && room}
    <TitleBar title={(b.opened ? localTime(new Date(b.openedAt).toISOString()) : t('battle_over')) + ' · ' + modeName(room.settings.mode)}>
      {#if b.opened}
        {@const saved = app.favoriteBattleIds.has(b.opened)}
        <button class="btn icon text" aria-label={t(saved ? 'favorite_battle_remove' : 'favorite_battle_add')} onclick={() => app.toggleOpenedBattle()}>
          <span class:star={saved}><Icon name={saved ? 'star' : 'starBorder'} /></span>
        </button>
      {/if}
      <button class="btn filled" onclick={() => app.battleDone()}>{t('battle_done')}</button>
    </TitleBar>
    <div class="scroll">
      <div class="summary">
        <div class="podium">
          {#each standings(room) as p (p.id)}
            {@const place = rankOf(room, p)}
            <div class="place p{Math.min(place, 4)}" class:me={p.id === r.me}>
              <span class="headline-small">{t('battle_place', place)}</span>
              <span class="title-medium ellipsis">{p.name}</span>
              <span class="title-large">{totalText(p)}</span>
            </div>
          {/each}
        </div>
        <p class="body-medium dim">{settingsLine(room, app.rulesName(values))}</p>
        <section class="card">
          <h2 class="section">{td('battle_hand_list')}</h2>
          <table class="hands">
            <thead><tr><th class="num"></th><th>{t('your_rows')}</th><th class="num">{t(puzzle ? 'battle_col_hand_loss' : 'battle_col_hand')}</th><th class="num">{t('battle_col_hand_rank')}</th></tr></thead>
            <tbody>
              {#each r.outcomes as o (o.index)}
                {@const i = o.plays.findIndex((p) => p.player === r.me)}
                {@const value = (j: number) => o.losses?.[j] ?? -(o.score?.result[j] ?? 0)}
                <tr class="pick hand-line" onclick={() => { if (i >= 0) r.openDetail(o.index); }}>
                  <td class="num title-medium">{o.index + 1}</td>
                  <td>{#if i >= 0}<ThreeRows rows={o.plays[i].rows} categories={r.categoriesOf(o.plays[i].rows)} max={56} />{/if}</td>
                  <td class="num">{#if i >= 0}<span class="title-medium {handClass(o, i)}">{handValue(o, i)}</span>{#if o.plays[i].timedOut}<br /><span class="body-small dim">{timedOut()}</span>{/if}{/if}</td>
                  <td class="num title-medium">{i >= 0 ? t('battle_hand_rank', 1 + o.plays.filter((_, j) => value(j) < value(i) - 1e-9).length, o.plays.length) : ''}</td>
                </tr>
              {/each}
            </tbody>
          </table>
        </section>
      </div>
    </div>

  {:else if page === 'detail' && room}
    {@const o = r.outcomes.find((x) => x.index === r.detail) ?? null}
    {#if o}
      <TitleBar title={t('battle_hand_of', o.index + 1, room.settings.hands)} onback={() => r.closeDetail()}>
        <span class="body-medium hl">{td('battle_tap_player')}</span>
        {#if ui.showKeys}<span class="body-small dim keys"><Kbd k="Esc" />{t('k_back')}</span>{/if}
      </TitleBar>
      {@render handTable(o, (p) => r.openAnalysis(p))}
    {/if}

  {:else if page === 'analysis' && room}
    {@const o = r.outcomes.find((x) => x.index === r.detail)}
    {@const play = o?.plays.find((p) => p.player === r.analyzed)}
    {#if o && play}
      {@const name = room.players.find((p) => p.id === play.player)?.name ?? '?'}
      <TitleBar title={`${t('battle_hand_of', o.index + 1, room.settings.hands)} · ${name}`} onback={() => (r.fromReview ? r.closeDetail() : r.closeAnalysis())}>
        {#if ui.showKeys}<span class="body-small dim keys"><Kbd k="Esc" />{t('k_back')}</span>{/if}
      </TitleBar>
      {#if r.detailAnalysis}
        <DeskCandidates a={r.detailAnalysis} mine={play.rows} tags={r.detailTags} mineLabel={play.player === r.me ? null : name} />
      {:else}
        <PanelSkeleton label={t('analyzing')} />
      {/if}
    {/if}
  {/if}
</div>

<style>
  .battle { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .scroll { flex: 1; min-height: 0; overflow: auto; }
  .middle { display: flex; flex-direction: column; justify-content: safe center; padding: 12px 24px 32px; }
  .center { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; padding: 24px; }
  .grow { flex: 1; min-width: 0; }
  .hl { color: var(--primary); }
  .spin { display: inline-block; flex: none; border-radius: 50%; border: 3px solid transparent; border-top-color: currentColor;
    color: var(--primary); animation: spin 0.9s linear infinite; box-sizing: border-box; }
  .felt .spin { color: var(--on-felt); }
  @keyframes spin { to { transform: rotate(360deg); } }
  .progress { height: 4px; flex: none; overflow: hidden; position: relative; }
  .pbar { position: absolute; inset: 0 auto 0 0; width: 40%; background: var(--primary); animation: slide 1.1s ease-in-out infinite; }
  @keyframes slide { from { left: -40%; } to { left: 100%; } }
  .card { background: var(--surface-container-low); border-radius: 20px; padding: 16px 20px; }
  .section { font-size: 0.875rem; font-weight: 500; color: var(--primary); margin: 0 0 6px; }
  .col { display: flex; flex-direction: column; gap: 14px; min-width: 0; }

  /* The entry */
  .entry { display: grid; grid-template-columns: minmax(340px, 0.9fr) 1.2fr; gap: 24px; max-width: 1080px; width: 100%; margin: 0 auto; }
  .field { display: flex; flex-direction: column; gap: 2px; padding: 6px 14px 8px; border: 1px solid var(--outline); border-radius: 8px; background: var(--surface); }
  .field:focus-within { border: 2px solid var(--primary); padding: 5px 13px 7px; }
  .field input { border: none; outline: none; background: none; color: var(--on-surface); padding: 2px 0; font: inherit; width: 100%; min-width: 0; }
  .server { display: flex; align-items: center; gap: 8px; }
  .join { display: flex; flex-direction: column; gap: 8px; border-top: 1px solid var(--outline-variant); padding-top: 16px; margin-top: 4px; }
  .pin-row { display: flex; align-items: center; gap: 10px; }
  .pin { flex: 1; }
  .pin-input { font-size: 1.75rem !important; font-weight: 700; letter-spacing: 10px; }
  .tile { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; text-align: left; padding: 22px 24px; border-radius: 22px; font: inherit; border: none; cursor: pointer; flex: 1; }
  .tile .headline-small { font-weight: 700; font-size: 1.6rem; line-height: 2rem; }
  .tile:disabled { opacity: 0.6; cursor: default; }
  .tile:not(:disabled):hover { filter: brightness(1.08); }
  .fill { background: var(--felt); color: var(--on-felt); }
  :global(:root.minimal) .fill { background: var(--secondary-container); color: var(--on-secondary-container); }
  .tile .info { opacity: 0.9; }

  /* The lobby */
  .lobby { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; max-width: 1080px; width: 100%; margin: 0 auto; align-items: start; }
  .code { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 6px; padding: 20px; border-radius: 24px; }
  .code .sub { opacity: 0.85; }
  .pin-big { font-size: 3.25rem; line-height: 1.2; font-weight: 700; letter-spacing: 6px; }
  .copy { background: color-mix(in srgb, currentColor 14%, transparent); color: inherit; }
  .player { display: flex; align-items: center; gap: 12px; min-height: 52px; }
  .avatar { flex: none; width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
    background: var(--primary-container); color: var(--on-primary-container); font-weight: 700; }
  .setting { display: flex; align-items: center; gap: 12px; min-height: 56px; }
  .start { width: 100%; }
  .waiting { display: flex; align-items: center; gap: 12px; }

  /* Setting a hand, and the wait */
  .desk { flex: 1; display: flex; min-height: 0; }
  .main { flex: 1; min-width: 0; display: flex; flex-direction: column; padding-top: 12px; }
  .side { flex: none; width: 340px; display: flex; flex-direction: column; gap: 12px; padding: 12px 16px 16px 4px; min-height: 0; overflow: auto; }
  .side-head { display: flex; align-items: center; gap: 12px; min-height: 40px; flex-wrap: wrap; }
  .clock { display: inline-flex; align-items: center; gap: 4px; padding: 2px 10px; border-radius: 10px; background: var(--surface-container-high); }
  .clock.bad { color: var(--on-error-container); background: var(--error-container); }
  .pulse { animation: pulse 1s ease-in-out infinite; }
  @keyframes pulse { 50% { opacity: 0.55; } }
  .hint { margin: 0; min-height: 2.9em; }
  .sortline { display: flex; align-items: center; gap: 8px; color: var(--on-surface-variant); }
  .sortline :global(.group) { flex: 1; }
  .tools { display: flex; flex-wrap: wrap; gap: 8px; }
  .tools .btn { flex: 1 1 auto; padding: 0 12px; }
  .side .big { width: 100%; }
  .help-text { white-space: pre-line; margin: 0; }
  .people { display: flex; flex-direction: column; gap: 2px; background: var(--surface-container-low); border-radius: 16px; padding: 6px; }
  .card .people { background: none; padding: 0; }
  .person { display: flex; align-items: center; gap: 10px; min-height: 44px; padding: 2px 10px; border-radius: 10px; }
  .person.me { background: var(--secondary-container); }
  .rank { width: 1.2em; }
  .pname { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  .state { display: inline-flex; align-items: center; gap: 3px; white-space: nowrap; }
  .total { min-width: 3.5em; text-align: right; }
  .bar { display: flex; align-items: center; gap: 14px; padding: 8px 16px; min-height: 64px; flex: none; flex-wrap: wrap; }
  .on-felt-text { color: var(--on-felt); }

  /* A hand's result */
  .seat-table { flex: 1; min-height: 0; margin: 0 16px 16px; padding: 12px; display: flex; flex-direction: column; }
  .seat-table > :global(.results) { flex: 1; min-height: 0; display: flex; flex-direction: column; justify-content: safe center; }
  .table-wrap { flex: 1; min-height: 0; overflow: auto; padding: 0 16px 16px; }
  table.hands { width: 100%; max-width: 1400px; margin: 0 auto; border-collapse: collapse; }
  .hands th { text-align: left; font-size: 0.8125rem; font-weight: 500; color: var(--on-surface-variant); padding: 6px 10px; border-bottom: 1px solid var(--outline-variant); white-space: nowrap; }
  .hands td { padding: 8px 10px; border-bottom: 1px solid color-mix(in srgb, var(--outline-variant) 50%, transparent); vertical-align: middle; }
  .hands td:nth-child(3), .summary .hands td:nth-child(2) { width: 70%; }
  .hands .num { text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
  .hands tr.me { background: var(--secondary-container); }
  .hands tr.best td { background: var(--surface-container-low); }
  .hands tr.pick { cursor: pointer; }
  .hands tr.pick:hover { background: var(--surface-container); }
  .keys { display: inline-flex; align-items: center; gap: 4px; }

  /* The final standings */
  .summary { padding: 4px 16px 24px; max-width: 1200px; margin: 0 auto; display: flex; flex-direction: column; gap: 12px; }
  .summary p { margin: 0; }
  .podium { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; }
  .place { display: flex; flex-direction: column; gap: 4px; padding: 16px 18px; border-radius: 18px; background: var(--surface-container); min-width: 0; }
  .place.p1 { background: color-mix(in srgb, #f0c058 40%, var(--surface-container)); }
  .place.p2 { background: color-mix(in srgb, #b8c4cc 40%, var(--surface-container)); }
  .place.p3 { background: color-mix(in srgb, #d49a6a 35%, var(--surface-container)); }
  .place.me { outline: 2px solid var(--primary); outline-offset: -2px; }
  .star { color: var(--highlight); display: contents; }
</style>
