package integration

import Terminal
import compiler.VeloCompiler
import core.NativeRegistry
import core.SerializedProgram
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

/**
 * Differential fuzzer (roadmap A0): generate random but **type-correct** Velo
 * programs and assert the legacy `vm` and the clean-room `vm2` backends agree —
 * identical stdout, or both fail the same way. The legacy `vm` is the oracle.
 *
 * Programs are drawn from the arithmetic / numeric-promotion / loop / closure
 * subset where the two VMs have historically diverged (cross-type `==`,
 * left-biased int/long promotion — see the conformance corpus). Only the
 * generator guarantees compilable output: a program the compiler rejects is
 * skipped (never a VM bug). A real divergence prints the seed and the exact
 * source so it reproduces deterministically.
 *
 * Deterministic by construction: fixed master seed, no host natives beyond
 * `Terminal`, no division by zero, all loops counter-bounded with a read-only
 * induction variable. Each run is wall-clock-capped, so a backend that hangs on
 * some program is reported as a divergence rather than hanging the suite. Tune
 * with `-Dvelo.fuzz.count=N` / `-Dvelo.fuzz.seed=S`.
 */
class Vm2FuzzTest {

    private val pool = Executors.newCachedThreadPool { r ->
        Thread(r, "fuzz-run").apply { isDaemon = true }
    }

    private fun registry() = NativeRegistry().register(Terminal::class)

    private sealed class Outcome {
        data class Done(val out: String, val err: Throwable?) : Outcome()
        object Hung : Outcome()
    }

