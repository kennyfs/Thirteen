<!-- A suit drawn, not set as text (fonts' symbols differ and come as emoji on some systems): index = suit. -->
<script lang="ts">
  let { suit, size, gap = 0 }: { suit: number; size: number; gap?: number } = $props();
</script>

<svg viewBox="0 0 100 100" width={size} height={size} style:margin-top="{gap}px" aria-hidden="true">
  {#if suit === 0}
    <circle cx="50" cy="27" r="23.5" /><circle cx="25.5" cy="60" r="23.5" /><circle cx="74.5" cy="60" r="23.5" />
    <circle cx="50" cy="55" r="16" /><polygon points="50,55 64,100 36,100" />
  {:else if suit === 1}
    <polygon points="50,0 88,50 50,100 12,50" />
  {:else if suit === 2}
    <path d="M50 95C12 66 0 50 0 30C0 12 13 3 27 3C39 3 47 10 50 20C53 10 61 3 73 3C87 3 100 12 100 30C100 50 88 66 50 95Z" />
  {:else}
    <path d="M50 0C88 28 100 42 100 60C100 76 88 86 74 86C63 86 56 80 53 72L64 100L36 100L47 72C44 80 37 86 26 86C12 86 0 76 0 60C0 42 12 28 50 0Z" />
  {/if}
</svg>

<style>
  svg { display: block; fill: currentColor; flex: none; }
</style>
