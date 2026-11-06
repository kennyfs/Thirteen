<!--
  How to play: a few pages for someone who has never played. The rows; which row hand is bigger; fouls; the points
  under the rules now chosen; a hand to set and have the AI judge (not scored or recorded); tips from the AI's own
  analysis of 10,000 random hands (docs/tips.md); and on to practice or the tour. At #/<screen>/tutorial, from the
  welcome card, the settings, the table's help and the tour's end; Back leaves it.
-->
<script lang="ts">
  import { onMount } from 'svelte';
  import type { App } from '../lib/app.svelte';
  import { Arranger } from '../lib/arranger.svelte';
  import type { Rows } from '../lib/cards';
  import { mineIndex, ranks } from '../lib/compare';
  import * as engine from '../lib/engine';
  import type { Analysis } from '../lib/engine';
  import { t, ta, td } from '../lib/i18n.svelte';
  import { nav } from '../lib/nav.svelte';
  import * as R from '../lib/rules';
  import { tour } from '../lib/tour.svelte';
  import { fmtQ, ui } from '../lib/ui.svelte';
  import Arranging from './Arranging.svelte';
  import Icon from './Icon.svelte';
  import RowCards from './RowCards.svelte';
  import TitleBar from './TitleBar.svelte';
  import DeskArranging from './desk/DeskArranging.svelte';

  let { app }: { app: App } = $props();

  /** "As" to a card: rank * 4 + suit, ranks 2 to A, suits c d h s. */
  const card = (s: string) => '23456789TJQKA'.indexOf(s[0]) * 4 + 'cdhs'.indexOf(s[1]);
  const cards = (s: string) => s.split(' ').map(card);
  const rows = (f: string, m: string, b: string): Rows => [cards(f), cards(m), cards(b)];

  const GOAL = rows('Ac Qh 5h', '9c 9h 7d 7s 2c', 'Ks Kd Kh 4s 4d');
  const GOAL_CATS = [1, 3, 7];
  // A five-card example of each kind, the biggest first (category 9 down to 1).
  const KINDS: [number, number[]][] = [
    [9, cards('9h 8h 7h 6h 5h')], [8, cards('Ks Kh Kd Kc 4s')], [7, cards('Qs Qh Qd 7c 7d')], [6, cards('Ad Jd 8d 6d 3d')],
    [5, cards('Tc 9s 8h 7d 6c')], [4, cards('8s 8h 8d Kc 2s')], [3, cards('Js Jd 4h 4c Ad')], [2, cards('9c 9d Ah 7s 3h')], [1, cards('Ac Qd 9h 6s 4c')],
  ];
  const FOUL = rows('Qs Qh 5c', 'Ad Kc 9s 7h 3d', 'Th 8h 6h 4h 2h');
  const FIXED = rows('Ad Kc 5c', 'Qs Qh 9s 7h 3d', 'Th 8h 6h 4h 2h');
  // The hand to try: the instinctive way (aces with the queens' full house in the back) is far from the best (the
  // aces in front, the sixes in the full house) under family and taiwan (python/tips/pick_hand.py).
  const TRY = cards('As Ad Kh Kd Qh Qd Qc Jh 7h 6s 6h 3h 2h');

  const PAGES = 7;
  let page = $state(0);
  const values = $derived(app.ruleValues);
  const wheel = $derived(values.wheel ?? 0);

  // ---- The scoring, as the rules now chosen have it ----
  const n = (k: string) => R.fmt(values[k] ?? 0);
  const scoring = $derived.by(() => {
    const out: string[] = [];
    const row = values.row ?? 0;
    if (row > 0) out.push(t('tut_score_rows', n('row')));
    else out.push(t('tut_score_norows'));
    if ((values.scoop ?? 0) > 0) out.push(t(row > 0 ? 'tut_score_scoop' : 'tut_score_scoop_only', n('scoop')));
    if ((values.home_run ?? 0) > 0) out.push(t('tut_score_home', n('home_run')));
    const special = R.ROYALTIES.map((k, i) => [ta('royalty_labels')[i], values[k] ?? 0] as [string, number]).filter(([, v]) => v > 0)
      .map(([name, v]) => `${name} ${R.fmt(v)}`).join(t('list_sep'));
    if (special) out.push(t(values.on_win === 1 ? 'tut_score_special_win' : 'tut_score_special', special));
    if ((values.dragon ?? 0) > 0) out.push(t('tut_score_dragon', n('dragon')));
    return out;
  });

  // ---- The hand to try ----
  const vm = new Arranger((m) => (app.error = m), (row, free) => t('row_room', ta('row_names')[row], free));
  let tryRules = $state('');
  let checking = $state(false);
  let verdict = $state<{ a: Analysis; mine: Rows; cats: number[] } | null>(null);
  const tryWheel = $derived(R.values(tryRules).wheel ?? 0);

  async function resetTry() {
    tryRules = await engine.rulesFull(R.specString(app.ruleSpec));
    verdict = null;
    vm.reset(TRY, tryRules);
  }
  onMount(() => { resetTry().catch((e) => (app.error = e.message)); });

  async function check() {
    const ev = await vm.eval();
    if (ev.valid !== true) { app.error = t(ev.valid === null ? 'fill_rows_first' : 'fouled'); return; }
    checking = true;
    try {
      const mine = $state.snapshot(vm.rows) as Rows;
      const a = await engine.analyze([...TRY].sort((x, y) => x - y), tryRules, engine.BUILT_IN, [mine]);
      verdict = { a, mine, cats: ev.categories };
    } catch (e) {
      app.error = (e as Error).message;
    } finally {
      checking = false;
    }
  }
  const judged = $derived.by(() => {
    if (!verdict) return null;
    const { a, mine } = verdict;
    const i = mineIndex(a, mine);
    const best = a.cands[0];
    const total = a.cands.filter((c) => !c.extra).length;
    let text: string;
    if (i === 0) text = t('tut_try_best');
    else if (i === null || a.cands[i].extra) text = t('tut_try_extra');
    else text = t('tut_try_rank', ranks(a)[i], total, fmtQ((best.q ?? 0) - (a.cands[i].q ?? 0)).slice(1));
    // The words on why fit the best the network finds for this hand under the usual rules: the aces in front.
    const why = best.arrangement[0].filter((c) => c >= 48).length === 2;
    return { text, good: i === 0, best, why };
  });

  /** On to practice, the tutorial left in place (no step back through the history, so a tour can start at once). */
  const finish = () => nav.replace('PLAY');