    /** Run [block] under a wall-clock cap, capturing stdout. */
    private fun timedRun(timeoutMs: Long, block: () -> Unit): Outcome {
        val baos = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(baos))
        try {
            val f = pool.submit { block() }
            return try {
                f.get(timeoutMs, TimeUnit.MILLISECONDS)
                Outcome.Done(strip(baos.toString()), null)
            } catch (te: TimeoutException) {
                f.cancel(true)
                Outcome.Hung
            } catch (ee: ExecutionException) {
                Outcome.Done(strip(baos.toString()), ee.cause ?: ee)
            }
        } finally {
            System.setOut(old)
        }
    }

    /** Drop VM banner/status lines so only program output is compared. */
    private fun strip(out: String): String = out.lineSequence()
        .filterNot { line ->
            line.startsWith("Program ended") || line.startsWith("Program halted") ||
                line.startsWith("VM stopped") || line.startsWith("Parsed in") ||
                line.startsWith("Compiled in") || line.startsWith("Bytecode ") ||
                line.startsWith("✓ Program") || line.startsWith("⏹ Program")
        }
        .joinToString("\n")
        .trimEnd('\n')

    @Test
    fun `random programs produce identical output on the legacy VM and vm2`() {
        val count = System.getProperty("velo.fuzz.count")?.toInt() ?: 400
        val masterSeed = System.getProperty("velo.fuzz.seed")?.toLong() ?: 0xC0FFEEL
        val tmp = File.createTempFile("velo-fuzz", ".vel")
        tmp.deleteOnExit()

        var compiled = 0
        var skippedCompile = 0
        var bothFailed = 0
        val diverged = mutableListOf<String>()

        for (i in 0 until count) {
            val seed = masterSeed + i
            val src = Generator(Random(seed)).program()
            tmp.writeText(src)

            // Compile quietly: a rejected program is skipped (a generator gap,
            // never a VM bug), so its compiler diagnostics shouldn't pollute output.
            val program: SerializedProgram? = run {
                val baos = ByteArrayOutputStream(); val o = System.out; val e = System.err
                System.setOut(PrintStream(baos)); System.setErr(PrintStream(baos))
                try { VeloCompiler(registry()).compile(tmp.path) }
                finally { System.setOut(o); System.setErr(e) }
            }
            if (program == null) { skippedCompile++; continue }
            compiled++

            val legacy = timedRun(10_000) { vm.VeloRuntime(registry()).run(program) }
            val fresh = timedRun(10_000) { vm2.VeloRuntime(registry()).run(program) }
            val compact = timedRun(10_000) { vm3.VeloRuntime(registry()).run(program) }

            val legacyVsFresh = sameOutcome(legacy, fresh)
            val legacyVsCompact = sameOutcome(legacy, compact)
            val bad = !(legacyVsFresh && legacyVsCompact)
            if (!bad && listOf(legacy, fresh, compact).all { it is Outcome.Done && it.err != null }) {
                bothFailed++
            } else if (!bad && listOf(legacy, fresh, compact).all { it is Outcome.Hung }) {
                bothFailed++
            }

            if (bad) diverged += report(seed, src, legacy, fresh, compact)
        }

        println("fuzz: compiled=$compiled skippedCompile=$skippedCompile bothFailed=$bothFailed diverged=${diverged.size}")
        check(compiled >= count / 2) {
            "generator produced too few compilable programs ($compiled/$count) — the generator is broken, not the VMs"
        }
        if (diverged.isNotEmpty()) {
            fail("${diverged.size} program(s) diverged between vm, vm2 and vm3:\n\n" +
                diverged.take(8).joinToString("\n" + "=".repeat(60) + "\n"))
        }
    }

    private fun sameOutcome(a: Outcome, b: Outcome): Boolean = when {
                // Both backends failed/hung identically-in-kind on a compilable
                // program: not a divergence. Constructed programs shouldn't reach
                // here — count it so a generator regression stays visible.
                a is Outcome.Hung && b is Outcome.Hung -> true
                a is Outcome.Done && b is Outcome.Done &&
                    a.err != null && b.err != null -> {
                    // Both raised: not a kind-divergence, but the stdout printed
                    // before the fault must still match (error *text* is not compared).
                    a.out == b.out
                }
                a is Outcome.Done && b is Outcome.Done ->
                    a.err == null && b.err == null && a.out == b.out
                else -> false
            }

    private fun describe(o: Outcome) = when (o) {
        is Outcome.Hung -> "HUNG"
        is Outcome.Done -> o.err?.let { "RAISED ${it.javaClass.simpleName}: ${it.message}" } ?: "ok"
    }

    private fun report(seed: Long, src: String, legacy: Outcome, fresh: Outcome, compact: Outcome) = buildString {
        appendLine("seed=$seed")
        appendLine("--- source ---"); appendLine(src)
        appendLine("--- legacy (vm) ${describe(legacy)} ---")
        if (legacy is Outcome.Done) appendLine(legacy.out)
        appendLine("--- vm2 ${describe(fresh)} ---")
        if (fresh is Outcome.Done) appendLine(fresh.out)
        appendLine("--- vm3 ${describe(compact)} ---")
        if (compact is Outcome.Done) appendLine(compact.out)
    }
}

/** Numeric-promotion rank; BOOL is non-numeric (rank -1). */
private enum class Ty(val kw: String, val rank: Int) {
    BYTE("byte", 0), INT("int", 1), LONG("long", 2), FLOAT("float", 3), BOOL("bool", -1);

    val numeric get() = rank >= 0
}

private val NUM = listOf(Ty.BYTE, Ty.INT, Ty.LONG, Ty.FLOAT)

private data class V(val name: String, val ty: Ty, val mutable: Boolean = true)
private data class Fn(val name: String, val params: List<Ty>, val ret: Ty)
private data class Method(val name: String, val params: List<Ty>, val ret: Ty, val callback: Boolean = false)
private data class ActorDef(val name: String, val ctor: List<Ty>, val methods: List<Method>)

/**
 * Generates one self-contained, type-correct Velo program. Type-directed:
 * [expr] always yields an expression whose type implicitly coerces to the
 * requested type, so the compiler never rejects a well-formed generator run.
 * A lexical [scope] stack models block scoping (truncated on block exit); the
 * top-level survivors are what the program prints, in declaration order. Loop
 * induction variables are inserted read-only (`mutable=false`) so a generated
 * assignment can never reset a counter into an infinite loop.
 */
