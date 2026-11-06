<!--
  A computer's table for setting a hand: the rows one above another, the hand under them in one line (or, with a key a
  card in 7 + 6, in two as the keys), the cards as large as the table allows (deskArrangeFit). The buttons are the
  page's side panel's (DeskPlay, the battle's). Each row's label has its key, its state and its hand once full.

  The mouse does what a finger does on the phone's: a click picks a card, and a sweep with the button held picks (or,
  from a picked card, unpicks) each card it passes; a click on a row puts the picked cards into it (or picks the row
  for the next ones), a click on a placed card or a sweep over the rows takes cards back. Besides: Shift and a click
  picks the run from the last card picked, a double click puts a card into the first row with room, a right click on a
  row takes it all back. The keys are desk-keys.ts's, by the settings' choice.
-->
<script lang="ts">
  import type { Arranger } from '../../lib/arranger.svelte';
  import { ROW_SIZES, rank, rowLabel, rowLayout, sortHand, suit } from '../../lib/cards';
  import { keyMap, type DeskKeys } from '../../lib/desk-keys';
  import { t, ta } from '../../lib/i18n.svelte';
  import { cardHeight, deskArrangeFit, deskRowLabel, CARD_GAP, DESK_HAND_GAP, DESK_PAD, HAND_GAP, HAND_LINE_GAP, ROW_PAD, ROW_STACK_GAP, SUIT_GAP } from '../../lib/sizing';
  import { signed, textRoom, ui } from '../../lib/ui.svelte';
  import Card from '../Card.svelte';
  import Kbd from './Kbd.svelte';

  interface Props { vm: Arranger; wheel: number; busy: boolean; onconfirm: () => void; keys?: DeskKeys; help?: boolean }
  let { vm, wheel, busy, onconfirm, keys = 'pick', help = $bindable(false) }: Props = $props();

  const km = $derived(keyMap(keys));
  const below = $derived(km.cards ? textRoom(13) + 10 : 0);
  let areaW = $state(0);
  let areaH = $state(0);
  const fit = $derived(deskArrangeFit(areaW, areaH, ui.u, km.perLine, below));
  const card = $derived(fit.card);
  const label = $derived(deskRowLabel(ui.u));
  const hand = $derived(vm.unplaced);
  const counts = $derived.by(() => {
    const m = new Map<number, number>();
    for (const c of hand) m.set(rank(c), (m.get(rank(c)) ?? 0) + 1);
    return m;
  });
  const SAME_RANK_CATEGORY = [0, 0, 2, 4, 8];
  // With a key a card, the whole hand's places, the played cards' empty (null), so that each key stays on its card;
  // else the hand left, closed up.
  const order = $derived(sortHand(vm.hand, vm.sortBySuit));
  const slots = $derived.by((): (number | null)[] => {
    if (!km.cards) return hand;
    const left = new Set(hand);
    return order.map((c) => (left.has(c) ? c : null));
  });
  const lines = $derived(km.perLine >= 13 ? [slots] : [slots.slice(0, km.perLine), slots.slice(km.perLine)]);
  const lineCount = $derived(Math.ceil(13 / km.perLine));
  const canConfirm = $derived(!busy && vm.rowsEval?.valid === true);
  const rowNames = $derived(ta('row_names'));

  /** The suit gap before place `i` of a line: in one line sorted by suit, between suits. */
  function suitGap(line: (number | null)[], i: number): boolean {
    if (km.perLine < 13 || !vm.sortBySuit) return false;
    const seq = km.cards ? order : hand;
    return suit(seq[i - 1]) !== suit(seq[i]);
  }

  function rowState(r: number) {
    const cards = vm.rows[r];
    const free = ROW_SIZES[r] - cards.length;
    const ev = vm.rowsEval;
    const picked = vm.selected.size;
    const target = (picked > 0 && free >= picked) || vm.armed === r;
    const dim = picked > free;
    const full = free === 0 && !!ev && ev.categories[r] > 0;
    const text = full ? rowLabel(ev!.keys[r], ta('row_labels'), wheel) : `${cards.length}/${ROW_SIZES[r]}`;
    const royalty = full && ev!.valid === true ? ev!.royalties[r] : 0;
    return { text, target, dim, full, royalty };
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

  // ---- Sweeps (the phone's swipes): over the hand they pick (or unpick, from a picked card) each card passed; over
  // the rows they take each card passed back to the hand. A click without moving is a click. ----
  type Sweep = { kind: 'hand' | 'rows'; x: number; y: number; moved: boolean; on: boolean; touched: Set<number>; id: number };
  let sweep: Sweep | null = null;
  let suppressClick = false;

  function cardAt(x: number, y: number, kind: 'hand' | 'rows'): number | null {
    const el = document.elementFromPoint(x, y)?.closest('[data-card]') as HTMLElement | null;
    if (!el || !el.closest(kind === 'hand' ? '.tray' : '.rows')) return null;
    return Number(el.dataset.card);
  }
  function down(e: PointerEvent, kind: 'hand' | 'rows') {
    if (e.button !== 0 || e.shiftKey) return;
    sweep = { kind, x: e.clientX, y: e.clientY, moved: false, on: true, touched: new Set(), id: e.pointerId };
  }
  function touch(x: number, y: number) {
    if (!sweep) return;
    const c = cardAt(x, y, sweep.kind);
    if (c === null || sweep.touched.has(c)) return;
    if (sweep.kind === 'hand') {
      if (sweep.touched.size === 0) sweep.on = !vm.selected.has(c);
      sweep.touched.add(c);
      vm.select([c], sweep.on);
    } else {
      sweep.touched.add(c);
      vm.unplace(c, sweep.touched.size === 1);
    }
  }
  function move(e: PointerEvent) {
    if (!sweep || e.pointerId !== sweep.id) return;
    if (!sweep.moved && Math.hypot(e.clientX - sweep.x, e.clientY - sweep.y) < 6) return;
    if (!sweep.moved) {
      sweep.moved = true;
      (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
      touch(sweep.x, sweep.y);
    }
    // Along the path in small steps, so a quick sweep misses none.
    const steps = Math.min(200, Math.max(1, Math.round(Math.hypot(e.clientX - sweep.x, e.clientY - sweep.y) / 4)));
    for (let k = 1; k <= steps; k++) touch(sweep.x + ((e.clientX - sweep.x) * k) / steps, sweep.y + ((e.clientY - sweep.y) * k) / steps);
    sweep.x = e.clientX;
    sweep.y = e.clientY;
  }
  function up(e: PointerEvent) {
    if (!sweep || e.pointerId !== sweep.id) return;
    if (sweep.moved) {
      suppressClick = true;
      setTimeout(() => (suppressClick = false), 0);
      if (sweep.kind === 'hand') vm.selectionDone();
    }
    sweep = null;
  }

  // ---- Clicks ----
  let anchor: number | null = null;
  let shift = false;
  function clickCard(c: number) {
    if (suppressClick) return;
    if (shift && anchor !== null && hand.includes(anchor)) {
      const i = hand.indexOf(anchor), j = hand.indexOf(c);
      vm.select(hand.slice(Math.min(i, j), Math.max(i, j) + 1), true);
      vm.selectionDone();
      return;
    }
    anchor = c;
    vm.toggle(c);
  }
  /** A double click: the card (picked by the first click, unpicked by the second) into the first row with room. */
  function doubleCard(c: number) {
    const r = [0, 1, 2].find((i) => vm.rows[i].length < ROW_SIZES[i]);
    if (r !== undefined) vm.move([c], r);
  }
  function clickPlaced(c: number) { if (!suppressClick) vm.unplace(c); }
  function rowClick(e: MouseEvent, r: number) {
    if (suppressClick || (e.target as HTMLElement).closest('[data-card]')) return;
    vm.tapRow(r);
  }
  function rowMenu(e: MouseEvent, r: number) {
    e.preventDefault();
    const el = (e.target as HTMLElement).closest('[data-card]') as HTMLElement | null;
    if (el) vm.unplace(Number(el.dataset.card));
    else vm.clearRow(r);
  }
  function rowKey(e: KeyboardEvent, r: number) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); vm.tapRow(r); } }

  // ---- Keys, by where they are on the keyboard (e.code) ----
  function onKey(e: KeyboardEvent) {
    const el = e.target as HTMLElement | null;
    if (el && (el.closest('input, textarea, select, [contenteditable]') || document.querySelector('dialog[open]'))) return;
    if (e.altKey) return;
    const done = () => e.preventDefault();
    const code = e.code;
    if (e.ctrlKey || e.metaKey) {
      if (code === 'KeyZ') { vm.undo(); done(); }
      return;
    }
    const slot = km.cards ? km.cards.indexOf(code) : -1;
    if (slot >= 0) {
      const c = slots[slot];
      if (c !== null && c !== undefined) vm.toggle(c);
      return done();
    }
    const row = km.rows.indexOf(code);
    if (row >= 0) { vm.tapRow(row); return done(); }
    if (code === km.sort) { vm.sortBySuit = !vm.sortBySuit; return done(); }
    if (km.undo.includes(code)) { vm.undo(); return done(); }
    if (code === 'Delete' || (!km.cards && code === 'Backspace')) { vm.clearRows(); return done(); }
    if (e.key === 'Escape' && (vm.selected.size > 0 || vm.armed !== null)) { vm.selected = new Set(); vm.armed = null; return done(); }
    if (e.key === '?') { help = true; return done(); }
    if (e.key === 'Enter' && canConfirm && !(el?.closest('button'))) { onconfirm(); return done(); }
    if (!km.cards && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
      const cards = [...document.querySelectorAll<HTMLElement>('.tray [data-card]')];
      if (cards.length === 0) return;
      const i = cards.indexOf(document.activeElement as HTMLElement);
      const step = e.key === 'ArrowRight' ? 1 : -1;
      const next = i < 0 ? (step > 0 ? 0 : cards.length - 1) : Math.max(0, Math.min(cards.length - 1, i + step));
      cards[next].focus();
      return done();
    }
  }
