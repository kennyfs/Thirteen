<!--
  Setting a hand into three rows on the table. Wide: the rows one above another with the buttons at their right, the
  hand in a line under them; tall: the rows, the hand in a line or two under them, and the buttons in a bar under the
  table. The rows' cards and the hand's are one size, the largest the room allows up to the window's `play` size.
  `bare` (a computer's): the rows one above another in the middle and the hand under them, without the buttons, which
  the page's side panel has (Play.svelte), and the keys below.

  A mouse does what a finger does: a sweep with the button held picks (or unpicks) the cards it passes. Keys: 1 2 3 put the picked cards into a row (or pick the row for the next ones), ← →
  go along the hand and Space picks, S sorts, Z undoes, Delete takes all back, Esc unpicks, Enter plays.
-->
<script lang="ts">
  import type { Arranger } from '../lib/arranger.svelte';
  import { ROW_SIZES, rank, rowLabel, rowLayout, suit } from '../lib/cards';
  import { t, ta } from '../lib/i18n.svelte';
  import { nav } from '../lib/nav.svelte';
  import {
    arrangeFit, cardHeight, deskFit, BIG_BUTTON, CARD_GAP, DESK_LABEL, HAND_GAP, HAND_LINE_GAP, HAND_LINE_GAP_TIGHT, ROW_PAD,
    ROW_STACK_GAP, ROWS_GAP, SUIT_GAP,
  } from '../lib/sizing';
  import { signed, textRoom, ui } from '../lib/ui.svelte';
  import Card from './Card.svelte';
  import Dialog from './Dialog.svelte';
  import Icon from './Icon.svelte';
  import Toggle from './Toggle.svelte';

  interface Props {
    vm: Arranger; wheel: number; busy: boolean; confirmLabel: string; onconfirm: () => void; bare?: boolean;
    /** Help asked for (the ? key, or the button): the page shows it when it has the buttons. */
    help?: boolean;
  }
  let { vm, wheel, busy, confirmLabel, onconfirm, bare = false, help = $bindable(false) }: Props = $props();

  let areaW = $state(0);
  let areaH = $state(0);
  const wide = $derived(bare || areaW > areaH * 1.15);
  const fit = $derived(arrangeFit(areaW, areaH, wide, ui.u, ui.sizes.play));
  const card = $derived(bare ? Math.min(ui.sizes.play, deskFit(areaW, areaH)) : fit.card);
  // The labels beside the rows: sizes from the text's.
  const labelWidth = $derived(Math.max(72, textRoom(14) * 4.6));
  const headerHeight = $derived(Math.max(32, textRoom(13) * 2));
  const labelHeight = $derived(textRoom(24) + textRoom(16) + 2);
  const above = $derived(!wide && fit.above);

  const hand = $derived(vm.unplaced);
  const counts = $derived.by(() => {
    const m = new Map<number, number>();
    for (const c of hand) m.set(rank(c), (m.get(rank(c)) ?? 0) + 1);
    return m;
  });
  const SAME_RANK_CATEGORY = [0, 0, 2, 4, 8];

  // The hand's room under the rows, on the tall page.
  let feltH = $state(0);
  const handRoom = $derived.by(() => {
    const rowH = above ? headerHeight + cardHeight(card) : Math.max(cardHeight(card), labelHeight);
    return feltH - 16 - ((rowH + ROW_PAD * 2) * 3 + 8 * 2) - 8;
  });
  // Sorted by suit with the room for it: a line a suit, the lines closer together if that makes them fit.
  const suitLines = $derived.by(() => {
    if (!vm.sortBySuit || wide) return null;
    const groups = new Map<number, number[]>();
    for (const c of hand) groups.set(suit(c), [...(groups.get(suit(c)) ?? []), c]);
    const lines = [...groups.values()].flatMap((g) => chunk(g, fit.perLine));
    const fits = (gap: number) => cardHeight(card) * lines.length + gap * (lines.length - 1) + card * 0.22 <= handRoom;
    if (fits(HAND_LINE_GAP)) return { lines, gap: HAND_LINE_GAP };
    if (fits(HAND_LINE_GAP_TIGHT)) return { lines, gap: HAND_LINE_GAP_TIGHT };
    return null;
  });
  const handLines = $derived(suitLines?.lines ?? chunk(hand, wide ? 13 : fit.perLine));
  const single = $derived(wide || fit.perLine >= 13);

  function chunk<T>(xs: T[], n: number): T[][] {
    const out: T[][] = [];
    for (let i = 0; i < xs.length; i += Math.max(1, n)) out.push(xs.slice(i, i + Math.max(1, n)));
    return out;
  }

  function rowState(r: number) {
    const cards = vm.rows[r];
    const free = ROW_SIZES[r] - cards.length;
    const ev = vm.rowsEval;
    const picked = vm.selected.size;
    const target = (picked > 0 && free >= picked) || vm.armed === r;
    const dim = picked > free;
    const text = free === 0 && ev && ev.categories[r] > 0 ? rowLabel(ev.keys[r], ta('row_labels'), wheel) : `${cards.length}/${ROW_SIZES[r]}`;
    return { text, target, dim };
  }

  const readiness = $derived.by(() => {
    const ev = vm.rowsEval;
    if (hand.length > 0 || !ev) return { text: '', bad: false };
    if (ev.valid === true) {
      const sum = ev.royalties.reduce((a, b) => a + b, 0);
      return { text: sum > 0 ? t('ready_royalty', signed(sum).slice(1)) : t('ready'), bad: false };
    }
    if (ev.valid === false && ev.keys[0] <= ev.keys[1] && ev.keys[1] <= ev.keys[2]) return { text: t('fouled_strict'), bad: true };
    if (ev.valid === false) return { text: t('fouled'), bad: true };
    return { text: '', bad: false };
  });

  // ---- Swipes: over the hand they pick (or unpick, from a picked card) each card touched; over the rows they take
  // each card touched back to the hand. A tap still toggles one card; a long press on a row takes it all back. ----
  type Swipe = { kind: 'hand' | 'rows'; x: number; y: number; moved: boolean; on: boolean; touched: Set<number>; id: number };
  let swipe: Swipe | null = null;
  let suppressClick = false;

  function cardAt(x: number, y: number, kind: 'hand' | 'rows'): number | null {
    const el = document.elementFromPoint(x, y)?.closest('[data-card]') as HTMLElement | null;
    if (!el || !el.closest(kind === 'hand' ? '.tray' : '.rows')) return null;
    return Number(el.dataset.card);
  }

  function down(e: PointerEvent, kind: 'hand' | 'rows') {
    if (e.button !== 0) return;
    swipe = { kind, x: e.clientX, y: e.clientY, moved: false, on: true, touched: new Set(), id: e.pointerId };
  }

  function touch(x: number, y: number) {
    if (!swipe) return;
    const c = cardAt(x, y, swipe.kind);
    if (c === null || swipe.touched.has(c)) return;
    if (swipe.kind === 'hand') {
      if (swipe.touched.size === 0) swipe.on = !vm.selected.has(c);
      swipe.touched.add(c);
      vm.select([c], swipe.on);
    } else {
      swipe.touched.add(c);
      vm.unplace(c, swipe.touched.size === 1);
    }
  }

  function move(e: PointerEvent) {
    if (!swipe || e.pointerId !== swipe.id) return;
    if (!swipe.moved && Math.hypot(e.clientX - swipe.x, e.clientY - swipe.y) < 8) return;
    if (!swipe.moved) {
      swipe.moved = true;
      (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
      touch(swipe.x, swipe.y);
    }
    // Along the path in small steps, so a quick swipe misses none.
    const steps = Math.min(200, Math.max(1, Math.round(Math.hypot(e.clientX - swipe.x, e.clientY - swipe.y) / 4)));
    for (let k = 1; k <= steps; k++) touch(swipe.x + ((e.clientX - swipe.x) * k) / steps, swipe.y + ((e.clientY - swipe.y) * k) / steps);
    swipe.x = e.clientX;
    swipe.y = e.clientY;
  }

  function up(e: PointerEvent) {
    if (!swipe || e.pointerId !== swipe.id) return;
    if (swipe.moved) {
      suppressClick = true;
      setTimeout(() => (suppressClick = false), 0);
      if (swipe.kind === 'hand') vm.selectionDone();
    }
    swipe = null;
  }

  function clickCard(c: number) { if (!suppressClick) vm.toggle(c); }
  function clickPlaced(c: number) { if (!suppressClick) vm.unplace(c); }

  let pressTimer: ReturnType<typeof setTimeout> | null = null;
  let pressed = false;
  function rowDown(e: PointerEvent, r: number) {
    if ((e.target as HTMLElement).closest('[data-card]')) return;
    pressed = false;
    pressTimer = setTimeout(() => { pressed = true; vm.clearRow(r); }, 550);
  }
  function rowUp() { if (pressTimer) clearTimeout(pressTimer); pressTimer = null; }
  function rowClick(e: MouseEvent, r: number) {
    if (pressed || suppressClick || (e.target as HTMLElement).closest('[data-card], .takeback')) return;
    vm.tapRow(r);
  }
  function rowKey(e: KeyboardEvent, r: number) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); vm.tapRow(r); } }

  // ---- Keys ----
  function onKey(e: KeyboardEvent) {
    const el = e.target as HTMLElement | null;
    if (el && (el.closest('input, textarea, select, [contenteditable]') || document.querySelector('dialog[open]'))) return;
    if (e.altKey || ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() !== 'z')) return;
    const k = e.key;
    const done = () => e.preventDefault();
    if (k === '1' || k === '2' || k === '3') { vm.tapRow(Number(k) - 1); return done(); }
    if (k.toLowerCase() === 's') { vm.sortBySuit = !vm.sortBySuit; return done(); }
    if (k.toLowerCase() === 'z') { vm.undo(); return done(); }
    if (k === 'Delete' || k === 'Backspace') { vm.clearRows(); return done(); }
    if (k === 'Escape' && (vm.selected.size > 0 || vm.armed !== null)) { vm.selected = new Set(); vm.armed = null; return done(); }
    if (k === '?') { help = true; return done(); }
    if (k === 'Enter' && canConfirm) { onconfirm(); return done(); }
    if (k === 'ArrowLeft' || k === 'ArrowRight') {
      const cards = [...document.querySelectorAll<HTMLElement>('.tray [data-card]')];
      if (cards.length === 0) return;
      const i = cards.indexOf(document.activeElement as HTMLElement);
      const next = i < 0 ? (k === 'ArrowRight' ? 0 : cards.length - 1) : Math.max(0, Math.min(cards.length - 1, i + (k === 'ArrowRight' ? 1 : -1)));
      cards[next].focus();
      return done();
    }
  }

  const rowNames = $derived(ta('row_names'));
  const sortOptions = $derived<[boolean, string][]>([[false, t('sort_rank')], [true, t('sort_suit')]]);
  const canConfirm = $derived(!busy && vm.rowsEval?.valid === true);
