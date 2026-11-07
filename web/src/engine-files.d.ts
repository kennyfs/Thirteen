// The one function of node:fs that vite.config.ts uses (there is no @types/node here).
declare module 'node:fs' {
  export function statSync(path: string | URL): { size: number };
}

// vite.config.ts's engineFiles: the engine's and the network's URLs (hashed, in /assets) and the sizes of the two
// large files.
declare module 'virtual:engine-files' {
  const files: { js: string; wasm: string; model: string; wasmSize: number; modelSize: number };
  export default files;
}
