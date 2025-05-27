package vm

import utils.SerializedFrame

interface FrameLoader {
    fun loadFrame(num: Int, parent: Frame?): Frame?
}

class GeneralFrameLoader : FrameLoader {

    private val frames = HashMap<Int, SerializedFrame>()

    constructor(frames: Map<Int, SerializedFrame>) {
        this.frames.putAll(frames)
    }

    override fun loadFrame(num: Int, parent: Frame?): Frame? {
        return frames[num]?.let { frame ->
            Frame(
                pc = 0,
                subs = LifoStack(),
                vars = createVars(vars = frame.vars, parent = parent?.vars),
                ops = frame.ops
            )
        }
    }
}