# Grammar & Operator Precedence

A formal reference for Velo's surface syntax. The grammar is written in EBNF:

- `=` defines a rule, terminated by `;`
- `|` alternatives, `[ x ]` optional, `{ x }` zero or more, `( x )` grouping
- `"lit"` a literal token, `'c'` a literal character
- lexical rules are `UPPERCASE`, grammar rules are `lowerCamel`

Expression structure (which operator binds first) is governed by the
[precedence table](#operator-precedence) at the end, not by the EBNF alone.

## Lexical structure

```ebnf
program        = { statement [ terminator ] } ;
terminator     = NEWLINE | ";" ;          (* a newline ends a statement; ";" only
                                             needed to separate statements on one line *)
comment        = "#" { any-char-except-newline } NEWLINE ;

IDENT          = ( LETTER | "_" ) { LETTER | DIGIT | "_" } ;
LETTER         = "a".."z" | "A".."Z" ;
DIGIT          = "0".."9" ;

NUMBER         = INT_LIT | FLOAT_LIT | BYTE_LIT | HEX_LIT | BIN_LIT ;
INT_LIT        = DIGIT { DIGIT | "_" } ;
FLOAT_LIT      = DIGIT { DIGIT | "_" } ( "." DIGIT { DIGIT } [ "f" ] | "f" ) ;
BYTE_LIT       = DIGIT { DIGIT } "y" ;
HEX_LIT        = "0x" HEXDIGIT { HEXDIGIT | "_" } ;
BIN_LIT        = "0b" ( "0" | "1" ) { "0" | "1" | "_" } ;
CHAR_LIT       = "'" ( CHARACTER | ESCAPE ) "'" ;           (* value is its code point *)

STRING         = '"' { STRCHAR | ESCAPE | interpolation } '"' ;
interpolation  = "$" IDENT | "${" expression "}" ;         (* write \$ for a literal $ *)
ESCAPE         = "\" ( "n" | "t" | "\" | '"' | "$" | "'" ) ;
```

## Declarations & statements

A statement is any of these forms or a bare `expression`.

```ebnf
statement      = import
               | typedDecl | letDecl
               | funcDecl  | extDecl  | operatorDecl
               | classDecl | dataDecl | actorDecl | interfaceDecl
               | if | while | for | return | "break" | "continue"
               | expression ;

import         = "import" STRING ;   (* .vel optional; std/ = stdlib; else relative to file *)

typedDecl      = type IDENT [ "=" expression ] ;           (* explicit type; mutable *)
letDecl        = "let" IDENT "=" expression ;              (* inferred type; immutable *)

funcDecl       = "func" [ IDENT ] [ typeParams ] "(" params ")" type block ;
extDecl        = "ext" "(" param ")" IDENT "(" params ")" type block ;
operatorDecl   = "operator" operatorName "(" params ")" type block ;
operatorName   = OPERATOR | "[" "]" [ "=" ] ;              (* e.g. +, ==, [], []= *)

classDecl      = "class"  IDENT [ typeParams ] "(" params ")" [ ":" typeList ] block ;
dataDecl       = "data" classDecl ;                        (* immutable value type *)
actorDecl      = "actor" classDecl ;                       (* concurrent, message-driven *)
interfaceDecl  = "interface" IDENT block ;                 (* body holds method signatures *)

if             = "if" [ "(" ] expression [ ")" ]
                 ( block [ "else" ( block | if ) ]
                 | "then" expression "else" expression ) ; (* the then/else form is an expression *)
while          = "while" [ "(" ] expression [ ")" ] block ;
for            = "for" IDENT "in" ( expression ".." expression   (* range, end exclusive *)
                                  | expression ) block ;         (* array iteration       *)
return         = "return" [ expression ] ;

block          = "{" { statement [ terminator ] } "}" ;

typeParams     = "[" typeParam { "," typeParam } "]" ;
typeParam      = IDENT [ ":" type ] ;                      (* interface-bounded generic *)
params         = [ param { "," param } ] ;
param          = type IDENT [ "=" expression ] ;           (* trailing default value    *)
typeList       = type { "," type } ;
```

## Types

```ebnf
type           = "byte" | "int" | "float" | "str" | "bool"
               | "void" | "any" | "Self"
               | "array" "[" type "]"
               | "tuple" "[" type { "," type } "]"
               | "dict"  "[" type ":" type "]"           (* sugar for Map[K, V] *)
               | "ptr"   "[" type "]"
               | "actor" "[" type "]"
               | "future" "[" type "]"
               | funcType
               | IDENT [ "[" type { "," type } "]" ] ;   (* class / interface, maybe generic *)

funcType       = "func" "[" ( "(" [ typeList ] ")" type   (* full signature: func[(int,int) int] *)
                            | type ) "]" ;                (* loose form:     func[int]           *)
```

## Expressions

Velo is expression-oriented: `if`, `while`, `for` and blocks are parsed as
expressions (so `let x = if c then a else b` is valid). The statement/expression
split above is only for readability.

```ebnf
expression     = assignment ;
assignment     = binary [ assignOp expression ] ;          (* right-associative *)
assignOp       = "=" | "+=" | "-=" | "*=" | "/=" | "%=" ;

binary         = unary { binOp unary } ;                   (* grouped per precedence table *)
binOp          = "||" | "|" | "&&" | "&" | "^"
               | "==" | "!=" | "<" | ">" | "<=" | ">="
               | "+" | "-" | "*" | "/" | "%" ;

unary          = [ "-" | "!" | "&" | "*" ] postfix ;       (* neg, not, address-of, deref *)

postfix        = primary { call | index | property | apply } ;
call           = "(" [ argList ] ")" ;
index          = "[" expression "]" ;
property       = "." ( IDENT | INT_LIT ) [ "(" [ argList ] ")" ] ;
                 (* .field / .N  → bare access;  .method(...) / .conv()  → call *)
apply          = "{" { statement [ terminator ] } "}" ;    (* apply block (e.g. UI builders) *)
argList        = expression { "," expression } ;

primary        = NUMBER | STRING | CHAR_LIT
               | "true" | "false" | "null" | "void"
               | IDENT
               | lambda
               | newExpr
               | asyncExpr | awaitExpr
               | if | while | for            (* control forms are expressions too *)
               | "(" expression ")" ;

lambda         = "func" [ typeParams ] "(" params ")" type block ;
newExpr        = "new" ( "array" "[" type "]" ( "(" expression ")" | "{" [ argList ] "}" )
                       | "tuple" "(" argList ")"
                       | "dict" "[" type ":" type "]" "{" [ dictEntries ] "}"
                       | "ptr" "[" type "]" "(" expression ")"
                       | IDENT [ "[" typeList "]" ] "(" [ argList ] ")" ) ;
dictEntries    = dictEntry { "," dictEntry } ;
dictEntry      = expression ":" expression ;
asyncExpr      = "async" postfix "." IDENT "(" [ argList ] ")" ;
awaitExpr      = "await" expression ;
```

## Operator precedence

From **loosest** (binds last) to **tightest** (binds first). Higher rows are
evaluated later. Example: `a + b * c` parses as `a + (b * c)` (multiplicative
binds tighter than additive); `a == b && c == d` parses as `(a == b) && (c == d)`.

| Level | Operators | Kind | Associativity |
|------:|-----------|------|---------------|
| 1 | `=` `+=` `-=` `*=` `/=` `%=` | assignment | right |
| 2 | `\|\|` `\|` | logical-or / bitwise-or | left |
| 3 | `&&` `&` | logical-and / bitwise-and | left |
| 4 | `^` | bitwise-xor | left |
| 7 | `==` `!=` `<` `>` `<=` `>=` | comparison | left |
| 10 | `+` `-` | additive | left |
| 20 | `*` `/` `%` | multiplicative | left |
| 30 | `(…)` `[…]` `.` `{…}` | call, index, property, apply | left |
| 40 | `-` `!` `&` `*` (prefix) | unary: negate, not, address-of, deref | right |

Notes:

- **`&&`/`||` are logical with short-circuit; `&`/`|`/`^` are bitwise** on ints
  (and `&`/`|` also short-circuit on bools, kept as aliases). Prefer `&&`/`||`.
- **`..`** (range) is not a general operator — it appears only in `for i in a..b`.
- There are **no shift operators**; use `x.shl(n)` / `x.shr(n)`.
- `<` is lowered internally but behaves as a normal left-associative comparison.

---

[Previous: LLM Guide ←](30-llm-guide.md) | [Back to README →](README.md)
