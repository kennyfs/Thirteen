<!--
  Battles online, with the app's players (a port of the app's BattleScreen.kt, without the nearby rooms): the entry,
  the room's lobby, each hand to set, the wait for the others, each hand's result, the final standings, a hand's table
  and a player's analysis from them. Back (the browser's too) goes one page up, and in a room asks before leaving it.
-->
<script lang="ts">
  import type { App } from '../lib/app.svelte';
  import {
    fmtLoss, HAND_CHOICES, NAME_LENGTH, rankOf, standings, TIME_LIMITS,
    type HandOutcome, type Mode, type PlayerView, type RoomView,
  } from '../lib/battle.svelte';
  import { t, ta } from '../lib/i18n.svelte';
  import { nav } from '../lib/nav.svelte';
  import * as R from '../lib/rules';
  import { lineCard } from '../lib/sizing';
  import { localTime, signed, textRoom, ui } from '../lib/ui.svelte';
  import Arranging from './Arranging.svelte';
  import CandidateList from './CandidateList.svelte';
  import Dialog from './Dialog.svelte';
  import Icon from './Icon.svelte';
  import Menu from './Menu.svelte';
  import RowCards from './RowCards.svelte';
  import SeatResults from './SeatResults.svelte';
  import ThreeRows from './ThreeRows.svelte';
  import TitleBar from './TitleBar.svelte';
  import DeskBattle from './desk/DeskBattle.svelte';

  let { app }: { app: App } = $props();
  const b = $derived(app.battle);
  const r = $derived(b.review);
  const room = $derived(b.room);
  const over = $derived(b.over);

  // ---- Back ----
  let confirmLeave = $state(false);
  const back = $derived.by((): (() => void) | null => {
    if (r.detail !== null && room) return r.analyzed !== null && !r.fromReview ? () => r.closeAnalysis() : () => r.closeDetail();
    if (!room) return null;
    if (over && !b.lastSeen && b.outcomes.length > 0) return () => b.toSummary();
    if (over) return () => app.battleDone();
    return () => (confirmLeave = true);
  });
  // What Back does is read when it is pressed: two quick ones go up two pages.
  const goBack = () => back?.();
  $effect(() => { nav.guard(back ? goBack : null); });
  $effect(() => () => nav.guard(null));

  // ---- The room's clock ----
  let now = $state(Date.now());
  $effect(() => {
    const id = setInterval(() => (now = Date.now()), 500);
    return () => clearInterval(id);
  });
  const left = $derived(room?.remainingMs != null ? Math.max(0, room.remainingMs - (now - b.roomAt)) : null);
  const clock = (ms: number) => { const s = Math.floor(ms / 1000); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };

  // ---- What the pages share ----
  const modeName = (m: Mode) => t(m === 'PUZZLE' ? 'battle_puzzle' : 'battle_game');
  const puzzle = $derived(room?.settings.mode === 'PUZZLE');
  const values = $derived(room ? R.values(room.rules) : {});
  const wheel = $derived(values.wheel ?? 0);
  const me = $derived(room?.players.find((p) => p.id === b.me) ?? null);
  const totalText = (p: PlayerView) => (puzzle ? t('battle_total_loss', fmtLoss(p.total)) : signed(p.total));
  function notes(p: PlayerView): string {
    const out: string[] = [];
    if (p.id === b.me) out.push(t('you'));
    if (room && p.id === room.host) out.push(t('battle_host'));
    // A network's seat is already named "AI n".
    if (!p.bot && !p.connected) out.push(t('battle_away'));
    return out.join('、');
  }
  const timeText = (s: number) => (s === 0 ? t('battle_no_limit') : t('battle_seconds', s));
  /** A hand's value: a puzzle's loss ("0.144", nothing lost "0"), or a table's points ("+3"). */
  function handValue(o: HandOutcome, i: number): string {
    const loss = o.losses?.[i];
    if (loss != null) return loss < 0.0005 ? '0' : fmtLoss(loss);
    const x = o.score?.result[i];
    return x == null ? '' : signed(x);
  }
  function handClass(o: HandOutcome, i: number): string {
    const loss = o.losses?.[i];
    if (loss != null) return loss < 0.0005 ? 'good' : loss >= 0.5 * R.notableQGap(values) ? 'bad' : '';
    const x = o.score?.result[i] ?? 0;
    return x > 0 ? 'good' : x < 0 ? 'bad' : '';
  }
  const timedOut = () => t('battle_timed_out').trim();

  // ---- The entry ----
  let editServer = $state(false);

  // ---- The lobby's rules ----
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

  // ---- A hand's result ----
  const reviewing = $derived.by((): HandOutcome | null => {
    if (!room) return null;
    if (over) return b.outcomes.at(-1) ?? null;
    return [...b.outcomes].reverse().find((o) => o.index === room.hand) ?? null;
  });
  let analysisMode = $state(false);
  let modeFor = $state(-1);
  $effect(() => { const i = reviewing?.index ?? -1; if (i !== modeFor) { modeFor = i; analysisMode = false; } });

  /** The page shown, in the app's order. */
  const page = $derived.by(() => {
    if (r.detail !== null && room) return r.analyzed !== null ? 'analysis' : 'detail';
    if (!room) return 'entry';
    if (over && !b.lastSeen && b.outcomes.length > 0) return 'last';
    if (over) return 'summary';
    if (room.phase === 'LOBBY') return 'lobby';
    if (room.phase === 'PLAYING' && !me?.submitted) return 'playing';
    if (room.phase === 'PLAYING') return 'waiting';
    return 'review';
  });

  function analyzeButton() {
    const o = reviewing;
    if (!o) return;
    if (puzzle) b.analyzeHand(o.index, b.me);
    else analysisMode = !analysisMode;
  }

  // The keys of a computer's pages are DeskBattle's.
  function onKey(e: KeyboardEvent) {
    if (ui.desk || (page !== 'review' && page !== 'last')) return;
    const el = e.target as HTMLElement | null;
    if (el?.closest('input, textarea, select, [contenteditable]') || document.querySelector('dialog[open]')) return;
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    const k = e.key.toLowerCase();
    if (k === 'enter' || k === 'n') { if (page === 'last') b.toSummary(); else if (!me?.ready) b.ready(); }
    else if (k === 'a') analyzeButton();
    else return;
    e.preventDefault();
  }

  // The tables' sizes.
  let tableW = $state(0);
  let listW = $state(0);
  const rankW = $derived(textRoom(26));
  const numW = $derived(textRoom(76));
  const nameW = $derived(Math.min(textRoom(120), tableW * 0.22));
  const tableStacked = $derived(lineCard(tableW - rankW - nameW - numW * 2 - 8 * 4 - 8) < ui.sizes.other - 0.5);
  const listStacked = $derived(lineCard(listW - rankW - numW * 2 - 8 * 3) < ui.sizes.other - 0.5);
