<!--
  A card `width` wide (1.4 times as tall unless given a `height`): its rank and suit scale with it, whatever the font
  size. `tint` is the category of the row hand it makes (0: none), `light` the second group's lighter tint. A selected
  card has the table's highlight around it and, with `lift`, sits a little higher.
-->
<script lang="ts">
  import { isRed, rankText, suit } from '../lib/cards';
  import { ta } from '../lib/i18n.svelte';
  import SuitMark from './SuitMark.svelte';

  interface Props {
    card: number; width: number; height?: number; tint?: number; light?: boolean; fill?: string | null;
    selected?: boolean; lift?: boolean; onclick?: (() => void) | null;
    /** Faint: being dragged elsewhere. */
    faded?: boolean;
  }
  let { card, width, height, tint = 0, light = false, fill = null, selected = false, lift, onclick = null, faded = false }: Props = $props();

  const h = $derived(height ?? width * 1.4);
  const base = $derived(Math.min(width, h / 1.4));
  const lifted = $derived(lift ?? selected);
  const bg = $derived(fill ?? (tint <= 1 ? 'var(--card-face)' : `var(--tint-${tint}${light ? '-light' : ''})`));
  const name = $derived(ta('suit_names')[suit(card)] + ' ' + rankText(card));
</script>

{#if onclick}
  <button
    class="card" class:selected class:faded class:red={isRed(card)} aria-pressed={selected} aria-label={name} data-card={card}
    style:width="{width}px" style:height="{h}px" style:background={bg} style:border-radius="{width * 0.14}px"
    style:transform={lifted ? `translateY(${-width * 0.2}px)` : null} onclick={onclick}
  >
    <span class="rank" style:font-size="{base * 0.46}px">{rankText(card)}</span>
    <SuitMark suit={suit(card)} size={base * 0.4} gap={base * 0.03} />
  </button>
{:else}
  <div
    class="card" class:selected class:red={isRed(card)} role="img" aria-label={name}
    style:width="{width}px" style:height="{h}px" style:background={bg} style:border-radius="{width * 0.14}px"
  >
    <span class="rank" style:font-size="{base * 0.46}px">{rankText(card)}</span>
    <SuitMark suit={suit(card)} size={base * 0.4} gap={base * 0.03} />
  </div>
{/if}

<style>
  .card {
    flex: none; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 0;
    border: 1px solid var(--card-edge); color: var(--card-black); user-select: none; -webkit-user-select: none;
    touch-action: none; transition: transform 0.08s;
  }
  button.card { cursor: pointer; font: inherit; }
  .card.red { color: var(--card-red); }
  .card.faded { opacity: 0.35; }
  .card.selected { border: 3px solid var(--highlight); box-shadow: 0 3px 6px rgba(0, 0, 0, 0.35); }
  .rank { font-family: Georgia, "Times New Roman", "Noto Serif", serif; font-weight: 700; line-height: 1; letter-spacing: -0.04em; }
</style>