</script>

{#snippet rowSlot(r: number)}
  {@const st = rowState(r)}
  {@const ev = vm.rowsEval}
  {@const category = vm.rows[r].length === ROW_SIZES[r] && ev ? ev.categories[r] : 0}
  <div
    class="slot-row" class:target={st.target} class:above class:can={vm.selected.size > 0 && st.target}
    role="button" tabindex="0" data-row={r}
    aria-label="{rowNames[r]} {st.text}" style:padding="{ROW_PAD}px"
    onclick={(e) => rowClick(e, r)} onkeydown={(e) => rowKey(e, r)}
    onpointerdown={(e) => rowDown(e, r)} onpointerup={rowUp} onpointerleave={rowUp} onpointercancel={rowUp}
  >
    {#if above}
      <div class="row-header" style:height="{headerHeight}px">
        <span class="title-medium bold">{rowNames[r]}</span>
        <span class="state body-small ellipsis" class:hl={st.target} class:dimmed={st.dim}>{st.text}</span>
        {#if vm.rows[r].length > 0}
          <button class="takeback" aria-label={t('take_back_row', rowNames[r])} onclick={() => vm.clearRow(r)}><Icon name="download" size={20} /></button>
        {/if}
      </div>
    {:else}
      <div class="row-label" style:width={bare ? `${DESK_LABEL}px` : wide ? `${labelWidth}px` : null} class:fill={!wide}>
        <span class="title-medium bold nowrap">{rowNames[r]}</span>
        <span class="state body-small" class:hl={st.target} class:dimmed={st.dim}>{st.text}</span>
      </div>
    {/if}
    <div class="row-cards" style:gap="{CARD_GAP}px" style:width={bare || (!wide && !above) ? `${card * 5 + CARD_GAP * 4}px` : null}>
      {#each rowLayout(vm.rows[r], category) as p (p.card)}
        <Card card={p.card} width={card} tint={p.tint} light={p.light} lift={false} onclick={() => clickPlaced(p.card)} />
      {/each}
      {#each Array(ROW_SIZES[r] - vm.rows[r].length) as _, i (i)}
        <div class="empty" style:width="{card}px" style:height="{card * 1.4}px" style:border-radius="{card * 0.14}px"></div>
      {/each}
    </div>
  </div>
{/snippet}

{#snippet rowsStack()}
  <div class="rows" role="group" style:gap="{above ? 8 : ROW_STACK_GAP}px"
    onpointerdown={(e) => down(e, 'rows')} onpointermove={move} onpointerup={up} onpointercancel={up}>
    {#each [0, 1, 2] as r (r)}{@render rowSlot(r)}{/each}
  </div>
{/snippet}

{#snippet tray()}
  <div class="tray" role="group" data-tour="hand" style:padding-top="{card * 0.22}px" style:gap="{suitLines?.gap ?? HAND_LINE_GAP}px" class:left={!!suitLines}
    onpointerdown={(e) => down(e, 'hand')} onpointermove={move} onpointerup={up} onpointercancel={up}>
    {#each handLines as line, li (li)}
      <div class="hand-line">
        {#each line as c, i (c)}
          {#if i > 0}<span style:width="{single && !suitLines && vm.sortBySuit && suit(line[i - 1]) !== suit(c) ? SUIT_GAP : HAND_GAP}px" class="gap"></span>{/if}
          <Card card={c} width={card} tint={SAME_RANK_CATEGORY[counts.get(rank(c)) ?? 0]} light selected={vm.selected.has(c)} onclick={() => clickCard(c)} />
        {/each}
      </div>
    {/each}
    {#if hand.length === 0}
      <div class="ready title-medium bold" class:bad={readiness.bad} aria-live="polite"
        style:height="{cardHeight(card) * (wide ? 1 : Math.ceil(13 / fit.perLine)) + HAND_LINE_GAP * ((wide ? 1 : Math.ceil(13 / fit.perLine)) - 1)}px">
        {readiness.text}
      </div>
    {/if}
  </div>
{/snippet}

<svelte:window onkeydown={onKey} />

<div class="area" bind:clientWidth={areaW} bind:clientHeight={areaH}>
  {#if areaW > 0}
    {#if bare}
      <div class="felt desk-felt">
        <div class="desk-rows">{@render rowsStack()}</div>
        {@render tray()}
      </div>
    {:else if wide}
      <div class="felt wide-felt" style:margin="0 12px 6px 8px" style:padding="6px">
        <div class="wide-top" style:gap="{ROWS_GAP}px">
          {@render rowsStack()}
          <div class="wide-buttons" style:gap="{ROW_STACK_GAP}px">
            <div class="wb"><Toggle options={sortOptions} selected={vm.sortBySuit} onselect={(v) => (vm.sortBySuit = v)} felt big height={44} /></div>
            <div class="wb tools">
              <button class="tool" disabled={!vm.canUndo} onclick={() => vm.undo()}><Icon name="undo" /><span>{t('undo')}</span></button>
              <button class="tool" disabled={!vm.canClear} onclick={() => vm.clearRows()}><Icon name="clearAll" /><span>{t('take_all')}</span></button>
              <button class="tool" onclick={() => (help = true)}><Icon name="help" /><span>{t('help')}</span></button>
            </div>
            <button class="wb confirm title-large" data-tour="confirm" disabled={!canConfirm} onclick={onconfirm}>{busy ? '…' : confirmLabel}</button>
          </div>
        </div>
        {@render tray()}
      </div>
    {:else}
      <div class="felt tall-felt" bind:clientHeight={feltH}>
        <div class="tall-rows">{@render rowsStack()}</div>
        <div style:padding-top="8px">{@render tray()}</div>
      </div>
      <div class="bar" style:height="{BIG_BUTTON + 16}px">
        <button class="btn outlined sort" aria-label={t(vm.sortBySuit ? 'sort_by_suit_on' : 'sort_by_rank_on')} onclick={() => (vm.sortBySuit = !vm.sortBySuit)}>
          <Icon name="swap" size={20} /><span>{t(vm.sortBySuit ? 'sort_suit' : 'sort_rank')}</span>
        </button>
        <button class="btn outlined icon" aria-label={t('undo')} disabled={!vm.canUndo} onclick={() => vm.undo()}><Icon name="undo" /></button>
        <button class="btn outlined icon" aria-label={t('take_all')} disabled={!vm.canClear} onclick={() => vm.clearRows()}><Icon name="clearAll" /></button>
        <button class="btn outlined icon" aria-label={t('help')} onclick={() => (help = true)}><Icon name="help" /></button>
        <button class="btn filled big go" data-tour="confirm" disabled={!canConfirm} onclick={onconfirm}>{busy ? '…' : confirmLabel}</button>
      </div>
    {/if}
  {/if}
</div>

{#if help && !bare}
  <Dialog title={t('arrange_help_title')} onclose={() => (help = false)}>
    <p class="body-large help-text">{t('arrange_help')}</p>
    {#snippet actions()}
      <button class="btn text" onclick={() => { help = false; nav.open('tutorial'); }}>{t('tut_title')}</button>
      <button class="btn text" onclick={() => (help = false)}>{t('ok')}</button>
    {/snippet}
  </Dialog>
{/if}

<style>
  .area { flex: 1; min-height: 0; display: flex; flex-direction: column; }
  .tall-felt { flex: 1; min-height: 0; margin: 0 8px; padding: 8px; display: flex; flex-direction: column; justify-content: space-between; }
  .tall-rows { min-height: 0; overflow: auto; }
  .wide-felt { flex: 1; min-height: 0; display: flex; flex-direction: column; justify-content: space-between; align-items: center; }
  .wide-top { display: flex; width: 100%; }
  .wide-buttons { flex: 1; display: flex; flex-direction: column; min-width: 0; }
  .wb { flex: 1 1 0; min-height: 0; display: flex; }
  .wb :global(.group) { flex: 1; }
  .tools { gap: 8px; }
  .tool { flex: 1 1 0; min-width: 0; display: flex; align-items: center; justify-content: center; gap: 6px; border-radius: 18px; cursor: pointer;
    background: transparent; color: var(--on-felt); border: 1px solid color-mix(in srgb, var(--on-felt) 50%, transparent); font-size: 1rem; font-weight: 500;
    overflow: hidden; padding: 4px 6px; }
  .tool:disabled { opacity: 0.4; cursor: default; }
  .tool span { white-space: nowrap; overflow: hidden; }
  .confirm { align-items: center; justify-content: center; border: none; border-radius: 18px; cursor: pointer; background: var(--action); color: var(--on-action); }
  .confirm:disabled { background: color-mix(in srgb, var(--on-felt) 14%, transparent); color: color-mix(in srgb, var(--on-felt) 55%, transparent); cursor: default; }
  .rows { display: flex; flex-direction: column; touch-action: none; }
  .wide-top .rows { flex: none; }
  .slot-row { display: flex; align-items: center; gap: 8px; border-radius: 10px; background: var(--slot); cursor: pointer; user-select: none; -webkit-user-select: none; }
  .slot-row.above { flex-direction: column; align-items: stretch; gap: 0; }
  .slot-row.target { background: var(--target); outline: 2.5px dashed var(--highlight); outline-offset: -2.5px; }
  .row-header { display: flex; align-items: center; gap: 8px; padding-left: 4px; }
  .row-label { display: flex; flex-direction: column; gap: 2px; min-width: 0; color: var(--on-felt); }
  .row-label.fill { flex: 1; }
  .state { color: var(--on-felt); font-weight: 500; overflow-wrap: anywhere; }
  .state.hl { color: var(--highlight); font-weight: 700; }
  .state.dimmed { color: var(--on-felt-dim); }
  .row-header .state { flex: 1; }
  .row-cards { display: flex; flex: none; }
  .empty { flex: none; border: 1.5px dashed color-mix(in srgb, var(--on-felt) 45%, transparent); }
  .takeback { width: 32px; height: 32px; border-radius: 16px; border: none; display: grid; place-items: center; cursor: pointer;
    background: color-mix(in srgb, var(--on-felt) 12%, transparent); color: var(--on-felt); }
  .tray { display: flex; flex-direction: column; align-items: center; touch-action: none; width: 100%; }
  .tray.left { align-items: flex-start; }
  .hand-line { display: flex; align-items: flex-end; }
  .gap { flex: none; display: block; }
  .ready { display: flex; align-items: center; justify-content: center; text-align: center; color: var(--on-felt); width: 100%; }
  .ready.bad { color: var(--felt-bad); }
  .bar { flex: none; display: flex; align-items: center; gap: 6px; padding: 8px 10px; }
  .sort { flex: none; height: 46px; padding: 0 10px; gap: 4px; }
  .go { flex: 1; min-width: 0; }
  .help-text { white-space: pre-line; margin: 0; }
  .desk-felt { flex: 1; min-height: 0; margin: 0 12px 12px; padding: 16px; display: flex; flex-direction: column; align-items: center;
    justify-content: center; gap: 28px; }
  .desk-rows { display: flex; justify-content: center; }
  @media (hover: hover) {
    .tray :global(button.card:not(.selected):hover) { transform: translateY(-4px); box-shadow: 0 3px 6px rgba(0, 0, 0, 0.25); }
    .rows :global(button.card:hover) { filter: brightness(0.94); }
    .slot-row.can:hover { outline-style: solid; }
  }
</style>
