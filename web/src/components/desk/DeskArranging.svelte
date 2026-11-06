<!--
  A computer's table for setting a hand: the rows one above another, the hand under them in one line or two (7 and 6),
  the cards as large as the table allows (deskArrangeFit). The buttons are the page's side panel's (DeskPlay, the
  battle's). Each row's label has its key, its state and its hand once full.

  The mouse: cards are dragged from the hand (the picked ones with the one dragged) into a row, from a row into another
  or back to the hand; a click picks a card (Shift: the run from the last one picked), a double click puts it into the
  first row with room; a click on a placed card takes it back, a right click on a row takes the row back. The keys: 1 2 3
  put the picked cards into a row (or pick the row for the next ones), ← → go along the hand and Space picks, S sorts,
  Z undoes, Delete takes all back, Esc unpicks, Enter plays, ? asks for help.
-->
<script lang="ts">
  import type { Arranger } from '../../lib/arranger.svelte';
  import { ROW_SIZES, rank, rowLabel, rowLayout, suit } from '../../lib/cards';
  import { t, ta } from '../../lib/i18n.svelte';
  import { cardHeight, deskArrangeFit, deskRowLabel, CARD_GAP, DESK_HAND_GAP, DESK_PAD, HAND_GAP, HAND_LINE_GAP, ROW_PAD, ROW_STACK_GAP, SUIT_GAP } from '../../lib/sizing';
  import { signed, ui } from '../../lib/ui.svelte';
  import Card from '../Card.svelte';
  import Kbd from './Kbd.svelte';

  interface Props { vm: Arranger; wheel: number; busy: boolean; onconfirm: () => void; help?: boolean }
  let { vm, wheel, busy, onconfirm, help = $bindable(false) }: Props = $props();

  let areaW = $state(0);
  let areaH = $state(0);
  const fit = $derived(deskArrangeFit(areaW, areaH, ui.u));
  const card = $derived(fit.card);
  const label = $derived(deskRowLabel(ui.u));
  const hand = $derived(vm.unplaced);
  const counts = $derived.by(() => {
    const m = new Map<number, number>();
    for (const c of hand) m.set(rank(c), (m.get(rank(c)) ?? 0) + 1);
    return m;
  });
  const SAME_RANK_CATEGORY = [0, 0, 2, 4, 8];
  // Two lines (7 and 6 of 13) stay about even as the hand empties: 8 cards are 4 and 4, not 7 and 1.
  const handLines = $derived(fit.perLine >= 13 ? [hand] : chunk(hand, Math.ceil(hand.length / Math.ceil(hand.length / fit.perLine)) || 1));
  const lines = $derived(Math.ceil(13 / fit.perLine));
  const canConfirm = $derived(!busy && vm.rowsEval?.valid === true);
  const rowNames = $derived(ta('row_names'));

  function chunk<T>(xs: T[], n: number): T[][] {
    const out: T[][] = [];
    for (let i = 0; i < xs.length; i += n) out.push(xs.slice(i, i + n));
    return out;
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

  // ---- Dragging: the cards follow the pointer and drop into the row under it (or the hand). `target`: a row, null for
  // the hand, undefined for nowhere. ----
  type Drag = { from: 'hand' | 'rows'; card: number; cards: number[] | null; x0: number; y0: number; x: number; y: number; id: number; target?: number | null };
  let drag = $state<Drag | null>(null);
  let suppressClick = false;

  function cardAt(e: PointerEvent, kind: 'hand' | 'rows'): number | null {
    const el = (e.target as HTMLElement).closest('[data-card]') as HTMLElement | null;
    if (!el || !el.closest(kind === 'hand' ? '.tray' : '.rows')) return null;
    return Number(el.dataset.card);
  }
  function dropTarget(x: number, y: number): number | null | undefined {
    const el = document.elementFromPoint(x, y);
    const row = el?.closest('[data-row]') as HTMLElement | null;
    if (row) return Number(row.dataset.row);
    return el?.closest('.tray') ? null : undefined;
  }
  /** Whether the dragged cards fit row `r`. */
  function fits(r: number, cards: number[]) { return vm.rows[r].filter((c) => !cards.includes(c)).length + cards.length <= ROW_SIZES[r]; }

  function down(e: PointerEvent, kind: 'hand' | 'rows') {
    if (e.button !== 0) return;
    const c = cardAt(e, kind);
    if (c !== null) drag = { from: kind, card: c, cards: null, x0: e.clientX, y0: e.clientY, x: e.clientX, y: e.clientY, id: e.pointerId };
  }
  function move(e: PointerEvent) {
    const d = drag;
    if (!d || e.pointerId !== d.id) return;
    if (!d.cards) {
      if (Math.hypot(e.clientX - d.x0, e.clientY - d.y0) < 6) return;
      d.cards = d.from === 'hand' && vm.selected.has(d.card) ? [d.card, ...[...vm.selected].filter((c) => c !== d.card)] : [d.card];
      (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    }
    d.x = e.clientX;
    d.y = e.clientY;
    d.target = dropTarget(e.clientX, e.clientY);
  }
  function up(e: PointerEvent) {
    const d = drag;
    if (!d || e.pointerId !== d.id) return;
    drag = null;
    if (!d.cards) return;
    suppressClick = true;
    setTimeout(() => (suppressClick = false), 0);
    if (typeof d.target === 'number') vm.move(d.cards, d.target);
    else if (d.target === null && d.from === 'rows') vm.move(d.cards, null);
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
    if (k === 'Enter' && canConfirm && !(el?.closest('button'))) { onconfirm(); return done(); }
    if (k === 'ArrowLeft' || k === 'ArrowRight' || k === 'ArrowUp' || k === 'ArrowDown') {
      const cards = [...document.querySelectorAll<HTMLElement>('.tray [data-card]')];
      if (cards.length === 0) return;
      const i = cards.indexOf(document.activeElement as HTMLElement);
      const step = k === 'ArrowRight' ? 1 : k === 'ArrowLeft' ? -1 : k === 'ArrowDown' ? fit.perLine : -fit.perLine;
      const next = i < 0 ? (step > 0 ? 0 : cards.length - 1) : Math.max(0, Math.min(cards.length - 1, i + step));
      cards[next].focus();
      return done();
    }
  }
</script>

<svelte:window onkeydown={onKey} />

<div class="area" bind:clientWidth={areaW} bind:clientHeight={areaH}>
  {#if areaW > 0}
    <div class="felt table" class:dropping={drag?.cards && drag.from === 'rows' && drag.target === null} style:padding="{DESK_PAD}px" style:gap="{DESK_HAND_GAP}px">
      <!-- svelte-ignore a11y_no_static_element_interactions -->
      <div class="rows" style:gap="{ROW_STACK_GAP}px" onpointerdown={(e) => down(e, 'rows')} onpointermove={move} onpointerup={up} onpointercancel={up}>
        {#each [0, 1, 2] as r (r)}
          {@const st = rowState(r)}
          {@const ev = vm.rowsEval}
          {@const category = vm.rows[r].length === ROW_SIZES[r] && ev ? ev.categories[r] : 0}
          {@const dragging = drag?.cards ?? null}
          <div class="slot-row" class:target={dragging ? drag?.target === r && fits(r, dragging) : st.target}
            class:refuse={!!dragging && drag?.target === r && !fits(r, dragging)} class:can={vm.selected.size > 0 && st.target}
            role="button" tabindex="0" data-row={r} aria-label="{rowNames[r]} {st.text}" style:padding="{ROW_PAD}px"
            onclick={(e) => rowClick(e, r)} onkeydown={(e) => rowKey(e, r)} oncontextmenu={(e) => rowMenu(e, r)}>
            <div class="row-label" style:width="{label}px">
              <span class="name"><Kbd k={String(r + 1)} /><span class="title-medium bold nowrap">{rowNames[r]}</span></span>
              <span class="state body-medium" class:hl={st.target} class:dimmed={st.dim} class:full={st.full}>{st.text}</span>
              {#if st.royalty > 0}<span class="royalty label-small">+{st.royalty}</span>{/if}
            </div>
            <div class="row-cards" style:gap="{CARD_GAP}px" style:width="{card * 5 + CARD_GAP * 4}px">
              {#each rowLayout(vm.rows[r], category) as p (p.card)}
                <Card card={p.card} width={card} tint={p.tint} light={p.light} lift={false} faded={!!drag?.cards?.includes(p.card)} onclick={() => clickPlaced(p.card)} />
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
        style:min-height="{cardHeight(card) * lines + HAND_LINE_GAP * (lines - 1) + card * 0.22}px"
        onpointerdown={(e) => down(e, 'hand')} onpointermove={move} onpointerup={up} onpointercancel={up}>
        {#each handLines as line, li (li)}
          <div class="hand-line">
            {#each line as c, i (c)}
              {#if i > 0}<span style:width="{fit.perLine >= 13 && vm.sortBySuit && suit(line[i - 1]) !== suit(c) ? SUIT_GAP : HAND_GAP}px" class="gap"></span>{/if}
              <!-- svelte-ignore a11y_no_static_element_interactions -->
              <span class="hold" ondblclick={() => doubleCard(c)} onclickcapture={(e) => (shift = e.shiftKey)}>
                <Card card={c} width={card} tint={SAME_RANK_CATEGORY[counts.get(rank(c)) ?? 0]} light selected={vm.selected.has(c)}
                  faded={!!drag?.cards?.includes(c)} onclick={() => clickCard(c)} />
              </span>
            {/each}
          </div>
        {/each}
        {#if hand.length === 0}
          <div class="ready headline-small" class:bad={readiness.bad} aria-live="polite">{readiness.text}</div>
        {/if}
      </div>
    </div>
  {/if}
</div>

{#if drag?.cards}
  <!-- The dragged cards, under the pointer. -->
  <div class="ghost" style:left="{drag.x - card / 2}px" style:top="{drag.y - card * 0.7}px">
    {#each drag.cards as c, i (c)}
      <div style:margin-left={i > 0 ? `${-card * 0.62}px` : null}><Card card={c} width={card} light /></div>
    {/each}
  </div>
{/if}

<style>
  .area { flex: 1; min-height: 0; min-width: 0; display: flex; flex-direction: column; }
  .table { flex: 1; min-height: 0; margin: 0 12px 12px; display: flex; flex-direction: column; align-items: center; justify-content: center; }
  .table.dropping .tray { outline: 2.5px dashed var(--highlight); outline-offset: 4px; border-radius: 10px; }
  .rows { display: flex; flex-direction: column; touch-action: none; }
  .slot-row { display: flex; align-items: center; gap: 12px; border-radius: 12px; background: var(--slot); cursor: pointer; user-select: none; -webkit-user-select: none; }
  .slot-row.target { background: var(--target); outline: 2.5px dashed var(--highlight); outline-offset: -2.5px; }
  .slot-row.refuse { outline: 2.5px dashed var(--felt-bad); outline-offset: -2.5px; cursor: not-allowed; }
  .row-label { display: flex; flex-direction: column; gap: 4px; min-width: 0; color: var(--on-felt); padding-left: 6px; }
  .name { display: flex; align-items: center; gap: 8px; }
  .state { color: var(--on-felt-dim); font-weight: 500; overflow-wrap: anywhere; }
  .state.full { color: var(--on-felt); font-weight: 700; }
  .state.hl { color: var(--highlight); font-weight: 700; }
  .state.dimmed { color: var(--on-felt-dim); opacity: 0.7; }
  .royalty { align-self: flex-start; padding: 1px 6px; border-radius: 6px; background: var(--highlight); color: #3b2a00; }
  .row-cards { display: flex; flex: none; }
  .empty { flex: none; border: 1.5px dashed color-mix(in srgb, var(--on-felt) 45%, transparent); }
  .tray { display: flex; flex-direction: column; align-items: center; touch-action: none; position: relative; align-self: stretch; }
  .hand-line { display: flex; align-items: flex-end; }
  .hold { display: contents; }
  .gap { flex: none; display: block; }
  .ready { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; text-align: center; color: var(--on-felt); }
  .ready.bad { color: var(--felt-bad); }
  .ghost { position: fixed; z-index: 50; pointer-events: none; display: flex; filter: drop-shadow(0 6px 10px rgba(0, 0, 0, 0.35)); }
  .tray :global(button.card:not(.selected):hover) { transform: translateY(-4px); box-shadow: 0 3px 6px rgba(0, 0, 0, 0.25); }
  .rows :global(button.card:hover) { filter: brightness(0.94); }
  .slot-row.can:hover { outline-style: solid; }
</style>
