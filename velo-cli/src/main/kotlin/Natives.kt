import core.NativeRegistry

/**
 * The native classes every CLI-run program gets out of the box. An
 * embedding application picks its own set by registering directly on a
 * `VeloCompiler` / `VeloRuntime`.
 */
fun NativeRegistry.registerDefaults(): NativeRegistry = this
    .register(Terminal::class)
    .register(Time::class)
    .register(FileSystem::class)
    .register(Http::class)
    .register(Socket::class)
