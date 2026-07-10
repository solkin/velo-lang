package org.velo.android.engine

import compiler.VeloCompiler
import core.NativeRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class GameOfLifeSampleTest {
    class Probe {
        fun record(length: Int, sum: Int) {
            observedLength = length
            observedSum = sum
        }
    }

    @Test
    fun expandingFieldInitializesEveryNewCell() {
        val sample = sequenceOf(
            File("samples/game-of-life/program.vel"),
            File("velo-android/samples/game-of-life/program.vel"),
        ).first { it.isFile }.readText()
        val actorStart = sample.indexOf("actor class LifeEngine")
        val actorEnd = sample.indexOf("actor class TickTimer", actorStart)
        require(actorStart >= 0 && actorEnd > actorStart)

        val source = buildString {
            append(sample.substring(actorStart, actorEnd))
            appendLine("Probe probe = new Probe();")
            appendLine("actor[LifeEngine] engine = new LifeEngine(32, 4);")
            appendLine("await async engine.clear();")
            appendLine("future[array[int]] first = async engine.resize(33, 4);")
            appendLine("future[array[int]] latest = async engine.resize(56, 6);")
            appendLine("await first;")
            appendLine("array[int] snap = await latest;")
            appendLine("int sum = 0;")
            appendLine("int i = 1;")
            appendLine("while (i < snap.len()) { sum += snap[i]; i += 1; };")
            appendLine("probe.record(snap.len(), sum);")
        }
        val file = File.createTempFile("game-of-life-resize", ".vel").apply { writeText(source) }
        val registry = NativeRegistry().register(Probe::class)
        val program = try {
            VeloCompiler(registry).compile(file.path) ?: error("compile failed")
        } finally {
            file.delete()
        }

        observedLength = -1
        observedSum = -1
        vm3.VeloRuntime(registry)
            .actorPlacement { AndroidActorDispatcherFactory(parallelism = 1) }
            .run(program)

        assertEquals(56 * 6 + 1, observedLength)
        assertEquals(0, observedSum)
    }

    companion object {
        @Volatile private var observedLength = -1
        @Volatile private var observedSum = -1
    }
}
