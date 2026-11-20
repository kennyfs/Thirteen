<!--
  The glossary's list (lib/glossary.ts; the app's GlossaryList): each term and its definition, whose earlier terms are
  links (each at its first appearance) that jump up to theirs. `focusId`: the term to scroll to and mark.
-->
<script lang="ts">
  import { tick } from 'svelte';
  import { GLOSSARY, linkGlossary, type TextPart } from '../lib/glossary';
  import { t } from '../lib/i18n.svelte';

  let { focusId = null }: { focusId?: string | null } = $props();
  let activeId = $state<string | null>(null);
  const refs: Record<string, HTMLElement | undefined> = {};

  async function jumpTo(id: string, smooth = true) {
    activeId = id;
    await tick();
    const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
    refs[id]?.scrollIntoView({ behavior: smooth && !reduce ? 'smooth' : 'auto', block: 'center' });
  }

  /** Each term linked at its first appearance only, as everywhere (Linked.svelte). */
  function once(parts: TextPart[]): TextPart[] {
    const seen = new Set<string>();
    return parts.map((p) => (!p.termId || !seen.has(p.termId) ? (p.termId && seen.add(p.termId), p) : { text: p.text }));
  }

  $effect(() => {
    const id = focusId;
    if (id) void jumpTo(id, false);
  });
</script>

<ol class="list">
  {#each GLOSSARY as entry, idx (entry.id)}
    <li bind:this={refs[entry.id]} class="item" class:focus={activeId === entry.id} data-term={entry.id}>
      <div class="head"><span class="num label-medium">{idx + 1}</span><span class="term title-small">{t(`glossary_t_${entry.id}`)}</span></div>
      <p class="desc body-medium">
        {#each once(linkGlossary(t(`glossary_d_${entry.id}`), entry.id)) as part, pi (pi)}{#if part.termId}<button type="button" class="ref" onclick={() => void jumpTo(part.termId!)}>{part.text}</button>{:else}{part.text}{/if}{/each}
      </p>
    </li>
  {/each}
</ol>

<style>
  .list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
  .item { padding: 10px 12px; border-radius: 12px; background: var(--surface-container-low); border: 1px solid transparent;
    display: flex; flex-direction: column; gap: 4px; transition: background-color 220ms ease, border-color 220ms ease; }
  .item.focus { background: var(--primary-container); border-color: var(--primary); color: var(--on-primary-container); }
  .head { display: flex; align-items: baseline; gap: 8px; }
  .num { color: var(--on-surface-variant); min-width: 1.6em; font-variant-numeric: tabular-nums; }
  .item.focus .num { color: var(--on-primary-container); opacity: 0.8; }
  .term { font-weight: 600; }
  .desc { margin: 0; line-height: 1.5; text-wrap: pretty; }
  .ref { border: none; background: none; padding: 0; margin: 0; font: inherit; color: var(--primary);
    text-decoration: underline; text-underline-offset: 2px; cursor: pointer; }
  .item.focus .ref { color: var(--on-primary-container); }
  @media (prefers-reduced-motion: reduce) { .item { transition: none; } }
</style>
