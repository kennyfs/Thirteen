/** The glossary dialog (GlossaryDialog, shown by App.svelte over everything): open, at `term` when a link in some
 *  words (Linked.svelte) opened it, at the top from the settings or the tutorial. */
export const glossaryLink = $state<{ open: boolean; term: string | null }>({ open: false, term: null });

export function openGlossary(term: string | null = null) {
  glossaryLink.term = term;
  glossaryLink.open = true;
}
