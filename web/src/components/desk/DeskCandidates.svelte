<!--
  A computer's page of candidates: the remarks over all; the list at the left (best Q first, the player's pinned on top
  when it is not the best); at the right the one chosen (or the one under the mouse) in large cards beside the one it is
  compared with (the player's, else the best), the cards that moved framed, and their figures in a table. ↑ ↓ choose.
  The props are CandidateList's: `tags` names others who played a candidate (a battle's players), `mineLabel` pins
  `mine` as someone else's, without the remarks on it.
-->
<script lang="ts">
  import { rowLabel, type Rows } from '../../lib/cards';
  import { remarks as remarksOf, shapeOf } from '../../lib/commentary';
  import { mineIndex, moved, ranks as ranksOf } from '../../lib/compare';
  import type { Analysis, Candidate } from '../../lib/engine';
  import { t, ta } from '../../lib/i18n.svelte';
  import { remarkText } from '../../lib/remarks';
  import { notableQGap } from '../../lib/rules';
  import { CARD_GAP } from '../../lib/sizing';
  import { fmtQ } from '../../lib/ui.svelte';
  import ThreeRows from '../ThreeRows.svelte';
  import CompareTable from './CompareTable.svelte';
  import DeskRows from './DeskRows.svelte';
  import Kbd from './Kbd.svelte';

  interface Props { a: Analysis; mine: Rows | null; legacyNote?: string | null; tags?: Record<number, string>; mineLabel?: string | null }
  let { a, mine, legacyNote = null, tags = {}, mineLabel = null }: Props = $props();

  const mineAt = $derived(mineIndex(a, mine));
  const pinned = $derived(mineAt !== null && mineAt !== 0);
  const rk = $derived(ranksOf(a));
  const wheel = $derived(a.rules.wheel ?? 0);
  const rem = $derived(remarksOf(a, mine).filter((r) => mineLabel === null || (r.kind !== 'same' && r.kind !== 'mistake')));
  const mineRemark = $derived(rem.find((r) => r.kind === 'same' || r.kind === 'mistake') ?? null);
  const restRemarks = $derived(rem.filter((r) => r !== mineRemark).map(remarkText).join(t('remark_sep')));
  const texts = $derived(a.cands.map((cd) => cd.info.keys.map((k) => rowLabel(k, ta('row_labels'), wheel))));
  const qGap = $derived(notableQGap(a.rules));
  const yours = $derived(mineLabel ?? t('yours'));
  let showAll = $state(false);
  const shown = $derived(a.cands.map((_, i) => i).slice(0, showAll ? a.cands.length : 30));
  /** The list's order: the pinned one, then the shown. */
  const order = $derived(pinned ? [mineAt!, ...shown] : shown);

  let current = $state(0);
  let hovered = $state<number | null>(null);
  let lastA: Analysis | null = null;
  $effect.pre(() => {
    if (a !== lastA) {
      lastA = a;
      current = pinned ? mineAt! : 0;
      hovered = null;
      showAll = false;
    }
  });
  const showing = $derived(hovered ?? current);
  const baseAt = $derived(mineAt !== null && showing !== mineAt ? mineAt : showing !== 0 ? 0 : null);

  function label(i: number): { text: string; strong: boolean } {
    if (i === mineAt) return { text: i === 0 ? `${t('best')} · ${yours}` : a.cands[i].extra ? yours : t('yours_rank', yours, rk[i]), strong: true };
    if (a.cands[i].extra) return { text: '−', strong: false };
    if (i === 0) return { text: t('best'), strong: true };
    return { text: t('cand_n', rk[i]), strong: false };
  }
  const shape = (cd: Candidate) => { const s = shapeOf(cd.arrangement, cd.info.categories, wheel); return s === null ? null : ta('shape_names')[s]; };

  function onKey(e: KeyboardEvent) {
    if (document.querySelector('dialog[open]') || (e.target as HTMLElement | null)?.closest('input, textarea, select')) return;
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    e.preventDefault();
    const i = order.indexOf(current);
    current = order[Math.max(0, Math.min(order.length - 1, i + (e.key === 'ArrowDown' ? 1 : -1)))];
    hovered = null;
    document.querySelector(`.dc [data-cand="${current}"]`)?.scrollIntoView({ block: 'nearest' });
  }

  // The detail's cards: the two arrangements side by side in the width, three rows in about half the height.
  let detailW = $state(0);
  let detailH = $state(0);
  const stackCard = $derived(Math.max(24, Math.min(
    Math.floor(((detailW - 32 - 32 - 12) / 2 - CARD_GAP * 4) / 5),
    (detailH * 0.5 - 3 * 26 - 16) / (3 * 1.4),
    96,
  )));
</script>

<svelte:window onkeydown={onKey} />