private class Generator(private val rnd: Random) {
    private val sb = StringBuilder()
    private val scope = ArrayList<V>()
    private val fns = ArrayList<Fn>()
    private val actors = ArrayList<ActorDef>()
    private val actorVars = ArrayList<Pair<String, ActorDef>>()
    private var counter = 0
    private var indent = 0

    private fun fresh(p: String) = "$p${counter++}"
    private fun line(s: String) { sb.append("    ".repeat(indent)).append(s).append('\n') }
    private fun chance(p: Double) = rnd.nextDouble() < p
    private fun <T> pick(xs: List<T>) = xs[rnd.nextInt(xs.size)]
    private fun <T> List<T>.randomOrNullBy(): T? = if (isEmpty()) null else this[rnd.nextInt(size)]

    fun program(): String {
        // In error mode the program prints all its values, then faults. Both VMs
        // now propagate an uncaught error from run() (contract reconciled), so
        // this asserts they raise alike after an identical stdout prefix.
        val errorMode = chance(0.12)
        line("Terminal term = new Terminal();")
        repeat(rnd.nextInt(0, 2)) { actorDecl() }
        repeat(rnd.nextInt(0, 3)) { topLevelFunc() }
        // A few globals to guarantee observable, printable output.
        repeat(rnd.nextInt(3, 6)) { declStmt() }
        for (a in actors) if (chance(0.85)) spawnActor(a)
        repeat(rnd.nextInt(4, 10)) { stmt(depth = 2) }
        // Print every surviving top-level variable, in declaration order.
        for (v in scope) line("term.println((${v.name}).str());")
        if (errorMode) emitFault()
        return sb.toString()
    }

    /** A guaranteed fault — an actor method reading out of bounds (surfaces
     *  through await), else a plain out-of-bounds read on main. */
    private fun emitFault() {
        val a = actorVars.randomOrNullBy()
        if (a != null) {
            line("term.println((await async ${a.first}.boom()).str());")
        } else {
            line("array[int] boom = new array[int]{};")
            line("term.println((boom[0]).str());")
        }
    }

    // ---- statements -------------------------------------------------------

    private fun stmt(depth: Int) {
        when (rnd.nextInt(if (depth <= 0) 2 else 8)) {
            0 -> declStmt()
            1 -> assignStmt()
            2 -> ifStmt(depth)
            3 -> whileStmt(depth)
            4 -> forStmt(depth)
            5 -> closureLoopStmt()
            6 -> if (actorVars.isNotEmpty() && depth >= 2) actorCallStmt() else assignStmt()
            else -> assignStmt()
        }
    }

    private fun declStmt() {
        // Bias toward the wide numeric types where promotion bugs live.
        val ty = pick(listOf(Ty.INT, Ty.INT, Ty.LONG, Ty.LONG, Ty.FLOAT, Ty.FLOAT, Ty.BYTE, Ty.BOOL))
        val name = fresh("v")
        line("${ty.kw} $name = ${expr(ty, 3)};")
        scope += V(name, ty)
    }

    private fun assignStmt() {
        val target = scope.filter { it.mutable }.randomOrNullBy() ?: return
        // Compound assignment only where the op result coerces back to the type.
        if (target.ty in listOf(Ty.INT, Ty.LONG, Ty.FLOAT) && chance(0.4)) {
            val op = pick(listOf("+=", "-=", "*="))
            line("${target.name} $op ${expr(target.ty, 2)};")
        } else {
            line("${target.name} = ${expr(target.ty, 2)};")
        }
    }

    private fun ifStmt(depth: Int) {
        line("if (${boolExpr(2)}) {")
        block { repeat(rnd.nextInt(1, 3)) { stmt(depth - 1) } }
        if (chance(0.5)) {
            line("} else {")
            block { repeat(rnd.nextInt(1, 3)) { stmt(depth - 1) } }
        }
        line("};")
    }

    private fun whileStmt(depth: Int) {
        val i = fresh("w")
        val n = rnd.nextInt(2, 6)
        line("int $i = 0;")
        line("while ($i < $n) {")
        block {
            scope += V(i, Ty.INT, mutable = false) // readable, never an assign target
            repeat(rnd.nextInt(1, 3)) { assignStmt() }
            line("$i += 1;")
        }
        line("};")
    }

