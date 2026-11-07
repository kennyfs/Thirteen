<!--
  A candidate's figures as the app's tiles, a group a line: the expected points; scooping and scooped; the home run
  and its loss; winning the front, middle and back; the special rows lost (under rules that pay for them). Each tile
  its name over its figure; against the best (`best`, null for the best itself) a
  notable difference beside the figure, green where better and red where worse. Two of these side by side line up
  tile for tile.
-->
<script lang="ts">
  import { lostShown, pct, pctDiff, ROYALTY_LOST, tone } from '../../lib/compare';
  import type { Analysis, Candidate } from '../../lib/engine';
  import { t, ta } from '../../lib/i18n.svelte';
  import { fitRem, fmtQ, SHRINK_MIN, textWidth } from '../../lib/ui.svelte';

  interface Props { a: Analysis; x: Candidate; best: Candidate | null }
  let { a, x, best }: Props = $props();
  const names = $derived(ta('prob_names'));
  const lost = $derived(lostShown(a));
  const cls = (v: number) => (v > 0 ? 'good' : v < 0 ? 'bad' : '');
  // A name's room in a line of `n` tiles: a long one (Vietnamese's "Thắng chi giữa") shrinks a little to fit, and
  // the rows' three go two and one where they would not (as the app's ProbGrid).
  let w = $state(0);
  const room = (n: number) => (w - 6 * (n - 1)) / n - 20;
  const groups = $derived(!w || [5, 6, 7].every((k) => textWidth(names[k], 13) * SHRINK_MIN <= room(3))
    ? [[0, 1], [2, 3], [5, 6, 7]] : [[0, 1], [2, 3], [5, 6], [7]]);
</script>

<div class="tiles" bind:clientWidth={w}>
  {#if x.q !== null}
    {@const d = best?.q != null ? x.q - best.q : 0}
    <div class="tile q">
      <span class="body-small dim ellipsis">{t('expected')}</span>
      <span class="fig">
        <span class="title-large bold" class:good={x.q >= 0} class:bad={x.q < 0}>{fmtQ(x.q)}</span>
        {#if best && d < -0.005}<span class="body-medium bad nowrap">{fmtQ(d)}</span>{/if}
      </span>
    </div>
  {/if}
  {#if x.probs}
    {#each groups as g, gi (gi)}<div class="line">{#each g as k (k)}
      {@const v = x.probs[k]}
      {@const b = best?.probs?.[k] ?? null}
      {@const tn = b === null ? 0 : tone(k, v, b)}
      <div class="tile">
        <span class="body-small dim ellipsis" style:font-size={w ? fitRem(names[k], 13, room(g.length)) : null}>{names[k]}</span>
        <span class="fig">
          <span class="title-medium {cls(tn)}" class:bold={tn !== 0}>{pct(v)}</span>
          {#if tn !== 0 && b !== null}<span class="body-small bold nowrap {cls(tn)}">{pctDiff(v - b)}</span>{/if}
        </span>
      </div>
    {/each}</div>{/each}
    {#each Array(Math.ceil(lost.length / 2)) as _, li (li)}<div class="line">{#each lost.slice(li * 2, li * 2 + 2) as i (i)}
      <div class="tile">
        <span class="body-small dim ellipsis" style:font-size={w ? fitRem(names[ROYALTY_LOST + i], 13, room(2)) : null}>{names[ROYALTY_LOST + i]}</span>
        <span class="fig"><span class="title-medium">{pct(x.probs[ROYALTY_LOST + i])}</span></span>
      </div>
    {/each}{#if lost.slice(li * 2, li * 2 + 2).length === 1}<div class="tile blank"></div>{/if}</div>{/each}
  {/if}
</div>

<style>
  .tiles { display: flex; flex-direction: column; gap: 6px; }
  .line { display: flex; gap: 6px; }
  .line > .tile { flex: 1 1 0; }
  .tile.blank { background: none; }
  .tile { background: var(--surface-container-highest); border-radius: 10px; padding: 6px 10px; display: flex; flex-direction: column; gap: 1px; min-width: 0; }
  .tile.q { background: var(--secondary-container); color: var(--on-secondary-container); }
  .fig { display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; font-variant-numeric: tabular-nums; }
</style>
