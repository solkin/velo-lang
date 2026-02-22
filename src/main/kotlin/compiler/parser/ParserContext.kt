package compiler.parser

import compiler.nodes.ClassType
import compiler.nodes.GenericType

class ParserContext {
    val classTypes = mutableMapOf<String, ClassType>()
    private val genericTypes = mutableMapOf<String, GenericType>()
    
    fun registerClass(name: String, type: ClassType) {
        classTypes[name] = type
    }
    
    fun isClassType(name: String): Boolean {
        return classTypes.containsKey(name)
    }
    
    fun getClassType(name: String): ClassType? {
        return classTypes[name]
    }

    fun registerGenericType(name: String) {
        genericTypes[name] = GenericType(name)
    }

    fun isGenericType(name: String): Boolean {
        return genericTypes.containsKey(name)
    }

    fun getGenericType(name: String): GenericType? {
        return genericTypes[name]
    }

    fun saveGenericTypes(): Map<String, GenericType> = HashMap(genericTypes)

    fun restoreGenericTypes(saved: Map<String, GenericType>) {
        genericTypes.clear()
        genericTypes.putAll(saved)
    }
}