    private fun forStmt(depth: Int) {
        val k = fresh("k")
        val n = rnd.nextInt(2, 6)
        line("for $k in 0..$n {")
        block {
            scope += V(k, Ty.INT, mutable = false)
            repeat(rnd.nextInt(1, 3)) { assignStmt() }
        }
        line("};")
    }

    /**
     * Per-iteration closure capture — the historical "2,2,2" scoping trap. Each
     * iteration builds a closure over the loop variable; they are collected and
     * only invoked after the loop, so a broken per-iteration scope diverges.
     */
    private fun closureLoopStmt() {
        val n = rnd.nextInt(2, 4)
        val arr = fresh("fns")
        val k = fresh("k")
        val acc = fresh("acc")
        val ci = fresh("ci")
        val bias = rnd.nextInt(0, 100)
        line("array[func[int]] $arr = new array[func[int]]($n);")
        line("for $k in 0..$n {")
        indent++; line("$arr[$k] = func() int { return $k + $bias; };"); indent--
        line("};")
        line("int $acc = 0;")
        line("int $ci = 0;")
        line("while ($ci < $n) {")
        indent++; line("$acc += $arr[$ci]();"); line("$ci += 1;"); indent--
        line("};")
        scope += V(acc, Ty.INT)
    }

    private inline fun block(body: () -> Unit) {
        val mark = scope.size
        indent++
        body()
        indent--
        while (scope.size > mark) scope.removeAt(scope.size - 1)
    }

    // ---- top-level functions ---------------------------------------------

    private fun topLevelFunc() {
        val name = fresh("f")
        val params = List(rnd.nextInt(0, 3)) { pick(NUM) }
        val ret = pick(NUM)
        val pnames = params.mapIndexed { idx, t -> "p$idx" to t }
        line("func $name(${pnames.joinToString(", ") { "${it.second.kw} ${it.first}" }}) ${ret.kw} {")
        // Restrict the body to the parameters: keeps functions pure & terminating.
        val saved = ArrayList(scope)
        scope.clear()
        pnames.forEach { scope += V(it.first, it.second) }
        indent++
        line("return ${expr(ret, 3)};")
        indent--
        scope.clear(); scope.addAll(saved)
        line("};")
        fns += Fn(name, params, ret) // registered after body ⇒ non-recursive
    }

    // ---- actors -----------------------------------------------------------

    /**
     * `actor class` with numeric fields and value-returning methods only — all
     * signatures are primitives, which are transferable across the actor
     * boundary, so the compiler accepts them. Methods mutate fields and return
     * a number; they never print and never fault, so stdout stays a pure
     * function of the (serialized) call order.
     */
    private fun actorDecl() {
        val name = fresh("A")
        val ctor = List(rnd.nextInt(0, 3)) { pick(NUM) }
        val cnames = ctor.mapIndexed { i, t -> "c$i" to t }
        line("actor class $name(${cnames.joinToString(", ") { "${it.second.kw} ${it.first}" }}) {")
        indent++
        val fields = ArrayList<V>()
        repeat(rnd.nextInt(1, 4)) {
            val ft = pick(NUM)
            val init = cnames.filter { it.second.rank <= ft.rank }.randomOrNullBy()?.first ?: literal(ft)
            line("${ft.kw} fld${fields.size} = $init;")
            fields += V("fld${fields.size}", ft)
        }
        val methods = ArrayList<Method>()
        repeat(rnd.nextInt(1, 4)) {
            val mparams = List(rnd.nextInt(0, 3)) { pick(NUM) }
            val pnames = mparams.mapIndexed { i, t -> "q$i" to t }
            // A quarter of methods take a void callback they fire once — exercises
            // the reentrant callback path (VEL-11), where vm and vm2 differ inside.
            val callback = chance(0.25)
            val ret = if (callback) Ty.INT else pick(NUM)
            val extra = if (callback) (if (pnames.isEmpty()) "" else ", ") + "func[(int) void] cb" else ""
            line("func m${methods.size}(${pnames.joinToString(", ") { "${it.second.kw} ${it.first}" }}$extra) ${if (callback) "void" else ret.kw} {")
            // Body sees the fields (mutable) and the params (read-only). fns is
            // still empty here (actors are emitted first), so no top-level calls.
            val saved = ArrayList(scope); scope.clear()
            fields.forEach { scope += it }
            pnames.forEach { scope += V(it.first, it.second, mutable = false) }
            indent++
            repeat(rnd.nextInt(0, 3)) { assignStmt() }
            if (callback) { line("cb(${numExpr(Ty.INT, 2)});"); line("void") }
            else line("return ${expr(ret, 3)};")
            indent--
            scope.clear(); scope.addAll(saved)
            line("};")
            methods += Method("m${methods.size}", mparams, ret, callback)
        }
        // A faulting method (out-of-bounds), never in `methods` so normal calls
        // skip it — invoked only in error mode to check both VMs raise alike.
        line("func boom() int { array[int] xs = new array[int]{}; return xs[0]; };")
        indent--
        line("};")
        actors += ActorDef(name, ctor, methods)
    }

