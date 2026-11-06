<!--
  The candidates, best Q first, with the player's arrangement (when known) pinned on top. Each shows its cards; opened,
  its rows, Q and the chances, colored where they differ notably from the best one's. On a wide window the list is on
  the left and the opened one on the right. `tags` names others who played a candidate, by its index (a battle's other
  players); `mineLabel` pins `mine` as someone else's (their name), without the remarks on it.
-->
<script lang="ts">
  import { rowLabel, sameArrangement, type Rows } from '../lib/cards';
  import { remarks as remarksOf, shapeOf } from '../lib/commentary';
  import type { Analysis, Candidate } from '../lib/engine';
  import { t, ta } from '../lib/i18n.svelte';
  import { remarkText } from '../lib/remarks';
  import { notableQGap, ROYALTIES } from '../lib/rules';
  import { LINE_GAP, rowWidth } from '../lib/sizing';
  import { fmtQ, textRoom, ui } from '../lib/ui.svelte';
  import RowCards from './RowCards.svelte';
  import ThreeRows from './ThreeRows.svelte';

  interface Props { a: Analysis; mine: Rows | null; legacyNote?: string | null; tags?: Record<number, string>; mineLabel?: string | null }
  let { a, mine, legacyNote = null, tags = {}, mineLabel = null }: Props = $props();

  // The chances shown (prob_names), without the dealer (4): the page plays with c = 0.
  const SHOWN = [0, 1, 2, 3, 5, 6, 7];
  const GOOD_WHEN_HIGH = [true, false, true, false, false, true, true, true];
  const NOTABLE_GAP = [0.03, 0.03, 0.005, 0.005, 0.03, 0.1, 0.1, 0.1];
  const ROYALTY_LOST = 11;
  const PINNED = -1;

  const mineAt = $derived.by(() => {
    if (!mine) return null;
    const i = a.cands.findIndex((c) => sameArrangement(c.arrangement, mine));
    return i >= 0 ? i : null;
  });
  const pinned = $derived(mineAt !== null && mineAt !== 0);
  const ranks = $derived.by(() => { let n = 0; return a.cands.map((c) => (c.extra ? n : ++n)); });
  const wheel = $derived(a.rules.wheel ?? 0);
  const rem = $derived(remarksOf(a, mine).filter((r) => mineLabel === null || (r.kind !== 'same' && r.kind !== 'mistake')));
  const texts = $derived(a.cands.map((cd) => cd.info.keys.map((k) => rowLabel(k, ta('row_labels'), wheel))));
  let showAll = $state(false);
  const shown = $derived(showAll ? a.cands.map((_, i) => i) : a.cands.map((_, i) => i).slice(0, 30));
  const qGap = $derived(notableQGap(a.rules));
  const lostShown = $derived(a.rules.on_win === 1 ? ROYALTIES.map((_, i) => i).filter((i) => (a.rules[ROYALTIES[i]] ?? 0) > 0) : []);

  let w = $state(0);
  let h = $state(0);
  // Two panes on a wide window when the side pane's height takes its details.
  const paneWidth = $derived(w * 0.34 - 20);
  const tileLines = $derived([2, 2, 3].reduce((s, n) => s + ((paneWidth - 4 * (n - 1)) / n >= textRoom(98) ? 0 : 1), 0));
  const paneHeight = $derived(16 + textRoom(24) + 4 + (textRoom(18) + textRoom(24) + 10) * 3 + textRoom(16) * tileLines + (textRoom(18) + textRoom(24) + 10) * Math.ceil(lostShown.length / 2));
  const wide = $derived((w >= 760 || (w > h && w >= 600)) && paneHeight <= h);
  let openSet = $state<Set<number>>(new Set());
  let current = $state(0);
  let lastA: Analysis | null = null;
  $effect.pre(() => {
    if (a !== lastA) {
      lastA = a;
      openSet = new Set(pinned ? [PINNED, 0] : [0]);
      current = pinned ? PINNED : 0;
      showAll = false;
    }
  });
  const index = (key: number) => (key === PINNED ? mineAt! : key);
  function toggle(key: number) {
    if (wide) { current = key; return; }
    const s = new Set(openSet);
    if (s.has(key)) s.delete(key); else s.add(key);
    openSet = s;
  }

  function badge(i: number, pin: boolean): { label: string; strong: boolean } {
    const yours = mineLabel ?? t('yours');
    if (pin) return { label: a.cands[i].extra ? yours : t('yours_rank', yours, ranks[i]), strong: true };
    if (i === 0 && mineAt === 0) return { label: `${t('best')} · ${yours}`, strong: true };
    if (a.cands[i].extra) return { label: '−', strong: false };
    if (i === 0) return { label: t('best'), strong: true };
    return { label: String(ranks[i]), strong: false };
  }

  function probLines(width: number, pane: boolean): number[][] {
    const tile = textRoom(62);
    const fit = Math.floor((width + 4) / (tile + 4));
    const three = (width - 8) / 3 >= tile * 0.85;
    if (pane) return three ? [[0, 1], [2, 3], [5, 6, 7]] : [[0, 1], [2, 3], [5, 6], [7]];
    if (fit >= 7) return [SHOWN];
    if (fit >= 4) return [[0, 1, 2, 3], [5, 6, 7]];
    return three ? [[0, 1], [2, 3], [5, 6, 7]] : [[0, 1], [2, 3], [5, 6], [7]];
  }
  function tone(k: number, p: number[], best: number[] | null): { tone: number; d: number } {
    const d = best ? p[k] - best[k] : 0;
    if (Math.abs(d) < NOTABLE_GAP[k]) return { tone: 0, d };
    return { tone: d > 0 === GOOD_WHEN_HIGH[k] ? 1 : -1, d };
  }
  const pct = (x: number) => (100 * x).toFixed(1) + '%';
  const diff = (d: number) => (d >= 0 ? '+' : '−') + Math.abs(100 * d).toFixed(1);
  const shapeName = (cd: Candidate) => { const s = shapeOf(cd.arrangement, cd.info.categories, wheel); return s === null ? null : ta('shape_names')[s]; };
  const mineRemark = $derived(rem.find((r) => r.kind === 'same' || r.kind === 'mistake') ?? null);
  const restRemarks = $derived(rem.filter((r) => r !== mineRemark).map(remarkText).join(t('remark_sep')));
  const names = $derived(ta('prob_names'));
