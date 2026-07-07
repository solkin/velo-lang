# Velo conformance corpus

Language-neutral behavioural tests. Every case is a `.vel` program plus a
`.out` file with its **exact expected stdout**. Any implementation — the shipping
`velo-vm`, the clean-room `velo-vm2`, a future Go/C VM — must reproduce the `.out`
byte for byte.

This complements [`spec/velo-bytecode.yaml`](../spec/velo-bytecode.yaml): the spec
pins the *format*; this corpus pins the *behaviour* (overflow, truncation,
evaluation order, unicode, closures, …) that codegen alone can't guarantee.

## Layout

```
conformance/
  cases/<category>/<name>.vel   # program (prints deterministic output)
  cases/<category>/<name>.out   # expected stdout, byte-exact
```

`cases/<category>/` holds edge-case, self-checking programs; `cases/golden/`
holds the migrated legacy demo corpus (observational). Both are run identically
on every backend.

A case may start with a marker comment on line 1:

- `# conformance: vm2-only` — run only on velo-vm2 (feature the legacy VM lacks)
- `# conformance: skip` — not run (e.g. host-coupled / non-deterministic)

## Two authoring styles

- **Self-checking** (preferred for semantics): the program compares each result
  against a hard-coded expected value and prints `ok <label>` or `FAIL <label>`.
  The `.out` is a list of `ok` lines, trivial to eyeball. A `FAIL` line means the
  behaviour is wrong — the runner refuses to record such output.
- **Observational** (for features): the program prints results directly; the
  `.out` captures them. Parity across VMs is the guarantee.

## The runner (JVM, both VMs at once)

`velo-cli` test `integration.ConformanceTest` compiles each case once and runs it
on **both** `velo-vm` and `velo-vm2`, asserting each matches `.out` **and** the two
agree with each other.

```
./gradlew :velo-cli:test --tests "integration.ConformanceTest"
```

Missing `.out` files are auto-generated on first run (only when both VMs agree and
no `FAIL` appears); the run then fails asking you to review and re-run, so nothing
is ever silently blessed. To re-record a case, delete its `.out`.

Non-JVM implementations run the same corpus with their own thin runner (read
`<name>.vel`, execute, diff against `<name>.out`).
