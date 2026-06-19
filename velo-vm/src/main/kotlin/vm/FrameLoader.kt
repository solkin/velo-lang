package vm


import core.SerializedFrame

interface FrameLoader {
    /**
     * Construct a runtime [Frame] from the bytecode frame numbered [num].
     *
     * @param parentVars the [Vars] chain to use as the new frame's parent
     *   (for variable lookup through enclosing scopes). Pass `null` for
     *   the program's main frame, the captured chain from a [vm.records.FuncRecord]
     *   for ordinary function calls, or the receiver instance's vars for
     *   class-method dispatch.
     */
    fun loadFrame(num: Int, parentVars: Vars?): Frame?
}

class GeneralFrameLoader : FrameLoader {

    private val frames = HashMap<Int, SerializedFrame>()

    constructor(frames: Map<Int, SerializedFrame>) {
        this.frames.putAll(frames)
    }

    override fun loadFrame(num: Int, parentVars: Vars?): Frame? {
        return frames[num]?.let { frame ->
            Frame(
                pc = 0,
                subs = LifoStack(),
                vars = createVars(vars = frame.vars, parent = parentVars),
                ops = frame.ops,
                num = num,
            )
        }
    }
}
