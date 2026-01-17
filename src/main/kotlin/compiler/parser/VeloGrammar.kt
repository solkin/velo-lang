package compiler.parser

import compiler.parser.parselets.literals.*
import compiler.parser.parselets.operators.*
import compiler.parser.parselets.postfix.*
import compiler.parser.parselets.statements.*

object VeloGrammar {
    fun configure(parser: PrattParser) {
        // Literals
        parser.register(TokenType.NUMBER, NumberParselet())
        parser.register(TokenType.STRING, StringParselet())
        parser.register(TokenType.VARIABLE, VariableParselet())
        
        // Keywords
        parser.registerKeyword("true", BoolParselet())
        parser.registerKeyword("false", BoolParselet())
        parser.registerKeyword("null", NullParselet())
        parser.registerKeyword("void", VoidParselet())
        parser.registerKeyword("if", IfParselet())
        parser.registerKeyword("while", WhileParselet())
        parser.registerKeyword("func", FuncParselet())
        parser.registerKeyword("class", ClassParselet())
        parser.registerKeyword("let", LetParselet())
        parser.registerKeyword("ext", ExtParselet())
        parser.registerKeyword("new", NewParselet())
        parser.registerKeyword("native", NativeParselet())
        parser.registerKeyword("include", IncludeParselet())
        
        // Type keywords (will be handled by DefParselet)
        parser.registerKeyword(BYTE, DefParselet())
        parser.registerKeyword(INT, DefParselet())
        parser.registerKeyword(FLOAT, DefParselet())
        parser.registerKeyword(STR, DefParselet())
        parser.registerKeyword(BOOL, DefParselet())
        parser.registerKeyword(TUPLE, DefParselet())
        parser.registerKeyword(ARRAY, DefParselet())
        parser.registerKeyword(DICT, DefParselet())
        parser.registerKeyword(FUNC, DefParselet())
        parser.registerKeyword(PTR, DefParselet())
        // VOID is handled by VoidParselet, not DefParselet
        parser.registerKeyword(ANY, DefParselet())
        
        // Class type parselet (special keyword for class types)
        parser.registerKeyword("__class_type__", ClassTypeParselet())
        
        // Prefix operators
        parser.registerOperator("&", AddressOfParselet())
        parser.registerOperator("*", DerefParselet())
        parser.registerOperator("-", UnaryParselet())
        
        // Infix operators with precedence
        parser.registerInfix("=", Precedence.ASSIGNMENT, AssignParselet())
        parser.registerInfix("+=", Precedence.ASSIGNMENT, AssignParselet())
        parser.registerInfix("-=", Precedence.ASSIGNMENT, AssignParselet())
        parser.registerInfix("*=", Precedence.ASSIGNMENT, AssignParselet())
        parser.registerInfix("/=", Precedence.ASSIGNMENT, AssignParselet())
        parser.registerInfix("%=", Precedence.ASSIGNMENT, AssignParselet())
        
        parser.registerInfix("|", Precedence.OR, LogicalParselet(Precedence.OR))
        parser.registerInfix("&", Precedence.AND, LogicalParselet(Precedence.AND))
        parser.registerInfix("^", Precedence.XOR, LogicalParselet(Precedence.XOR))
        
        parser.registerInfix("<", Precedence.COMPARISON, BinaryParselet(Precedence.COMPARISON))
        parser.registerInfix(">", Precedence.COMPARISON, BinaryParselet(Precedence.COMPARISON))
        parser.registerInfix("<=", Precedence.COMPARISON, BinaryParselet(Precedence.COMPARISON))
        parser.registerInfix(">=", Precedence.COMPARISON, BinaryParselet(Precedence.COMPARISON))
        parser.registerInfix("==", Precedence.COMPARISON, BinaryParselet(Precedence.COMPARISON))
        parser.registerInfix("!=", Precedence.COMPARISON, BinaryParselet(Precedence.COMPARISON))
        
        parser.registerInfix("+", Precedence.ADDITIVE, BinaryParselet(Precedence.ADDITIVE))
        parser.registerInfix("-", Precedence.ADDITIVE, BinaryParselet(Precedence.ADDITIVE))
        
        parser.registerInfix("*", Precedence.MULTIPLICATIVE, BinaryParselet(Precedence.MULTIPLICATIVE))
        parser.registerInfix("/", Precedence.MULTIPLICATIVE, BinaryParselet(Precedence.MULTIPLICATIVE))
        parser.registerInfix("%", Precedence.MULTIPLICATIVE, BinaryParselet(Precedence.MULTIPLICATIVE))
        
        // Postfix operators
        parser.registerInfix("(", Precedence.CALL, CallParselet())
        parser.registerInfix("[", Precedence.INDEX, IndexParselet())
        parser.registerInfix(".", Precedence.PROPERTY, PropertyParselet())
        parser.registerInfix("{", Precedence.CALL, ApplyParselet())
        
        // Prefix punctuation
        parser.register(TokenType.PUNCTUATION, object : compiler.parser.parselets.PrefixParselet {
            override fun parse(parser: compiler.parser.parselets.ExpressionParser, token: compiler.parser.Token): compiler.nodes.Node {
                return when (token.value as? Char) {
                    '(' -> ParenParselet().parse(parser, token)
                    '{' -> BlockParselet().parse(parser, token)
                    else -> throw ParseException("Unexpected punctuation: ${token.value}")
                }
            }
        })
    }
}
