<!--
  Everyone's rows on the table, the player first. Wide: a line per player filling the height, the scoops as arcs at
  the right. Narrow: a block per player, the rows side by side (a tap shows them one above another). With `onplayer`, a
  tap on a player calls it instead (a battle's analysis of their hand).
-->
<script lang="ts">
  import { rowLabel, ROW_SIZES } from '../lib/cards';
  import type { Score } from '../lib/engine';
  import { t, ta } from '../lib/i18n.svelte';
  import { MAX_OTHER, MAX_PLAY, rowWidth } from '../lib/sizing';
  import { signed, textRoom, ui } from '../lib/ui.svelte';
  import RowCards from './RowCards.svelte';

  interface Props {
    rows: number[][][]; score: Score; wheel: number; names: string[]; notes: (string | null)[]; totals: number[];
    onplayer?: ((seat: number) => void) | null;
  }
  let { rows, score, wheel, names, notes, totals, onplayer = null }: Props = $props();
  const tap = (s: number) => { if (onplayer) onplayer(s); else if (sideBySide) expanded = expanded === s ? null : s; };

  let w = $state(0);
  let h = $state(0);
  const seats = $derived(rows.length);
  const wide = $derived(w > h);
  const ARROWS = 56, SEAT_GAP = 4, COLUMN_GAP = 10, SMALL_GAP = 3;
  const nameWidth = $derived(textRoom(92));
  // Wide: the lines, as many as the players, fill the height; their cards as large as the height and width allow.
  const lineCard = $derived(Math.max(14, Math.min(
    (w - nameWidth - ARROWS - 16 - COLUMN_GAP * 3 - SMALL_GAP * 10 - 12) / 13,
    ((h - SEAT_GAP * (seats - 1)) / seats - textRoom(18) - 10) / 1.4,
    MAX_PLAY,
  )));
  // Narrow: the rows side by side fill the width.
  const GRID_PAD = 6, GRID_COLUMN_GAP = 6, GRID_CARD_GAP = 1;
  const sideCard = $derived(Math.min((w - GRID_PAD * 2 - GRID_COLUMN_GAP * 2 - GRID_CARD_GAP * 10 - 6) / 13, MAX_OTHER));
  const sideBySide = $derived(sideCard >= 20 && rowWidth(3, sideCard, GRID_CARD_GAP) >= textRoom(48));
  const labelWidth = $derived(Math.max(72, textRoom(13) * 5.5));
  const stackCard = $derived(Math.max(16, Math.min((w - GRID_PAD * 2 - labelWidth - 8 - SMALL_GAP * 4) / 5, ui.sizes.play)));
  let expanded = $state<number | null>(null);

  const texts = $derived.by(() => {
    const labels = ta('row_labels');
    const mine = score.info[0];
    return score.info.map((info, s) => [0, 1, 2].map((r) => ({
      text: info.valid ? rowLabel(info.keys[r], labels, wheel) : t('fouled_short'),
      d: s !== 0 && info.valid && mine.valid ? Math.sign(mine.keys[r] - info.keys[r]) : null,
    })));
  });

  function sub(s: number): string {
    if (s === 0) return t('total_so_far', signed(totals[0]));
    const v = score.pairs[0][s];
    return v > 0 ? t('vs_you_win', signed(v).slice(1)) : v < 0 ? t('vs_you_lose', signed(v).slice(1)) : t('vs_you_even');
  }
  const markText = (d: number) => t(d > 0 ? 'row_win' : d < 0 ? 'row_lose' : 'row_tie');
  const markClass = (d: number) => (d > 0 ? 'win' : d < 0 ? 'lose' : 'tie');
  const pointsClass = (x: number) => (x > 0 ? 'fgood' : x < 0 ? 'fbad' : '');

  // The scoops as arcs from the scooper's line to the scooped one's, bulging right, nested by span.
  let lineH = $state(0);
  const arcs = $derived.by(() => {
    const out: { d: string; head: string; color: string }[] = [];
    if (!wide || lineH === 0) return out;
    const pairs: [number, number][] = [];
    for (let i = 0; i < seats; i++) for (let j = 0; j < seats; j++) if (i !== j && score.scoops[i][j]) pairs.push([i, j]);
    const line = lineH + SEAT_GAP;
    const spacing = Math.min(lineH * 0.2, 12);
    const partners = (i: number) => {
      const all = pairs.flatMap(([a, b]) => (a === i ? [b] : b === i ? [a] : []));
      return [...all.filter((x) => x < i).sort((a, b) => b - a), ...all.filter((x) => x > i).sort((a, b) => b - a)];
    };
    const port = (i: number, j: number) => { const p = partners(i); return line * i + lineH / 2 + (p.indexOf(j) - (p.length - 1) / 2) * spacing; };
    const x0 = 3;
    for (const [i, j] of [...pairs].sort(([a, b], [c, d]) => (a === 0 || b === 0 ? 1 : 0) - (c === 0 || d === 0 ? 1 : 0))) {
      const span = Math.abs(i - j);
      const lane = span === 1 ? 0 : span === 2 ? (Math.min(i, j) === 0 ? 1 : 2) : 3;
      const y1 = port(i, j), y2 = port(j, i);
      const dir = y2 > y1 ? 1 : -1;
      const k = (12 + 14 * lane) / 0.75;
      const v = Math.min(0.9 * k, Math.abs(y2 - y1) * 0.35);
      const c1 = [x0 + k, y1 + dir * v], c2 = [x0 + k, y2 - dir * v];
      const tt = 0.88, u = 1 - tt;
      const nx = u * u * u * x0 + 3 * u * u * tt * c1[0] + 3 * u * tt * tt * c2[0] + tt * tt * tt * x0;
      const ny = u * u * u * y1 + 3 * u * u * tt * c1[1] + 3 * u * tt * tt * c2[1] + tt * tt * tt * y2;
      const dx = x0 - nx, dy = y2 - ny, n = Math.max(1e-3, Math.hypot(dx, dy));
      const head = [0.45, -0.45].map((a) => {
        const bx = -(dx / n) * Math.cos(a) + (dy / n) * Math.sin(a);
        const by = -(dy / n) * Math.cos(a) - (dx / n) * Math.sin(a);
        return `M${x0} ${y2}L${x0 + 8 * bx} ${y2 + 8 * by}`;
      }).join('');
      out.push({ d: `M${x0} ${y1}C${c1[0]} ${c1[1]} ${c2[0]} ${c2[1]} ${x0} ${y2}`, head, color: i === 0 ? 'var(--felt-good)' : j === 0 ? 'var(--felt-bad)' : 'var(--on-felt-dim)' });
    }
    return out;
  });
  const anyScoop = $derived(score.scoops.some((r) => r.some((x) => x)));
</script>

<div class="results" bind:clientWidth={w} bind:clientHeight={h}>
  {#if w > 0}
    {#if wide}
      <div class="lines-wrap">
        <div class="lines" style:gap="{SEAT_GAP}px">
          {#each rows as seat, s (s)}
            {@const info = score.info[s]}
            <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
            <div class="line" class:me={s === 0} class:homerun={score.homeRun[s]} class:pick={!!onplayer}
              role={onplayer ? 'button' : undefined} tabindex={onplayer ? 0 : undefined}
              onclick={() => onplayer?.(s)} onkeydown={(e) => { if (e.key === 'Enter') onplayer?.(s); }}
              bind:clientHeight={() => lineH, (v) => { if (s === 0) lineH = v; }}
              style:gap="{COLUMN_GAP}px">
              <div class="name" style:width="{nameWidth}px">
                <div class="nameline"><span class="title-medium bold ellipsis">{names[s]}</span><span class="title-medium bold {pointsClass(score.result[s])}">{signed(score.result[s])}</span></div>
                <div class="body-small fdim ellipsis">{sub(s)}</div>
              </div>
              {#each [0, 1, 2] as r (r)}
                <div class="col">
                  <div class="label">
                    <span class="label-small nowrap" class:fbad={!info.valid} class:fdim={info.valid}>{texts[s][r].text}</span>
                    {#if texts[s][r].d !== null}<span class="mark small {markClass(texts[s][r].d!)}">{markText(texts[s][r].d!)}</span>{/if}
                  </div>
                  <div class="box" class:bonus={info.royalties[r] > 0}>
                    <RowCards cards={seat[r]} category={info.valid ? info.categories[r] : 0} width={lineCard} gap={SMALL_GAP} />
                  </div>
                </div>
              {/each}
            </div>
          {/each}
        </div>
        <div class="arrows" style:width="{ARROWS}px">
          {#if anyScoop}
            <div class="label-small fdim">{ta('prob_names')[0]}</div>
            <svg width={ARROWS} height={lineH * seats + SEAT_GAP * (seats - 1)} style:margin-top="-1rem" aria-hidden="true">
              {#each arcs as a, i (i)}
                <path d={a.d} stroke={a.color} fill="none" stroke-width="3" stroke-linecap="round" />
                <path d={a.head} stroke={a.color} fill="none" stroke-width="3" stroke-linecap="round" />
              {/each}
            </svg>
          {/if}
        </div>
      </div>
    {:else}
      <div class="blocks">
        {#each rows as seat, s (s)}
          {@const info = score.info[s]}
          {@const scooped = rows.map((_, j) => j).filter((j) => score.scoops[s][j])}
          {@const by = rows.map((_, j) => j).filter((j) => score.scoops[j][s])}
          {@const open = sideBySide && expanded === s}
          <div class="block" class:me={s === 0} class:homerun={score.homeRun[s]} role="button" tabindex="0" style:padding="6px {GRID_PAD}px"
            onclick={() => tap(s)} onkeydown={(e) => { if (e.key === 'Enter') tap(s); }}>
            <div class="head">
              <div class="who">
                <div class="nameline"><span class="title-medium bold nowrap">{names[s]}</span>{#if notes[s]}<span class="body-small fdim ellipsis">{notes[s]}</span>{/if}</div>
                <div class="subline body-small">
                  <span class="fdim nowrap">{sub(s)}</span>
                  {#if scooped.length}<span class="bold ellipsis" class:fgood={s === 0}>{ta('prob_names')[0]} → {scooped.map((j) => names[j]).join('、')}</span>{/if}
                  {#if by.length}<span class="bold ellipsis" class:fbad={s === 0} class:fdim={s !== 0}>{t('scooped_by', by.map((j) => names[j]).join('、'))}</span>{/if}
                </div>
              </div>
              {#if score.homeRun[s]}<span class="label-large hl">{t('home_run')}</span>{/if}
              <span class="headline-small {pointsClass(score.result[s])}">{signed(score.result[s])}</span>
            </div>
            {#if sideBySide && !open}
              <div class="side" style:gap="{GRID_COLUMN_GAP}px">
                {#each [0, 1, 2] as r (r)}
                  {@const x = texts[s][r]}
                  <div class="col" style:width="{rowWidth(ROW_SIZES[r], sideCard, GRID_CARD_GAP) + 2}px">
                    <!-- Against another player, the hand's name on the color of the player's result there. -->
                    {#if x.d !== null && info.valid}
                      <span class="label-small rowname {markClass(x.d)}" aria-label="{x.text}, {markText(x.d)}">{x.text}</span>
                    {:else}
                      <span class="label-small ellipsis" class:fbad={!info.valid} class:fdim={info.valid}>{x.text}</span>
                    {/if}
                    <span class="spacer"></span>
                    <div class="box thin" class:bonus={info.royalties[r] > 0}>
                      <RowCards cards={seat[r]} category={info.valid ? info.categories[r] : 0} width={sideCard} gap={GRID_CARD_GAP} />
                    </div>
                  </div>
                {/each}
              </div>
            {:else}
              {#each [0, 1, 2] as r (r)}
                <div class="stack-row">
                  <div class="stack-label" style:width="{labelWidth}px">
                    <span class="body-small" class:fbad={!info.valid} class:fdim={info.valid}>{texts[s][r].text}</span>
                    {#if texts[s][r].d !== null}<span class="mark {markClass(texts[s][r].d!)}">{markText(texts[s][r].d!)}</span>{/if}
                  </div>
                  <div class="box" class:bonus={info.royalties[r] > 0}>
                    <RowCards cards={seat[r]} category={info.valid ? info.categories[r] : 0} width={stackCard} gap={SMALL_GAP} />
                  </div>
                </div>
              {/each}
            {/if}
          </div>
        {/each}
      </div>
    {/if}
  {/if}
</div>

<style>
  .results { width: 100%; height: 100%; overflow: auto; color: var(--on-felt); }
  .lines-wrap { display: flex; }
  .lines { flex: 1; display: flex; flex-direction: column; min-width: 0; }
  .line { display: flex; align-items: center; padding: 2px 8px; border-radius: 12px; }
  .line.me { background: color-mix(in srgb, var(--on-felt) 12%, transparent); }
  .line.pick { cursor: pointer; outline: 1.5px dashed color-mix(in srgb, var(--on-felt) 45%, transparent); outline-offset: -1.5px; }
  .line.pick:hover { background: color-mix(in srgb, var(--on-felt) 18%, transparent); }
  .homerun { outline: 2px solid var(--highlight); outline-offset: -2px; }
  .name { display: flex; flex-direction: column; min-width: 0; flex: none; }
  .nameline { display: flex; align-items: baseline; gap: 8px; min-width: 0; }
  .col { display: flex; flex-direction: column; gap: 2px; }
  .label { display: flex; align-items: center; gap: 4px; min-height: 18px; }
  .box { padding: 2px; border-radius: 6px; border: 2px solid transparent; margin: -2px; }
  .box.thin { padding: 1px; margin: -1px; border-radius: 5px; }
  .box.bonus { border-color: var(--highlight); }
  .mark { padding: 1px 7px; border-radius: 8px; font-size: 0.8125rem; font-weight: 500; white-space: nowrap; }
  .mark.small { padding: 0 5px; border-radius: 6px; font-size: 0.75rem; }
  .win { background: #d6efd8; color: #1e5e22; }
  .lose { background: #f8d9d6; color: #8c1d18; }
  .tie { background: color-mix(in srgb, var(--on-felt) 18%, transparent); color: var(--on-felt); }
  .arrows { flex: none; display: flex; flex-direction: column; align-items: flex-end; }
  .blocks { display: flex; flex-direction: column; gap: 6px; }
  .block { border-radius: 12px; background: var(--slot); display: flex; flex-direction: column; gap: 3px; cursor: pointer; }
  .block.me { background: color-mix(in srgb, var(--on-felt) 14%, transparent); }
  .head { display: flex; align-items: center; gap: 6px; }
  .who { flex: 1; min-width: 0; }
  .subline { display: flex; gap: 8px; align-items: center; min-width: 0; }
  .side { display: flex; }
  .side .col { align-self: stretch; }
  .spacer { flex: 1; }
  .rowname { align-self: flex-start; max-width: 100%; padding: 0 4px; border-radius: 5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .stack-row { display: flex; align-items: center; gap: 8px; }
  .stack-label { display: flex; flex-direction: column; gap: 2px; align-items: flex-start; flex: none; }
  .fdim { color: var(--on-felt-dim); }
  .fgood { color: var(--felt-good); }
  .fbad { color: var(--felt-bad); }
  .hl { color: var(--highlight); }
</style>
