<!--
  The tour (tour.svelte.ts): the page dimmed but for a light on the step's element, and a card beside it with the
  step's words, Back, Next and, always, Skip. Escape or the browser's Back (a phone's back gesture) ends it too; a
  click on the dimmed page does nothing, so nothing under it is touched by mistake.
-->
<script lang="ts">
  import { onMount, tick } from 'svelte';
  import { t, td } from '../lib/i18n.svelte';
  import { nav } from '../lib/nav.svelte';
  import { ui } from '../lib/ui.svelte';
  import { STEPS, tour } from '../lib/tour.svelte';

  let { ontutorial }: { ontutorial: () => void } = $props();

  const step = $derived(STEPS[tour.step]);
  const last = $derived(tour.step === STEPS.length - 1);
  let rect = $state<DOMRect | null>(null);
  let cardW = $state(0);
  let cardH = $state(0);
  let vw = $state(innerWidth);
  let vh = $state(innerHeight);
  let card: HTMLElement | undefined = $state();

  /** The step's element, measured again until the screen has settled (a screen shown anew lays out in a frame or two). */
  function measure() {
    vw = innerWidth;
    vh = innerHeight;
    const el = step?.target ? document.querySelector(`[data-tour="${step.target}"]`) : null;
    const r = el?.getBoundingClientRect();
    rect = r && r.width > 0 && r.height > 0 ? r : null;
  }

  $effect(() => {
    const s = step;
    if (!s) return;
    if (nav.screen !== s.screen) nav.go(s.screen);
    else if (nav.sub) nav.replace(s.screen);
    let frames = 0;
    let id = 0;
    const loop = () => { measure(); if (++frames < 20) id = requestAnimationFrame(loop); };
    tick().then(() => { id = requestAnimationFrame(loop); card?.focus(); });
    return () => cancelAnimationFrame(id);
  });

  onMount(() => {
    const onResize = () => measure();
    // No key reaches the page under the tour (a computer's table would play its keys); Enter and Space still press
    // the card's buttons, Tab still goes between them.
    const onKey = (e: KeyboardEvent) => {
      e.stopPropagation();
      if (e.key === 'Escape') tour.end();
      else if (e.key === 'ArrowRight') tour.next();
      else if (e.key === 'ArrowLeft') tour.prev();
      else if (!(e.key === 'Tab' || ((e.key === 'Enter' || e.key === ' ') && card?.contains(e.target as Node)))) e.preventDefault();
      else return;
      e.preventDefault();
    };
    // The browser's Back ends the tour; the tour's own going from screen to screen does not come here (it pushes).
    let armed = false;
    const arm = setTimeout(() => (armed = true), 500);
    const onPop = () => { if (armed) tour.end(); };
    addEventListener('resize', onResize);
    addEventListener('keydown', onKey, true);
    addEventListener('popstate', onPop);
    return () => {
      clearTimeout(arm);
      removeEventListener('resize', onResize);
      removeEventListener('keydown', onKey, true);
      removeEventListener('popstate', onPop);
    };
  });

  const PAD = 6;
  const hole = $derived(rect ? { x: rect.left - PAD, y: rect.top - PAD, w: rect.width + PAD * 2, h: rect.height + PAD * 2 } : null);
  // The card below the light if it fits, else above, else beside it, else over the bottom of the window.
  const place = $derived.by(() => {
    const m = 12;
    const w = Math.min(380, vw - 2 * m);
    if (!hole) return { x: (vw - w) / 2, y: Math.max(m, (vh - cardH) / 2), w };
    const cx = Math.max(m, Math.min(vw - w - m, hole.x + hole.w / 2 - w / 2));
    if (hole.y + hole.h + m + cardH <= vh - m) return { x: cx, y: hole.y + hole.h + m, w };
    if (hole.y - m - cardH >= m) return { x: cx, y: hole.y - m - cardH, w };
    const cy = Math.max(m, Math.min(vh - cardH - m, hole.y + hole.h / 2 - cardH / 2));
    if (hole.x + hole.w + m + w <= vw - m) return { x: hole.x + hole.w + m, y: cy, w };
    if (hole.x - m - w >= m) return { x: hole.x - m - w, y: cy, w };
    return { x: cx, y: vh - cardH - m, w };
  });
</script>

{#if step}
  <div class="tour" role="presentation">
    {#if hole}
      <div class="hole" style:left="{hole.x}px" style:top="{hole.y}px" style:width="{hole.w}px" style:height="{hole.h}px"></div>
    {:else}
      <div class="shade"></div>
    {/if}
    <div class="card" role="dialog" aria-modal="true" aria-labelledby="tour-title" tabindex="-1" bind:this={card}
      bind:clientWidth={cardW} bind:clientHeight={cardH} style:left="{place.x}px" style:top="{place.y}px" style:width="{place.w}px">
      <div class="top">
        <span class="label-large dim">{tour.step + 1} / {STEPS.length}</span>
        <span class="grow"></span>
        {#if !last}<button class="btn text skip" onclick={() => tour.end()}>{t('tour_skip')}</button>{/if}
      </div>
      <h2 id="tour-title" class="title-large">{t(step.title)}</h2>
      <p class="body-large">{ui.desk ? td(step.text) : t(step.text)}</p>
      <div class="actions">
        {#if tour.step > 0}<button class="btn text" onclick={() => tour.prev()}>{t('tour_prev')}</button>{/if}
        <span class="grow"></span>
        {#if last}
          <button class="btn tonal" onclick={() => { tour.end(); ontutorial(); }}>{t('tut_title')}</button>
          <button class="btn filled" onclick={() => tour.end()}>{t('tour_done')}</button>
        {:else}
          <button class="btn filled" onclick={() => tour.next()}>{t('tour_next')}</button>
        {/if}
      </div>
    </div>
  </div>
{/if}

<style>
  .tour { position: fixed; inset: 0; z-index: 50; }
  .shade { position: absolute; inset: 0; background: rgba(0, 0, 0, 0.55); }
  .hole { position: absolute; border-radius: 14px; box-shadow: 0 0 0 200vmax rgba(0, 0, 0, 0.55); outline: 3px solid var(--highlight);
    transition: left 0.2s, top 0.2s, width 0.2s, height 0.2s; pointer-events: none; }
  /* The hole lets nothing through: the page under the tour is not touched. */
  .tour::before { content: ''; position: absolute; inset: 0; }
  .card { position: absolute; background: var(--surface-container-high); color: var(--on-surface); border-radius: 20px; padding: 14px 16px 12px;
    box-shadow: 0 6px 24px rgba(0, 0, 0, 0.35); display: flex; flex-direction: column; gap: 6px; outline: none; }
  .card h2 { margin: 0; }
  .card p { margin: 0; }
  .top { display: flex; align-items: center; min-height: 32px; }
  .grow { flex: 1; }
  .skip { padding: 0 10px; min-height: 32px; }
  .actions { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
</style>