</script>

<svelte:window onkeydown={onKey} />

{#snippet spinner(size = 28)}<span class="spin" style:width="{size}px" style:height="{size}px"></span>{/snippet}

{#snippet header(index: number, withClock: boolean, hint: string | null, o: HandOutcome | null, actions: boolean)}
  {#if room}
    <div class="hand-head">
      <div class="facts">
        <span class="title-large nowrap">{t('battle_hand_of', index + 1, room.settings.hands)}</span>
        {#if withClock && left !== null}
          <span class="clock title-medium" class:bad={left < 15000}><Icon name="timer" size={22} />{clock(left)}</span>
        {/if}
        {#if hint}
          <span class="body-large hint">{hint}</span>
        {:else}
          {#if withClock}
            {@const humans = room.players.filter((p) => !p.bot)}
            <span class="body-large dim nowrap">{room.phase === 'PLAYING'
              ? t('battle_set_count', humans.filter((p) => p.submitted).length, humans.length)
              : t('battle_ready_count', humans.filter((p) => p.ready).length, humans.length)}</span>
            {#if puzzle && room.hand > 0}
              {@const rank = standings(room).findIndex((p) => p.id === b.me) + 1}
              {#if rank > 0}<span class="body-large nowrap">{t('battle_your_rank', rank)}</span>{/if}
            {/if}
          {/if}
          {@render myResult(o)}
        {/if}
      </div>
      {#if actions}
        <div class="head-actions">
          {#if o && (!puzzle || o.plays.some((p) => p.player === b.me))}
            <button class="btn big" class:outlined={!analysisMode || puzzle} class:tonal={analysisMode && !puzzle} onclick={analyzeButton}>
              <Icon name="analytics" size={20} />{t('battle_analyze')}
            </button>
          {/if}
          {#if page === 'last'}
            <button class="btn filled big" onclick={() => b.toSummary()}>{t('battle_to_summary')}</button>
          {:else}
            <button class="btn filled big" disabled={me?.ready === true} onclick={() => b.ready()}>
              {ui.w < 600 ? t('battle_next_short') : t('battle_next')}
            </button>
          {/if}
        </div>
      {/if}
    </div>
  {/if}
{/snippet}

{#snippet myResult(o: HandOutcome | null)}
  {#if o?.score}
    {@const i = o.plays.findIndex((p) => p.player === r.me)}
    {#if i >= 0}
      {@const x = o.score.result[i]}
      <span class="headline-small nowrap" class:good={x > 0} class:bad={x < 0}>{t('battle_this_hand', signed(x))}</span>
    {/if}
  {/if}
{/snippet}

{#snippet standingsList(marks: boolean)}
  {#if room}
    <div class="standings">
      <h2 class="title-medium">{t('battle_standings')}</h2>
      {#each standings(room) as p, i (p.id)}
        <div class="stand" class:me={p.id === b.me}>
          <span class="title-medium bold">{i + 1}</span>
          <span class="who"><span class="body-large ellipsis">{p.name}</span>
            {#if notes(p)}<span class="body-small dim">{notes(p)}</span>{/if}</span>
          {#if marks}
            {@const done = p.submitted || (room.phase === 'REVIEW' && p.ready)}
            <span class="body-medium" class:good={done} class:dim={!done}>{t(done ? 'battle_done_mark' : 'battle_thinking')}</span>
          {/if}
          <span class="body-large nowrap">{totalText(p)}</span>
        </div>
      {/each}
    </div>
  {/if}
{/snippet}

{#snippet line(rank: string, name: string, note: string | null, rows: number[][] | null, hand: string, handCls: string, total: string, mine: boolean, onclick: (() => void) | null)}
  <svelte:element this={onclick ? 'button' : 'div'} class="line" class:me={mine} class:pick={!!onclick} role={onclick ? undefined : 'row'} {onclick}>
    <span class="cells">
      <span class="title-medium num" style:width="{rankW}px">{rank}</span>
      <span class="name" style:width={tableStacked ? null : `${nameW}px`} class:grow={tableStacked}>
        <span class="body-large ellipsis">{name}</span>
        {#if note}<span class="body-small dim">{note}</span>{/if}
      </span>
      {#if !tableStacked}
        <span class="grow">{#if rows}<ThreeRows {rows} categories={r.categoriesOf(rows)} max={ui.sizes.other} />{/if}</span>
      {/if}
      <span class="title-medium num {handCls}" style:width="{numW}px">{hand}</span>
      <span class="title-medium num" style:width="{numW}px">{total}</span>
    </span>
    {#if tableStacked && rows}<ThreeRows {rows} categories={r.categoriesOf(rows)} max={ui.sizes.other} />{/if}
  </svelte:element>
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
        notes={plays.map((p) => (p.timedOut ? timedOut() : null))} {totals}
        onplayer={onplayer ? (s) => onplayer(plays[s].player) : null} />
    </div>
  {:else if o && room}
    <div class="scroll result-table" bind:clientWidth={tableW}>
      {#if tableW > 0}
        {@render line('', '', null, null, t(puzzle ? 'battle_col_hand_loss' : 'battle_col_hand'), 'dim head', t(puzzle ? 'battle_col_total_loss' : 'battle_col_total'), false, null)}
        {#if o.best}{@render line('', t('battle_best_rows'), null, o.best, '', '', '', false, null)}{/if}
        {#each standings(room) as p (p.id)}
          {@const i = o.plays.findIndex((x) => x.player === p.id)}
          {#if i >= 0}
            {@render line(t('battle_rank', rankOf(room, p)), p.name, o.plays[i].timedOut ? timedOut() : null, o.plays[i].rows,
              handValue(o, i), handClass(o, i), puzzle ? fmtLoss(p.total) : signed(p.total), p.id === r.me,
              onplayer ? () => onplayer(p.id) : null)}
          {/if}
        {/each}
      {/if}
    </div>
  {:else}
    <div class="center">{@render spinner(40)}</div>
  {/if}
{/snippet}

{#if ui.desk}
<DeskBattle {app} {page} {reviewing} {left} {analysisMode} onanalyze={analyzeButton} />
{:else}
<div class="battle">
  {#if page === 'entry'}
    <TitleBar title={t('nav_battle')} />
    <div class="progress">{#if b.busy}<span class="bar"></span>{/if}</div>
    <div class="scroll">
      <div class="entry" class:wide={ui.w >= 700}>
        <div class="col">
          <label class="field">
            <span class="body-small dim">{t('battle_name')}</span>
            <input class="body-large" maxlength={NAME_LENGTH} placeholder={b.defaultName} bind:value={b.name} />
          </label>
          {#if editServer}
            <label class="field">
              <span class="body-small dim">{t('battle_server')}</span>
              <input class="body-large" placeholder={t('battle_server_hint')} bind:value={b.server} onblur={() => (b.server = b.server.trim())} />
            </label>
          {:else}
            <div class="server">
              <span class="body-medium dim grow">{t('battle_server_is', b.server)}</span>
              <button class="btn text" onclick={() => (editServer = true)}>{t('change')}</button>
            </div>
          {/if}
        </div>
        <div class="col">
          <button class="tile fill" disabled={b.busy} onclick={() => b.create('PUZZLE')}>
            <span class="headline-small">{t('battle_new_puzzle')}</span><span class="body-medium info">{t('battle_puzzle_info')}</span>
          </button>
          <button class="tile fill" disabled={b.busy} onclick={() => b.create('GAME')}>
            <span class="headline-small">{t('battle_new_game')}</span><span class="body-medium info">{t('battle_game_info')}</span>
          </button>
          <div class="tile join">
            <span class="headline-small">{t('battle_join_room')}</span>
            <span class="body-medium dim">{t('battle_join_room_info')}</span>
            <form class="pin-row" onsubmit={(e) => { e.preventDefault(); if (b.pin.length === 6) b.join(); }}>
              <label class="field pin">
                <span class="body-small dim">{t('battle_pin')}</span>
                <input class="headline-small" inputmode="numeric" autocomplete="off" maxlength={6} value={b.pin}
                  oninput={(e) => { const v = e.currentTarget.value.replace(/\D/g, '').slice(0, 6); e.currentTarget.value = v; b.pin = v; }} />
              </label>
              <button class="btn filled big" type="submit" disabled={b.busy || b.pin.length !== 6}>{t('battle_join')}</button>
            </form>
          </div>
        </div>
      </div>
    </div>

  {:else if page === 'lobby' && room}
    {@const s = room.settings}
    {@const host = room.host === b.me}
    <TitleBar title={modeName(s.mode)} onback={() => b.leave()}>
      <button class="btn outlined" onclick={() => b.leave()}>{t('battle_leave')}</button>
    </TitleBar>
    <div class="scroll">
      <div class="lobby" class:wide={ui.w >= 720}>
        <div class="col">
          <div class="code fill">
            <span class="body-large sub">{t('battle_room_number')}</span>
            <span class="pin-big">{room.pin.slice(0, 3)} {room.pin.slice(3)}</span>
            <span class="body-medium sub">{t('battle_room_number_info')}</span>
          </div>
          <h2 class="section">{t('battle_players', room.players.length)}</h2>
          {#each room.players as p (p.id)}
            <div class="player">
              <span class="avatar">{[...p.name][0] ?? '?'}</span>
              <span class="body-large ellipsis grow">{p.name}</span>
              <span class="body-medium dim">{notes(p)}</span>
            </div>
          {/each}
        </div>
        <div class="col">
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
        </div>
      </div>
    </div>
    <div class="bottom">
      {#if host}
        <button class="btn filled big start" onclick={() => b.startGame()}>{t('battle_start_mode', modeName(s.mode))}</button>
      {:else}
        <div class="waiting">{@render spinner()}<span class="body-large">{t('battle_wait_host')}</span></div>
      {/if}
    </div>

  {:else if page === 'playing' && room}
    {@render header(room.hand, true, null, null, false)}
    {#if b.deal}
      <Arranging vm={b.arranger} {wheel} busy={b.submitting} confirmLabel={t('battle_submit')} onconfirm={() => b.submit()} />
    {:else}
      <div class="center">{@render spinner(40)}</div>
    {/if}

  {:else if page === 'waiting' && room}
    {@render header(room.hand, true, null, null, false)}
    <div class="scroll">
      <div class="wait" class:wide={ui.w > ui.h}>
        <div class="felt mine">
          <div class="waiting">{@render spinner(24)}<span class="title-medium">{t('battle_waiting')}</span></div>
          {#each [0, 1, 2] as row (row)}
            <RowCards cards={b.arranger.rows[row]} category={b.arranger.rowsEval?.categories?.[row] ?? 0} width={Math.min(ui.sizes.play, 64)} />
          {/each}
        </div>
        {@render standingsList(true)}
      </div>
    </div>

  {:else if (page === 'review' || page === 'last') && room}
    {@const o = reviewing}
    {@render header(o?.index ?? room.hand, page !== 'last', analysisMode && !puzzle ? t('battle_tap_player') : null, o, true)}
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
      <div class="summary" bind:clientWidth={listW}>
        {#each standings(room) as p (p.id)}
          {@const place = rankOf(room, p)}
          <div class="final" class:me={p.id === r.me}>
            <span class:title-large={place === 1} class:title-medium={place !== 1}>{t('battle_place', place)}</span>
            <span class="body-large grow ellipsis" class:bold={p.id === r.me}>{p.name}</span>
            <span class="title-medium nowrap">{totalText(p)}</span>
          </div>
        {/each}
        <p class="body-medium dim">{[t('battle_hands_n', room.settings.hands), app.rulesName(values), t('battle_time') + ' ' + timeText(room.settings.timeLimit)].join('，')}</p>
        <div class="list-head">
          <span class="title-small grow">{t('battle_hand_list')}</span>
          <span class="label-large num" style:width="{numW}px">{t(puzzle ? 'battle_col_hand_loss' : 'battle_col_hand')}</span>
          <span class="label-large num" style:width="{numW}px">{t('battle_col_hand_rank')}</span>
        </div>
        {#if listW > 0}
          {#each r.outcomes as o (o.index)}
            {@const i = o.plays.findIndex((p) => p.player === r.me)}
            <button class="hand-line" disabled={i < 0} onclick={() => r.openDetail(o.index)}>
              <span class="cells">
                <span class="title-medium num" style:width="{rankW}px">{o.index + 1}</span>
                {#if i >= 0}
                  <span class="grow">{#if !listStacked}<ThreeRows rows={o.plays[i].rows} categories={r.categoriesOf(o.plays[i].rows)} max={ui.sizes.other} />{/if}</span>
                  <span class="num" style:width="{numW}px">
                    <span class="title-medium {handClass(o, i)}">{handValue(o, i)}</span>
                    {#if o.plays[i].timedOut}<span class="body-small dim">{timedOut()}</span>{/if}
                  </span>
                  {@const value = (j: number) => o.losses?.[j] ?? -(o.score?.result[j] ?? 0)}
                  <span class="title-medium num" style:width="{numW}px">{t('battle_hand_rank', 1 + o.plays.filter((_, j) => value(j) < value(i) - 1e-9).length, o.plays.length)}</span>
                {/if}
              </span>
              {#if listStacked && i >= 0}<ThreeRows rows={o.plays[i].rows} categories={r.categoriesOf(o.plays[i].rows)} max={ui.sizes.other} />{/if}
            </button>
          {/each}
        {/if}
      </div>
    </div>

  {:else if page === 'detail' && room}
    {@const o = r.outcomes.find((x) => x.index === r.detail) ?? null}
    {#if o}
      <TitleBar title={t('battle_hand_of', o.index + 1, room.settings.hands)} onback={() => r.closeDetail()}>{@render myResult(o)}</TitleBar>
      <p class="body-medium dim tap-note">{t('battle_tap_player')}</p>
      {@render handTable(o, (p) => r.openAnalysis(p))}
    {/if}

  {:else if page === 'analysis' && room}
    {@const o = r.outcomes.find((x) => x.index === r.detail)}
    {@const play = o?.plays.find((p) => p.player === r.analyzed)}
    {#if o && play}
      {@const name = room.players.find((p) => p.id === play.player)?.name ?? '?'}
      <TitleBar title={`${t('battle_hand_of', o.index + 1, room.settings.hands)} · ${name}`} onback={() => (r.fromReview ? r.closeDetail() : r.closeAnalysis())} />
      {#if r.detailAnalysis}
        <CandidateList a={r.detailAnalysis} mine={play.rows} tags={r.detailTags} mineLabel={play.player === r.me ? null : name} />
      {:else}
        <div class="center">{@render spinner(48)}<span class="body-large dim">{t('analyzing')}</span></div>
      {/if}
    {/if}
  {/if}
</div>
{/if}

{#if confirmLeave && room && b.inRoom}
  <Dialog title={t('battle_leave_title')} onclose={() => (confirmLeave = false)}>
    <p class="body-large" style="margin:0">{t(room.phase === 'LOBBY' ? 'battle_leave_lobby' : 'battle_leave_game')}</p>
    {#snippet actions()}
      <button class="btn text" onclick={() => (confirmLeave = false)}>{t('cancel')}</button>
      <button class="btn text" onclick={() => { confirmLeave = false; b.leave(); }}>{t('battle_leave')}</button>
    {/snippet}
  </Dialog>
{/if}

<style>
  .battle { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .scroll { flex: 1; min-height: 0; overflow: auto; }
  .center { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; padding: 24px; }
  .grow { flex: 1; min-width: 0; }
  .nowrap { white-space: nowrap; }
  .bold { font-weight: 700; }
  .spin { display: inline-block; flex: none; border-radius: 50%; border: 3px solid transparent; border-top-color: currentColor;
    color: var(--primary); animation: spin 0.9s linear infinite; box-sizing: border-box; }
  .felt .spin { color: var(--on-felt); }
  @keyframes spin { to { transform: rotate(360deg); } }
  .progress { height: 4px; flex: none; overflow: hidden; position: relative; }
  .bar { position: absolute; inset: 0 auto 0 0; width: 40%; background: var(--primary); animation: slide 1.1s ease-in-out infinite; }
  @keyframes slide { from { left: -40%; } to { left: 100%; } }

  /* The entry */
  .entry { display: flex; flex-direction: column; gap: 14px; padding: 4px 16px 16px; max-width: 640px; margin: 0 auto; }
  .entry.wide { flex-direction: row; gap: 20px; max-width: 1100px; }
  .entry.wide .col:first-child { flex: 0.95; }
  .entry.wide .col:last-child { flex: 1.25; }
  .col { display: flex; flex-direction: column; gap: 10px; min-width: 0; }
  .field { display: flex; flex-direction: column; gap: 2px; padding: 6px 14px 8px; border: 1px solid var(--outline); border-radius: 6px; }
  .field:focus-within { border: 2px solid var(--primary); padding: 5px 13px 7px; }
  .field input { border: none; outline: none; background: none; color: var(--on-surface); padding: 2px 0; font: inherit; width: 100%; min-width: 0; }
  .server { display: flex; align-items: center; gap: 8px; }
  .tile { display: flex; flex-direction: column; gap: 4px; text-align: left; padding: 12px 14px; border-radius: 16px; font: inherit; }
  .tile .headline-small { font-weight: 700; }
  button.tile { border: none; cursor: pointer; }
  button.tile:disabled { opacity: 0.6; cursor: default; }
  button.tile:not(:disabled):hover { filter: brightness(1.08); }
  .fill { background: var(--felt); color: var(--on-felt); }
  /* The minimal colors' felt is the page: a container, so that it still looks like a button. */
  :global(:root.minimal) .fill { background: var(--secondary-container); color: var(--on-secondary-container); }
  .tile .info { opacity: 0.88; }
  .join { border: 2px solid var(--outline-variant); gap: 8px; }
  .pin-row { display: flex; align-items: center; gap: 10px; }
  .pin { flex: 1; max-width: 220px; }
  .pin input { letter-spacing: 6px; }

  /* The lobby */
  .lobby { display: flex; flex-direction: column; padding: 0 16px 8px; max-width: 640px; margin: 0 auto; }
  .lobby.wide { flex-direction: row; gap: 24px; max-width: 1100px; }
  .lobby.wide .col { flex: 1; }
  .code { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 14px 18px; border-radius: 22px; }
  .code .sub { opacity: 0.8; }
  .pin-big { font-size: 2.125rem; line-height: 1.25; font-weight: 700; letter-spacing: 4px; }
  .section { font-size: 0.875rem; font-weight: 500; color: var(--primary); margin: 16px 0 4px; }
  .player { display: flex; align-items: center; gap: 12px; min-height: 56px; }
  .avatar { flex: none; width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
    background: var(--primary-container); color: var(--on-primary-container); font-weight: 700; }
  .setting { display: flex; align-items: center; gap: 12px; min-height: 56px; }
  .bottom { flex: none; padding: 12px 16px 16px; display: flex; justify-content: center; }
  .start { width: 100%; max-width: 640px; }
  .waiting { display: flex; align-items: center; gap: 12px; }

  /* A hand */
  .hand-head { display: flex; align-items: center; gap: 8px; padding: 4px 12px 4px 16px; min-height: 56px; flex: none; }
  .facts { flex: 1; min-width: 0; display: flex; flex-wrap: wrap; align-items: center; column-gap: 12px; row-gap: 2px; }
  .clock { display: inline-flex; align-items: center; gap: 4px; }
  .hint { color: var(--primary); }
  .head-actions { display: flex; gap: 8px; flex: none; }
  .head-actions .btn { padding: 0 14px; }
  .wait { display: flex; flex-direction: column; gap: 16px; padding: 0 12px 12px; }
  .wait.wide { flex-direction: row; align-items: flex-start; }
  .wait.wide > :global(*) { flex: 1; min-width: 0; }
  .mine { display: flex; flex-direction: column; gap: 8px; padding: 14px; border-radius: 14px; }
  .standings { display: flex; flex-direction: column; gap: 4px; }
  .standings h2 { margin: 0 0 4px; }
  .stand { display: flex; align-items: center; gap: 10px; min-height: 48px; padding: 0 10px; border-radius: 12px; }
  .stand.me, .line.me, .final.me { background: var(--secondary-container); }
  .who { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  .seat-table { flex: 1; min-height: 0; margin: 0 12px 10px 8px; padding: 8px; display: flex; flex-direction: column; }
  .seat-table > :global(.results) { flex: 1; min-height: 0; }
  .result-table { padding: 0 12px 12px; }
  .line { display: flex; flex-direction: column; gap: 4px; width: 100%; padding: 6px 4px; border-radius: 12px; border: none;
    background: transparent; color: inherit; font: inherit; text-align: left; box-sizing: border-box; }
  button.line { cursor: pointer; }
  button.line:hover { background: var(--surface-container); }
  .cells { display: flex; align-items: center; gap: 8px; width: 100%; }
  .name { display: flex; flex-direction: column; min-width: 0; flex: none; }
  .name.grow { flex: 1; }
  .num { flex: none; text-align: right; display: flex; flex-direction: column; align-items: flex-end; white-space: nowrap; }
  .tap-note { margin: 0; padding: 2px 16px; }

  /* The final standings */
  .summary { padding: 0 12px 16px; max-width: 1100px; margin: 0 auto; display: flex; flex-direction: column; gap: 4px; }
  .final { display: flex; align-items: center; gap: 12px; min-height: 52px; padding: 0 14px; border-radius: 14px; background: var(--surface-container); }
  .summary p { margin: 4px 0 8px; }
  .list-head { display: flex; align-items: center; gap: 8px; padding: 8px 0 4px; }
  .hand-line { display: flex; flex-direction: column; gap: 4px; padding: 8px 0; border: none; border-bottom: 1px solid var(--outline-variant);
    background: none; color: inherit; font: inherit; text-align: left; cursor: pointer; width: 100%; }
  .hand-line:hover:not(:disabled) { background: var(--surface-container); }
  .hand-line:disabled { cursor: default; }
  .star { color: var(--highlight); display: contents; }
</style>
