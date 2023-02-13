package nodes

import Environment

abstract class Node {
    abstract fun evaluate(env: Environment<Any>): Any
}
