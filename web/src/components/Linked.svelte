<!--
  Words with the glossary's terms in them linked (lib/glossary.ts; only in a language that has the glossary): a tap
  opens the glossary (GlossaryDialog, shown by App.svelte) at the term. Each term is linked at its first appearance
  in `text` only, so that a paragraph is not all underlines. The app's LinkedText.
-->
<script lang="ts">
  import { linkGlossary, type TextPart } from '../lib/glossary';
  import { openGlossary } from '../lib/glossarylink.svelte';

  let { text }: { text: string } = $props();
  const parts = $derived.by(() => {
    const seen = new Set<string>();
    return linkGlossary(text).map((p): TextPart => {
      if (!p.termId || !seen.has(p.termId)) { if (p.termId) seen.add(p.termId); return p; }
      return { text: p.text };
    });
  });
</script>

{#each parts as part, i (i)}{#if part.termId}<button type="button" class="ref" onclick={(e) => { e.stopPropagation(); openGlossary(part.termId!); }}>{part.text}</button>{:else}{part.text}{/if}{/each}

<style>
  .ref { border: none; background: none; padding: 0; margin: 0; font: inherit; color: var(--primary); text-align: inherit;
    text-decoration: underline; text-underline-offset: 2px; cursor: pointer; }
</style>