</script>

{#snippet threeRows(r: Rows, cats: number[], w: number)}
  <div class="felt example">
    {#each [0, 1, 2] as i (i)}
      <div class="exrow">
        <span class="label-large exname">{ta('row_names')[i]}</span>
        <RowCards cards={r[i]} category={cats[i]} width={w} />
      </div>
    {/each}
  </div>
{/snippet}

<div class="page">
  <TitleBar title={t('tut_title')} onback={() => nav.back()}>
    <span class="body-medium dim">{t('tut_page', page + 1, PAGES)}</span>
  </TitleBar>
  <div class="scroll"><div class="content" class:trying={page === 4}>
    {#if page === 0}
      <h2 class="headline-small">{t('tut_goal_title')}</h2>
      <p class="body-large">{t('tut_goal_text')}</p>
      <p class="body-medium dim">{t('tut_goal_example')}</p>
      {@render threeRows(GOAL, GOAL_CATS, ui.desk ? 52 : 40)}
    {:else if page === 1}
      <h2 class="headline-small">{t('tut_ranks_title')}</h2>
      <p class="body-large">{t('tut_ranks_text')}</p>
      <div class="kinds">
        {#each KINDS as [cat, cs] (cat)}
          <div class="kind">
            <span class="title-small kname">{ta('category_names')[cat]}</span>
            <RowCards cards={cs} category={cat} width={ui.desk ? 40 : 30} />
          </div>
        {/each}
      </div>
      <p class="body-medium">{t('tut_ranks_front')}</p>
      <p class="body-medium dim">{t('tut_wheel', ta('wheel_options')[wheel])}</p>
    {:else if page === 2}
      <h2 class="headline-small">{t('tut_foul_title')}</h2>
      <p class="body-large">{t('tut_foul_text')}</p>
      {#if values.strict === 1}<p class="body-medium">{t('tut_foul_strict')}</p>{/if}
      <p class="title-small bad">{t('tut_foul_bad')}</p>
      {@render threeRows(FOUL, [2, 1, 6], ui.desk ? 46 : 36)}
      <p class="title-small good">{t('tut_foul_good')}</p>
      {@render threeRows(FIXED, [1, 2, 6], ui.desk ? 46 : 36)}
    {:else if page === 3}
      <h2 class="headline-small">{t('tut_score_title')}</h2>
      <p class="body-large">{t('tut_score_rules', app.rulesName(values))}</p>
      <ul class="body-large list">{#each scoring as line (line)}<li>{line}</li>{/each}</ul>
      <p class="body-medium dim">{t('tut_score_change')}</p>
    {:else if page === 4}
      <h2 class="headline-small">{t('tut_try_title')}</h2>
      {#if judged && verdict}
        <p class="title-medium" class:good={judged.good}>{judged.text}</p>
        {#if !judged.good}
          <p class="body-medium dim">{t('yours')}</p>
          {@render threeRows(verdict.mine, verdict.cats, ui.desk ? 46 : 34)}
        {/if}
        <p class="body-medium dim">{t('tut_try_best_rows')}</p>
        {@render threeRows(judged.best.arrangement, judged.best.info.categories, ui.desk ? 46 : 34)}
        {#if judged.why}<p class="body-large">{t('tut_try_why')}</p>{/if}
        <div class="buttons"><button class="btn outlined" onclick={() => resetTry()}><Icon name="replay" />{t('tut_try_again')}</button></div>
      {:else}
        <p class="body-large">{ui.desk ? td('tut_try_text') : t('tut_try_text')}</p>
        <div class="trybox">
          {#if tryRules}
            {#if ui.desk}
              <DeskArranging {vm} wheel={tryWheel} busy={checking} onconfirm={check} keys={app.deskKeys} />
              <div class="buttons">
                <button class="btn outlined" disabled={!vm.canUndo} onclick={() => vm.undo()}><Icon name="undo" />{t('undo')}</button>
                <button class="btn outlined" disabled={!vm.canClear} onclick={() => vm.clearRows()}><Icon name="clearAll" />{t('take_all')}</button>
                <button class="btn outlined" onclick={() => (vm.sortBySuit = !vm.sortBySuit)}><Icon name="swap" />{t(vm.sortBySuit ? 'sort_suit' : 'sort_rank')}</button>
                <span class="grow"></span>
                <button class="btn filled" disabled={checking || vm.rowsEval?.valid !== true} onclick={check}>{checking ? '…' : t('tut_try_check')}</button>
              </div>
            {:else}
              <Arranging {vm} wheel={tryWheel} busy={checking} confirmLabel={t('tut_try_check')} onconfirm={check} />
            {/if}
          {/if}
        </div>
      {/if}
    {:else if page === 5}
      <h2 class="headline-small">{t('tut_tips_title')}</h2>
      <p class="body-medium dim">{t('tut_tips_text')}</p>
      <ol class="body-large list tips">{#each ta('tut_tips') as tip (tip)}<li>{tip}</li>{/each}</ol>
    {:else}
      <h2 class="headline-small">{t('tut_end_title')}</h2>
      <p class="body-large">{t('tut_end_text')}</p>
      <div class="buttons">
        <button class="btn filled big" onclick={finish}>{t('tut_done')}</button>
        <button class="btn tonal big" onclick={() => { finish(); tour.start(); }}>{t('welcome_tour')}</button>
      </div>
    {/if}
  </div></div>
  <div class="bar tutbar">
    <button class="btn text" disabled={page === 0} onclick={() => (page = Math.max(0, page - 1))}>{t('tut_prev')}</button>
    <span class="dots" aria-hidden="true">{#each Array(PAGES) as _, i (i)}<span class="dot" class:on={i === page}></span>{/each}</span>
    {#if page < PAGES - 1}
      <button class="btn filled" onclick={() => (page = page + 1)}>{t('tut_next')}</button>
    {:else}
      <button class="btn filled" onclick={finish}>{t('tut_done')}</button>
    {/if}
  </div>
</div>

<style>
  .page { height: 100%; display: flex; flex-direction: column; min-height: 0; }
  .scroll { flex: 1; min-height: 0; overflow: auto; }
  .content { max-width: 760px; margin: 0 auto; padding: 4px 16px 16px; display: flex; flex-direction: column; gap: 10px; }
  .content.trying { height: 100%; box-sizing: border-box; max-width: 1100px; }
  h2 { margin: 4px 0 0; }
  p { margin: 0; }
  .example { padding: 10px 12px; display: flex; flex-direction: column; gap: 6px; align-self: flex-start; }
  .exrow { display: flex; align-items: center; gap: 10px; }
  .exname { color: var(--on-felt); min-width: 3.2em; }
  .kinds { display: flex; flex-direction: column; gap: 6px; }
  .kind { display: flex; align-items: center; gap: 12px; }
  .kname { min-width: 4.5em; }
  .list { margin: 0; padding-left: 1.4em; display: flex; flex-direction: column; gap: 8px; }
  .tips li::marker { color: var(--primary); font-weight: 700; }
  .trybox { flex: 1; min-height: 420px; display: flex; flex-direction: column; gap: 8px; }
  .buttons { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
  .grow { flex: 1; }
  .bar { flex: none; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 12px;
    border-top: 1px solid var(--surface-container-highest); }
  .dots { display: flex; gap: 6px; }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--surface-container-highest); }
  .dot.on { background: var(--primary); }
</style>