</script>

<svelte:window onkeydown={onKey} />

<div class="area" bind:clientWidth={areaW} bind:clientHeight={areaH}>
  {#if areaW > 0}
    <div class="felt table" style:padding="{DESK_PAD}px" style:gap="{DESK_HAND_GAP}px">
      <!-- svelte-ignore a11y_no_static_element_interactions -->
      <div class="rows" style:gap="{ROW_STACK_GAP}px" onpointerdown={(e) => down(e, 'rows')} onpointermove={move} onpointerup={up} onpointercancel={up}>
        {#each [0, 1, 2] as r (r)}
          {@const st = rowState(r)}
          {@const ev = vm.rowsEval}
          {@const category = vm.rows[r].length === ROW_SIZES[r] && ev ? ev.categories[r] : 0}
          <div class="slot-row" class:target={st.target} class:can={vm.selected.size > 0 && st.target}
            role="button" tabindex="0" data-row={r} aria-label="{rowNames[r]} {st.text}" style:padding="{ROW_PAD}px"
            onclick={(e) => rowClick(e, r)} onkeydown={(e) => rowKey(e, r)} oncontextmenu={(e) => rowMenu(e, r)}>
            <div class="row-label" style:width="{label}px">
              <span class="name"><Kbd k={km.rowLabels[r]} /><span class="title-medium bold nowrap">{rowNames[r]}</span></span>
              <span class="state body-medium" class:hl={st.target} class:dimmed={st.dim} class:full={st.full}>{st.text}</span>
              {#if st.royalty > 0}<span class="royalty label-small">+{st.royalty}</span>{/if}
            </div>
            <div class="row-cards" style:gap="{CARD_GAP}px" style:width="{card * 5 + CARD_GAP * 4}px">
              {#each rowLayout(vm.rows[r], category) as p (p.card)}
                <Card card={p.card} width={card} tint={p.tint} light={p.light} lift={false} onclick={() => clickPlaced(p.card)} />
              {/each}
              {#each Array(ROW_SIZES[r] - vm.rows[r].length) as _, i (i)}
                <div class="empty" style:width="{card}px" style:height="{card * 1.4}px" style:border-radius="{card * 0.14}px"></div>
              {/each}
            </div>
          </div>
        {/each}
      </div>
      <!-- svelte-ignore a11y_no_static_element_interactions -->
      <div class="tray" style:padding-top="{card * 0.22}px" style:gap="{HAND_LINE_GAP}px"
        style:min-height="{(cardHeight(card) + below) * lineCount + HAND_LINE_GAP * (lineCount - 1) + card * 0.22}px"
        onpointerdown={(e) => down(e, 'hand')} onpointermove={move} onpointerup={up} onpointercancel={up}>
        {#if hand.length > 0}
          {#each lines as line, li (li)}
            <div class="hand-line">
              {#each line as c, i (km.cards ? `s${li * km.perLine + i}` : c)}
                {@const keyLabel = km.cardLabels?.[li * km.perLine + i]}
                {#if i > 0}<span style:width="{suitGap(line, li * km.perLine + i) ? SUIT_GAP : HAND_GAP}px" class="gap"></span>{/if}
                <div class="place" class:keyed={!!km.cards}>
                  {#if c !== null}
                    <!-- svelte-ignore a11y_no_static_element_interactions -->
                    <span class="hold" ondblclick={() => doubleCard(c)} onclickcapture={(e) => (shift = e.shiftKey)}>
                      <Card card={c} width={card} tint={SAME_RANK_CATEGORY[counts.get(rank(c)) ?? 0]} light selected={vm.selected.has(c)} onclick={() => clickCard(c)} />
                    </span>
                  {:else}
                    <div class="gone" style:width="{card}px" style:height="{card * 1.4}px" style:border-radius="{card * 0.14}px"></div>
                  {/if}
                  {#if keyLabel}<span class="cardkey" class:off={c === null} class:on={c !== null && vm.selected.has(c)} style:height="{below}px"><Kbd k={keyLabel} /></span>{/if}
                </div>
              {/each}
            </div>
          {/each}
        {:else}
          <div class="ready headline-small" class:bad={readiness.bad} aria-live="polite">{readiness.text}</div>
        {/if}
      </div>
    </div>
  {/if}
</div>

<style>
  .area { flex: 1; min-height: 0; min-width: 0; display: flex; flex-direction: column; }
  .table { flex: 1; min-height: 0; margin: 0 12px 12px; display: flex; flex-direction: column; align-items: center; justify-content: center; }
  .rows { display: flex; flex-direction: column; touch-action: none; }
  .slot-row { display: flex; align-items: center; gap: 12px; border-radius: 12px; background: var(--slot); cursor: pointer; user-select: none; -webkit-user-select: none; }
  .slot-row.target { background: var(--target); outline: 2.5px dashed var(--highlight); outline-offset: -2.5px; }
  .row-label { display: flex; flex-direction: column; gap: 4px; min-width: 0; color: var(--on-felt); padding-left: 6px; }
  .name { display: flex; align-items: center; gap: 8px; }
  .state { color: var(--on-felt-dim); font-weight: 500; overflow-wrap: anywhere; }
  .state.full { color: var(--on-felt); font-weight: 700; }
  .state.hl { color: var(--highlight); font-weight: 700; }
  .state.dimmed { color: var(--on-felt-dim); opacity: 0.7; }
  .royalty { align-self: flex-start; padding: 1px 6px; border-radius: 6px; background: var(--highlight); color: #3b2a00; }
  .row-cards { display: flex; flex: none; }
  .empty { flex: none; border: 1.5px dashed color-mix(in srgb, var(--on-felt) 45%, transparent); }
  .tray { display: flex; flex-direction: column; align-items: center; touch-action: none; position: relative; align-self: stretch; user-select: none; -webkit-user-select: none; }
  .hand-line { display: flex; align-items: flex-end; }
  .place { display: flex; flex-direction: column; align-items: center; }
  .hold { display: contents; }
  .gone { flex: none; border: 1.5px dashed color-mix(in srgb, var(--on-felt) 22%, transparent); }
  .cardkey { display: flex; align-items: flex-end; justify-content: center; color: var(--on-felt); }
  .cardkey :global(kbd) { font-size: 0.8rem; padding: 3px 6px; }
  .cardkey.on { color: var(--highlight); }
  .cardkey.off { opacity: 0.3; }
  .gap { flex: none; display: block; }
  .ready { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; text-align: center; color: var(--on-felt); }
  .ready.bad { color: var(--felt-bad); }
  .tray :global(button.card:not(.selected):hover) { transform: translateY(-4px); box-shadow: 0 3px 6px rgba(0, 0, 0, 0.25); }
  .rows :global(button.card:hover) { filter: brightness(0.94); }
  .slot-row.can:hover { outline-style: solid; }
</style>