{#snippet item(i: number)}
  {@const cd = a.cands[i]}
  {@const b = label(i)}
  {@const sh = shape(cd)}
  {@const best = a.cands[0]}
  <div class="cand" class:on={current === i} class:hover={hovered === i && current !== i} class:mine={i === mineAt} class:tagged={!!tags[i]}
    role="button" tabindex="-1" data-cand={i}
    onclick={() => { current = i; }} onkeydown={() => {}} onmouseenter={() => (hovered = i)} onmouseleave={() => (hovered = null)}>
    <div class="cand-head">
      <span class="badge" class:strong={b.strong}>{b.text}</span>
      {#if tags[i]}<span class="tag body-small ellipsis">{tags[i]}</span>{/if}
      <span class="middle body-medium ellipsis" class:shape={sh !== null}>{sh ? `${sh} · ${texts[i].join(' · ')}` : texts[i].join(' · ')}</span>
      {#if cd.q !== null}
        {#if i !== 0 && best.q !== null && best.q - cd.q >= qGap}<span class="body-medium bad nowrap">({fmtQ(cd.q - best.q)})</span>{/if}
        <span class="title-medium bold nowrap" class:good={cd.q >= 0} class:bad={cd.q < 0}>{fmtQ(cd.q)}</span>
      {/if}
    </div>
    <ThreeRows rows={cd.arrangement} categories={cd.info.categories} max={34} />
  </div>
{/snippet}

<div class="dc">
  {#if a.dragon || legacyNote || rem.length}
    <div class="top">
      {#if a.dragon}<div class="body-large good">{t('dragon')}</div>{/if}
      {#if legacyNote}<div class="body-medium bad">{legacyNote}</div>{/if}
      {#if rem.length}
        <div class="remarks">
          <span class="title-small">{t('remarks_title')}</span>
          {#if mineRemark?.kind === 'mistake'}<span class="mistake body-medium bold">{remarkText(mineRemark)}</span>
          {:else if mineRemark}<span class="body-medium bold">{remarkText(mineRemark)}</span>{/if}
          {#if restRemarks}<span class="body-medium">{restRemarks}</span>{/if}
        </div>
      {/if}
    </div>
  {/if}
  <div class="body">
    <div class="list">
      {#if pinned}{@render item(mineAt!)}<hr />{/if}
      {#each shown as i (i)}{#if !(pinned && i === mineAt)}{@render item(i)}{/if}{/each}
      {#if !showAll && a.cands.length > 30}
        <button class="btn text more" onclick={() => (showAll = true)}>{t('show_all', a.cands.length)}</button>
      {/if}
    </div>
    <div class="detail" bind:clientWidth={detailW} bind:clientHeight={detailH}>
      {#if detailW > 0}
        {@const x = a.cands[showing]}
        {@const base = baseAt !== null ? a.cands[baseAt] : null}
        {@const xl = label(showing)}
        {@const bl = baseAt !== null ? label(baseAt) : null}
        {@const sh = shape(x)}
        <div class="detail-head">
          <span class="badge" class:strong={xl.strong}>{xl.text}</span>
          {#if sh}<span class="title-medium shape-name">{sh}</span>{/if}
          <span class="grow"></span>
          <span class="body-small dim keys"><Kbd k="↑" /><Kbd k="↓" />{t('k_move')}</span>
        </div>
        <div class="pair">
          <div class="side">
            {#if base}<div class="label-large side-title">{t('cmp_this')} · {xl.text}</div>{/if}
            <DeskRows rows={x.arrangement} keys={x.info.keys} categories={x.info.categories} width={stackCard} {wheel} outlined={base ? moved(x, base) : new Set()} />
          </div>
          {#if base && bl}
            <div class="side base">
              <div class="label-large side-title dim">{t('cmp_base')} · {bl.text}</div>
              <DeskRows rows={base.arrangement} keys={base.info.keys} categories={base.info.categories} width={stackCard} {wheel} />
            </div>
          {/if}
        </div>
        {#if base && bl}<div class="body-small dim">{t('cmp_moved', bl.text)}</div>{/if}
        <CompareTable {a} {x} {base} xLabel={xl.text} baseLabel={bl?.text ?? ''} />
      {/if}
    </div>
  </div>
</div>

<style>
  .dc { flex: 1; min-height: 0; display: flex; flex-direction: column; }
  .top { padding: 0 16px 8px; display: flex; flex-direction: column; gap: 6px; flex: none; }
  .remarks { background: var(--secondary-container); color: var(--on-secondary-container); border-radius: 14px; padding: 8px 14px; display: flex; flex-wrap: wrap; gap: 4px 10px; align-items: baseline; }
  .mistake { background: var(--error-container); color: var(--on-error-container); border-radius: 8px; padding: 2px 8px; }
  .body { flex: 1; min-height: 0; display: grid; grid-template-columns: minmax(380px, 40%) 1fr; gap: 12px; padding: 0 12px 12px; }
  .list { overflow: auto; min-height: 0; padding-right: 4px; }
  .cand { padding: 8px 10px; border-radius: 12px; cursor: pointer; display: flex; flex-direction: column; gap: 4px; margin-bottom: 2px; }
  .cand.mine { background: var(--surface-container-high); }
  .cand.tagged:not(.mine) { background: color-mix(in srgb, var(--tertiary-container) 45%, transparent); }
  .cand.hover { background: var(--surface-container-highest); }
  .cand.on { background: var(--secondary-container); outline: 2px solid var(--primary); outline-offset: -2px; }
  .cand-head { display: flex; align-items: center; gap: 6px; min-width: 0; }
  .badge { background: var(--surface-container-highest); color: var(--on-surface); }
  .badge.strong { background: var(--primary); color: var(--on-primary); }
  .tag { flex: 0 1 auto; max-width: 40%; padding: 1px 6px; border-radius: 6px; background: var(--tertiary-container); color: var(--on-tertiary-container); }
  .middle { flex: 1; color: var(--on-surface-variant); }
  .middle.shape { color: var(--primary); font-weight: 500; }
  hr { border: none; border-top: 1px solid var(--outline-variant); margin: 4px 8px; }
  .more { margin: 8px; }
  .detail { min-height: 0; overflow: auto; background: var(--surface-container-low); border-radius: 16px; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px; }
  .detail-head { display: flex; align-items: center; gap: 8px; }
  .shape-name { color: var(--primary); }
  .grow { flex: 1; }
  .keys { display: inline-flex; gap: 4px; align-items: center; }
  .pair { display: flex; gap: 32px; }
  .side { display: flex; flex-direction: column; gap: 6px; }
  .side-title { color: var(--primary); }
  .side.base .side-title { color: var(--on-surface-variant); }
</style>