    private fun spawnActor(a: ActorDef) {
        val vn = fresh("act")
        line("actor[${a.name}] $vn = new ${a.name}(${a.ctor.joinToString(", ") { exactArg(it, 2) }});")
        actorVars += vn to a
    }

    /**
     * A cross-actor call whose result main binds and later prints. Mostly the
     * `await async` shorthand; sometimes the explicit `future` form. Both are
     * deterministic — main awaits (and prints) in program order regardless of
     * how the scheduler interleaves the actors underneath.
     */
    private fun actorCallStmt() {
        val (vn, def) = actorVars.randomOrNullBy() ?: return
        val m = def.methods.randomOrNullBy() ?: return
        if (m.callback) {
            // The fired callback prints on main while it is parked in `await` —
            // deterministic in order, but a reentrancy divergence would show here.
            val cx = fresh("cx")
            val lead = m.params.joinToString("") { exactArg(it, 2) + ", " }
            line("await async $vn.${m.name}(${lead}func(int $cx) void { term.println(\"cb \".con($cx.str())); void });")
            return
        }
        val args = m.params.joinToString(", ") { exactArg(it, 2) }
        val rn = fresh("r")
        if (chance(0.3)) {
            val fut = fresh("fut")
            line("future[${m.ret.kw}] $fut = async $vn.${m.name}($args);")
            line("${m.ret.kw} $rn = await $fut;")
        } else {
            line("${m.ret.kw} $rn = await async $vn.${m.name}($args);")
        }
        scope += V(rn, m.ret)
    }

    // ---- expressions ------------------------------------------------------

    private fun expr(target: Ty, depth: Int): String =
        if (target == Ty.BOOL) boolExpr(depth) else numExpr(target, depth)

    private fun numExpr(target: Ty, depth: Int): String {
        if (depth <= 0) return pick(listOf(literal(target), varOrLiteral(target)))
        return when (rnd.nextInt(9)) {
            0, 1 -> literal(target)
            2 -> varOrLiteral(target)
            3 -> if (target == Ty.BYTE) convertTo(target, depth) else binary(target, depth)
            4 -> if (target == Ty.BYTE) literal(target) else divmod(target, depth)
            5 -> if (target == Ty.BYTE) literal(target) else "(-(${numExpr(target, depth - 1)}))"
            6 -> convertTo(target, depth)
            7 -> callExpr(target, depth) ?: literal(target)
            else -> if (target == Ty.INT || target == Ty.LONG) shiftExpr(target, depth) else numExpr(target, depth - 1)
        }
    }

    /** `a OP b` with both operands of rank ≤ target ⇒ result coerces to target. */
    private fun binary(target: Ty, depth: Int): String {
        val op = pick(listOf("+", "-", "*"))
        return "(${numExpr(loRank(target), depth - 1)} $op ${numExpr(loRank(target), depth - 1)})"
    }

