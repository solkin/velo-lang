package compiler.parser

import compiler.nodes.ClassType

class ParserContext {
    val classTypes = mutableMapOf<String, ClassType>()
    
    fun registerClass(name: String, type: ClassType) {
        classTypes[name] = type
    }
    
    fun isClassType(name: String): Boolean {
        return classTypes.containsKey(name)
    }
    
    fun getClassType(name: String): ClassType? {
        return classTypes[name]
    }
}