</script>

{#snippet probGrid(cd: Candidate, best: Candidate | null, pane: boolean, width: number)}
  {@const p = cd.probs!}
  <div class="grid">
    {#each probLines(width, pane) as line, li (li)}
      {@const inline = (width - 4 * (line.length - 1)) / line.length >= textRoom(98)}
      <div class="gline">
        {#each line as k (k)}
          {@const x = tone(k, p, best?.probs ?? null)}
          <div class="tile">
            <span class="body-small dim ellipsis">{names[k]}</span>
            <span class="fig" class:inline>
              <span class="body-large nowrap" class:good={x.tone === 1} class:bad={x.tone === -1} class:bold={x.tone !== 0}>{pct(p[k])}</span>
              {#if x.tone !== 0}<span class="label-small nowrap" class:good={x.tone === 1} class:bad={x.tone === -1}>{diff(x.d)}</span>{/if}
            </span>
          </div>
        {/each}
      </div>
    {/each}
    {#each Array(Math.ceil(lostShown.length / 2)) as _, li (li)}
      <div class="gline">
        {#each lostShown.slice(li * 2, li * 2 + 2) as k (k)}
          <div class="tile"><span class="body-small dim ellipsis">{names[ROYALTY_LOST + k]}</span><span class="body-large">{pct(p[ROYALTY_LOST + k])}</span></div>
        {/each}
        {#if lostShown.slice(li * 2, li * 2 + 2).length === 1}<div class="tile blank"></div>{/if}
      </div>
    {/each}
  </div>
{/snippet}

{#snippet detail(i: number, pane: boolean, width: number)}
  {@const cd = a.cands[i]}
  {@const best = i === 0 ? null : a.cands[0]}
  <div class="detail">
    {#if pane}<div class="body-medium dim">{texts[i].join(' · ')}</div>{/if}
    {#if cd.q !== null}
      <div class="expected">
        <span class="body-medium">{t('expected')}</span>
        <span class="title-medium bold" class:good={cd.q >= 0} class:bad={cd.q < 0}>{fmtQ(cd.q)}</span>
        {#if best?.q != null && best.q > cd.q}<span class="body-medium bad">({fmtQ(cd.q - best.q)})</span>{/if}
      </div>
    {/if}
    {#if cd.probs}{@render probGrid(cd, best, pane, width)}{/if}
  </div>
{/snippet}

{#snippet item(i: number, pin: boolean)}
  {@const cd = a.cands[i]}
  {@const b = badge(i, pin)}
  {@const open = wide ? current === (pin ? PINNED : i) : openSet.has(pin ? PINNED : i)}
  {@const showDetail = open && !wide}
  {@const shape = shapeName(cd)}
  {@const best = i === 0 ? null : a.cands[0]}
  <div class="cand" class:open class:mine={i === mineAt} class:tagged={!!tags[i]} role="button" tabindex="0"
    onclick={() => toggle(pin ? PINNED : i)} onkeydown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(pin ? PINNED : i); } }}>
    <div class="cand-head">
      <span class="badge" class:strong={b.strong}>{b.label}</span>
      {#if tags[i]}<span class="tag body-small ellipsis">{tags[i]}</span>{/if}
      <span class="middle body-medium ellipsis" class:shape={shape !== null}>{showDetail ? shape ?? '' : shape ? `${shape} · ${texts[i].join(' · ')}` : texts[i].join(' · ')}</span>
      {#if cd.q !== null}
        {#if !showDetail && best?.q != null && best.q - cd.q >= qGap}<span class="body-medium bad nowrap">({fmtQ(cd.q - best.q)})</span>{/if}
        <span class="title-medium bold nowrap" class:good={cd.q >= 0} class:bad={cd.q < 0}>{fmtQ(cd.q)}</span>
      {/if}
    </div>
    {#if showDetail}
      {@const maxLeft = Math.max(w - 28 - textRoom(132) - 8, (w - 28) * 0.34)}
      {@const card = Math.min((Math.min((w - 28) * 0.44, maxLeft) - LINE_GAP * 4) / 5, ui.sizes.play)}
      {@const left = rowWidth(5, card, LINE_GAP)}
      <div class="opened">
        <div class="left" style:width="{left}px">
          {#each [0, 1, 2] as r (r)}
            <div class="body-small dim ellipsis">{texts[i][r]}</div>
            <RowCards cards={cd.arrangement[r]} category={cd.info.categories[r]} width={card} gap={LINE_GAP} />
          {/each}
        </div>
        <div class="right">{@render detail(i, false, w - 28 - left - 8)}</div>
      </div>
    {:else}
      <ThreeRows rows={cd.arrangement} categories={cd.info.categories} max={ui.sizes.other} />
    {/if}
  </div>
{/snippet}

<div class="cl" bind:clientWidth={w} bind:clientHeight={h}>
  {#if w > 0}
    <div class="list" style:width={wide ? '66%' : '100%'}>
      <div class="top">
        {#if a.dragon}<div class="body-large good">{t('dragon')}</div>{/if}
        {#if legacyNote}<div class="body-medium bad">{legacyNote}</div>{/if}
        {#if rem.length}
          <div class="remarks">
            <div class="title-small">{t('remarks_title')}</div>
            {#if mineRemark?.kind === 'mistake'}<div class="mistake body-medium bold">{remarkText(mineRemark)}</div>
            {:else if mineRemark}<div class="body-medium bold">{remarkText(mineRemark)}</div>{/if}
            {#if restRemarks}<div class="body-medium">{restRemarks}</div>{/if}
          </div>
        {/if}
      </div>
      {#if pinned}{@render item(mineAt!, true)}<hr />{/if}
      {#each shown as i (i)}{@render item(i, false)}{/each}
      {#if !showAll && a.cands.length > 30}
        <button class="btn text more" onclick={() => (showAll = true)}>{t('show_all', a.cands.length)}</button>
      {/if}
    </div>
    {#if wide}
      <div class="pane">{@render detail(index(current), true, w * 0.34 - 20)}</div>
    {/if}
  {/if}
</div>

<style>
  .cl { flex: 1; min-height: 0; display: flex; }
  .list { height: 100%; overflow: auto; padding-bottom: 16px; }
  .pane { flex: 1; height: 100%; overflow: auto; background: var(--surface-container-low); padding: 8px 10px; }
  .top { padding: 4px 12px; display: flex; flex-direction: column; gap: 6px; }
  .remarks { background: var(--secondary-container); color: var(--on-secondary-container); border-radius: 14px; padding: 8px 12px; display: flex; flex-direction: column; gap: 3px; }
  .mistake { background: var(--error-container); color: var(--on-error-container); border-radius: 8px; padding: 3px 8px; }
  .cand { margin: 2px 6px; padding: 6px 8px; border-radius: 12px; cursor: pointer; display: flex; flex-direction: column; gap: 4px; }
  .cand.mine { background: var(--surface-container-high); }
  .cand.open { background: var(--secondary-container); }
  .cand-head { display: flex; align-items: center; gap: 6px; min-width: 0; }
  .badge { background: var(--surface-container-highest); color: var(--on-surface); }
  .badge.strong { background: var(--primary); color: var(--on-primary); }
  .tag { flex: 0 1 auto; max-width: 40%; padding: 1px 6px; border-radius: 6px; background: var(--tertiary-container); color: var(--on-tertiary-container); }
  .cand.tagged:not(.mine) { background: color-mix(in srgb, var(--tertiary-container) 45%, transparent); }
  .middle { flex: 1; color: var(--on-surface-variant); }
  .middle.shape { color: var(--primary); font-weight: 500; }
  .opened { display: flex; gap: 8px; }
  .left { display: flex; flex-direction: column; gap: 2px; flex: none; }
  .right { flex: 1; min-width: 0; display: flex; }
  .right > :global(.detail) { flex: 1; }
  .detail { display: flex; flex-direction: column; gap: 4px; }
  .expected { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
  .grid { display: flex; flex-direction: column; gap: 4px; flex: 1; }
  .gline { display: flex; gap: 4px; flex: 1; }
  /* From the top: a line's tiles are one height, and their names and figures line up whichever have a difference. */
  .tile { flex: 1 1 0; min-width: 0; background: var(--surface-container-highest); border-radius: 8px; padding: 3px 6px;
    display: flex; flex-direction: column; justify-content: flex-start; }
  .tile.blank { background: none; }
  .fig { display: flex; flex-direction: column; }
  .fig.inline { flex-direction: row; align-items: baseline; gap: 3px; }
  hr { border: none; border-top: 1px solid var(--outline-variant); margin: 2px 12px; }
  .more { margin: 8px; }
</style>