    private fun divmod(target: Ty, depth: Int): String {
        val op = pick(listOf("/", "%"))
        return "(${numExpr(loRank(target), depth - 1)} $op ${nonZeroLiteral(target)})"
    }

    private fun shiftExpr(target: Ty, depth: Int): String {
        val m = if (target == Ty.LONG) "shl" else pick(listOf("shl", "shr"))
        // Cast the receiver to int/long first: `.shl`/`.shr` exist only there,
        // and numExpr(target) may still be byte-typed (a byte var/leaf).
        return "((${numExpr(target, depth - 1)}).${target.kw}().$m(${rnd.nextInt(0, 16)}))"
    }

    /** Explicit conversion — the only way to reach BYTE and to exercise narrowing. */
    private fun convertTo(target: Ty, depth: Int): String =
        "((${numExpr(pick(NUM), depth - 1)}).${target.kw}())"

    /**
     * An argument of *exactly* [t]. Actor call/constructor boundaries do NOT
     * apply implicit widening (a value is marshalled by its concrete type), so
     * every crossing argument is pinned to the parameter type with a conversion.
     */
    private fun exactArg(t: Ty, depth: Int): String = "((${numExpr(t, depth)}).${t.kw}())"

    private fun callExpr(target: Ty, depth: Int): String? {
        val f = fns.filter { it.ret.rank in 0..target.rank }.randomOrNullBy() ?: return null
        return "${f.name}(${f.params.joinToString(", ") { expr(it, depth - 1) }})"
    }

    private fun boolExpr(depth: Int): String {
        if (depth <= 0) return pick(listOf("true", "false"))
        return when (rnd.nextInt(6)) {
            0 -> pick(listOf("true", "false"))
            1 -> scope.filter { it.ty == Ty.BOOL }.randomOrNullBy()?.name ?: comparison(depth)
            2, 3 -> comparison(depth)
            4 -> "(${boolExpr(depth - 1)} ${pick(listOf("&&", "||"))} ${boolExpr(depth - 1)})"
            else -> "(!(${boolExpr(depth - 1)}))"
        }
    }

    /** Compare two independently-typed numeric operands — exercises cross-type ==/<. */
    private fun comparison(depth: Int): String {
        val op = pick(listOf("<", ">", "<=", ">=", "==", "!="))
        return "(${numExpr(pick(NUM), depth - 1)} $op ${numExpr(pick(NUM), depth - 1)})"
    }

    // ---- leaves -----------------------------------------------------------

    private fun loRank(target: Ty): Ty = pick(NUM.filter { it.rank <= target.rank })

    private fun varOrLiteral(target: Ty): String =
        scope.filter { it.ty.numeric && it.ty.rank <= target.rank }.randomOrNullBy()?.name ?: literal(target)

    private fun literal(target: Ty): String = when (target) {
        // Always explicit: a bare int literal narrows to byte only at init, not
        // on reassignment — `(n).byte()` is valid byte in every context.
        Ty.BYTE -> "((${rnd.nextInt(-128, 128)}).byte())"
        Ty.INT -> pick(listOf(rnd.nextInt().toString(), rnd.nextInt(-100, 100).toString(), "0", "1", "-1"))
        Ty.LONG -> if (chance(0.5)) (rnd.nextLong() or (1L shl 40)).toString() else "((${rnd.nextInt(-100, 100)}).long())"
        Ty.FLOAT -> floatLiteral()
        Ty.BOOL -> pick(listOf("true", "false"))
    }

    private fun nonZeroLiteral(target: Ty): String = when (target) {
        Ty.LONG -> "((${signedSmall()}).long())"
        Ty.FLOAT -> floatLiteral(nonZero = true)
        else -> signedSmall().toString()
    }

    private fun signedSmall() = rnd.nextInt(1, 1000) * (if (chance(0.5)) 1 else -1)

    private fun floatLiteral(nonZero: Boolean = false): String {
        val whole = rnd.nextInt(if (nonZero) 1 else 0, 1000)
        val frac = rnd.nextInt(0, 100).toString().padStart(2, '0')
        return "${if (chance(0.5)) "-" else ""}$whole.$frac"
    }
}
