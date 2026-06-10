// velo-vm — the execution engine: interpreter, records, memory, actors,
// and the embedding API (VeloRuntime). Depends only on the core contract;
// no compiler on the classpath — this is what a client application embeds
// to run pre-compiled .vbc programs.

dependencies {
    api(project(":velo-core"))
}
