package compiler.parser.parselets.statements

import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.VoidNode
import compiler.parser.PrattParser
import compiler.parser.StringInput
import compiler.parser.Token
import compiler.parser.TokenStream
import compiler.parser.TokenType
import compiler.parser.VeloGrammar
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet

/**
 * `import "path"` — brings another module's top-level declarations into scope
 * (the `.vel` extension is optional; `std/` names resolve to the standard
 * library, other paths resolve **relative to this file**). Each module is
 * loaded at most once per compilation.
 *
 * The module is parsed **in isolation** — its own [TokenStream] over its source,
 * sharing this parse's [context]. It is not spliced into the current token
 * stream, so statement termination (newlines/`;`) in the two files never
 * interfere. Its statements are returned ahead of the importing file's, so
 * declare-before-use holds.
 */
class ImportParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val path = parser.peek()?.takeIf { it.type == TokenType.STRING }
            ?: throw IllegalStateException("import expects a module path string, e.g. import \"std/map\"")
        parser.consume(TokenType.STRING)

        // Optional `import "path" as ns` — `as` is a contextual word, not a keyword.
        var namespace: String? = null
        val next = parser.peek()
        if (next?.type == TokenType.VARIABLE && next.value == "as") {
            parser.consume(TokenType.VARIABLE)
            namespace = parser.consume(TokenType.VARIABLE).value as String
        }
        // The statement terminator after the path (explicit `;` or an inserted
        // newline) is consumed by the statement loop, like any other statement.

        val module = parser.depLoader.module(name = path.value as String, fromDir = parser.currentDir)
            ?: return VoidNode // already imported this compilation

        val ctx = parser.context
        val savedNamespace = ctx.currentNamespace
        val savedLocals = HashSet(ctx.currentModuleLocals)
        val savedMangle = ctx.mangleDeclarations
        ctx.currentNamespace = namespace // null for a plain import
        ctx.currentModuleLocals.clear()
        ctx.mangleDeclarations = true

        val sub = PrattParser(TokenStream(StringInput(module.text)), ctx, parser.depLoader, currentDir = module.dir)
        VeloGrammar.configure(sub)

        val statements = mutableListOf<Node>()
        while (!sub.eof()) {
            statements.add(sub.parseExpression())
            if (!sub.eof()) sub.consume(TokenType.PUNCTUATION, ';')
        }

        if (namespace != null) ctx.registerNamespace(namespace)
        ctx.currentNamespace = savedNamespace
        ctx.currentModuleLocals.clear()
        ctx.currentModuleLocals.addAll(savedLocals)
        ctx.mangleDeclarations = savedMangle
        return ProgramNode(prog = statements)
    }
}
