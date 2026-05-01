#!/usr/bin/env python3
"""
Static site generator for Velo by Example.
Generates a Go-by-Example-style documentation website.
"""

import os
import html
import json

SITE_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SITE_DIR, "public")

EXAMPLES = [
    {
        "id": "hello-world",
        "title": "Hello World",
        "intro": "Our first program will print the classic \"Hello, World!\" message. Here's the full source code.",
        "segments": [
            {
                "docs": "To print output, we first need to include the terminal module which provides console I/O.",
                "code": 'include "lang/terminal.vel";'
            },
            {
                "docs": "We declare a variable <code>hello</code> with type <code>str</code> and assign it a string value.",
                "code": 'str hello = "Hello, World!";'
            },
            {
                "docs": "The <code>term</code> object (created by the terminal module) provides <code>print</code> and <code>println</code> methods for output.",
                "code": "term.println(hello);"
            },
            {
                "docs": "To run the program, save it as <code>hello.vel</code> and use Gradle.",
                "code": "$ ./gradlew run --args=\"hello.vel\"\nHello, World!",
                "is_output": True
            }
        ]
    },
    {
        "id": "values",
        "title": "Values",
        "intro": "Velo has several built-in value types including strings, integers, floats, booleans, and bytes.",
        "segments": [
            {
                "docs": "Strings are declared with double quotes.",
                "code": 'str greeting = "Hello";\nstr multiline = "Line 1\\nLine 2";'
            },
            {
                "docs": "Integers support decimal, hexadecimal, and binary notation.",
                "code": "int decimal = 42;\nint hex = 0xCAFE;\nint binary = 0b101010;"
            },
            {
                "docs": "Floating-point numbers use the <code>float</code> type.",
                "code": "float pi = 3.14;\nfloat e = 2.71828;"
            },
            {
                "docs": "Booleans are <code>true</code> or <code>false</code>.",
                "code": "bool isTrue = true;\nbool isFalse = false;"
            },
            {
                "docs": "Bytes use the <code>y</code> suffix.",
                "code": "byte b = 65;\nbyte c = 2y;"
            },
            {
                "docs": "The <code>any</code> type can hold values of any type.",
                "code": 'any value = 42;\nvalue = "Hello";\nvalue = true;'
            }
        ]
    },
    {
        "id": "variables",
        "title": "Variables",
        "intro": "In Velo, variables are explicitly typed. Every variable declaration includes its type.",
        "segments": [
            {
                "docs": "Variables are declared with an explicit type and an initial value.",
                "code": 'int x = 10;\nstr name = "Velo";'
            },
            {
                "docs": "Variables can be reassigned to new values of the same type.",
                "code": "int a = 5;\na = 10;\na = a + 1;"
            },
            {
                "docs": "Velo supports compound assignment operators for concise updates.",
                "code": "int a = 10;\n\na += 5;    # a = a + 5 → 15\na -= 3;    # a = a - 3 → 12\na *= 2;    # a = a * 2 → 24\na /= 4;    # a = a / 4 → 6\na %= 4;    # a = a % 4 → 2"
            },
            {
                "docs": "The <code>any</code> type allows a variable to hold values of different types.",
                "code": 'any value = 42;\nvalue = "Hello";'
            }
        ]
    },
    {
        "id": "operators",
        "title": "Operators",
        "intro": "Velo supports arithmetic, comparison, logical, and bitwise operators with some unique design choices.",
        "segments": [
            {
                "docs": "Standard arithmetic operators work as expected.",
                "code": "int a = 10;\nint b = 3;\n\nint sum = a + b;     # 13\nint diff = a - b;    # 7\nint prod = a * b;    # 30\nint quot = a / b;    # 3\nint rem = a % b;     # 1"
            },
            {
                "docs": "Unary minus is supported for negation.",
                "code": "int x = -10;\nint y = -x;         # 10\nint z = 5 + -3;     # 2\nint w = -(-2);      # 2"
            },
            {
                "docs": "Comparison operators return <code>bool</code> values.",
                "code": "bool eq = a == b;    # false\nbool ne = a != b;    # true\nbool lt = a < b;     # false\nbool gt = a > b;     # true\nbool le = a <= b;    # false\nbool ge = a >= b;    # true"
            },
            {
                "docs": "Velo uses <code>&amp;</code> for AND, <code>|</code> for OR, and <code>^</code> for XOR. There are no <code>&amp;&amp;</code> or <code>||</code> operators — both operands are always evaluated.",
                "code": "bool a = true;\nbool b = false;\n\nbool and = a & b;    # false\nbool or = a | b;     # true\nbool xor = a ^ b;    # true"
            },
            {
                "docs": "There is no unary <code>!</code> operator. Use <code>== false</code> for negation.",
                "code": "bool value = true;\nif (value == false) {\n    # negation\n};"
            },
            {
                "docs": "Bitwise shift operators are available for integers.",
                "code": "int shl = 5 << 1;   # left shift\nint shr = 5 >> 1;   # right shift"
            }
        ]
    },
    {
        "id": "conditionals",
        "title": "If/Else",
        "intro": "Branching in Velo uses <code>if</code>-<code>then</code>-<code>else</code> syntax. Conditionals are expressions — they return values.",
        "segments": [
            {
                "docs": "The simplest form is an inline expression with <code>then</code> and <code>else</code>.",
                "code": 'int a = 5;\nstr result = if a == 2 then "two" else "not two";'
            },
            {
                "docs": "You can use block form with curly braces for multi-line branches.",
                "code": 'int a = 5;\nstr result = if a == 2 then {\n    "two"\n} else {\n    "not two"\n};'
            },
            {
                "docs": "Chain <code>else if</code> for multiple conditions. Since <code>if</code> is an expression, the result can be assigned directly.",
                "code": 'int score = 85;\nstr grade = if score >= 90 then {\n    "A"\n} else if score >= 80 then {\n    "B"\n} else if score >= 70 then {\n    "C"\n} else {\n    "F"\n};'
            },
            {
                "docs": "There is no ternary operator — use inline <code>if</code>-<code>then</code>-<code>else</code> instead.",
                "code": "int max = if a > b then a else b;"
            }
        ]
    },
    {
        "id": "loops",
        "title": "While Loops",
        "intro": "Velo has a single loop construct: <code>while</code>. There is no <code>for</code> loop, and no <code>break</code> or <code>continue</code>.",
        "segments": [
            {
                "docs": "A basic <code>while</code> loop runs as long as the condition is true.",
                "code": 'include "lang/terminal.vel";\n\nint i = 1;\nwhile (i <= 5) {\n    term.println(i.str);\n    i = i + 1;\n};'
            },
            {
                "docs": "Use compound assignment for concise loop counters.",
                "code": "int i = 0;\nwhile (i < 10) {\n    i += 1;\n};"
            },
            {
                "docs": "An infinite loop uses <code>true</code> as the condition. Since there is no <code>break</code>, use a boolean flag or modify the condition variable to exit.",
                "code": "bool running = true;\nwhile (running) {\n    # do work...\n    running = false;  # exit the loop\n};"
            },
            {
                "docs": "To simulate early exit, set the loop counter to the limit.",
                "code": "int i = 0;\nint n = 100;\nwhile (i < n) {\n    if (found) {\n        i = n;    # exit the loop\n    } else {\n        i = i + 1;\n    };\n};"
            }
        ]
    },
    {
        "id": "functions",
        "title": "Functions",
        "intro": "Functions are first-class citizens in Velo. The last expression in a function body is automatically the return value.",
        "segments": [
            {
                "docs": "A function is declared with <code>func</code>, parameter types, and a return type. The last expression becomes the return value — no <code>return</code> keyword needed.",
                "code": "func add(int a, int b) int {\n    a + b;\n};\n\nint result = add(5, 3);  # 8"
            },
            {
                "docs": "Functions without parameters use empty parentheses.",
                "code": "func getAnswer() int {\n    42;\n};\n\nint answer = getAnswer();"
            },
            {
                "docs": "Functions that don't return a value use the <code>void</code> return type.",
                "code": 'func printHello() void {\n    term.println("Hello");\n};'
            }
        ]
    },
    {
        "id": "lambdas",
        "title": "Lambda Functions",
        "intro": "Velo supports anonymous functions (lambdas) that can be stored in variables and passed around.",
        "segments": [
            {
                "docs": "A lambda is declared with <code>func</code> without a name and assigned to a variable.",
                "code": "any multiply = func(int a, int b) int {\n    a * b;\n};\n\nint result = multiply(4, 5);  # 20"
            },
            {
                "docs": "You can also use typed function variables.",
                "code": "func[int] add = func(int a, int b) int {\n    a + b;\n};"
            },
            {
                "docs": "Lambdas are commonly used with array operations like <code>map</code>.",
                "code": "array[int] numbers = new array[int]{1, 2, 3, 4, 5};\n\narray[int] doubled = numbers.map(\n    func(int index, int value) int {\n        value * 2\n    }\n);"
            }
        ]
    },
    {
        "id": "recursion",
        "title": "Recursion",
        "intro": "Velo supports recursive function calls. Combined with expression-based returns, recursive definitions are very concise.",
        "segments": [
            {
                "docs": "A classic factorial function using recursion and inline <code>if</code>.",
                "code": "func factorial(int n) int {\n    if n <= 1 then 1 else n * factorial(n - 1);\n};"
            },
            {
                "docs": "The Fibonacci sequence is a natural fit for recursive definitions.",
                "code": "func fib(int n) int {\n    if n < 2 then n else fib(n - 1) + fib(n - 2);\n};"
            },
            {
                "docs": "Use <code>let</code> to bind the result and print it.",
                "code": 'include "lang/terminal.vel";\n\nlet(any res = fib(15)) {\n    term.println(res.str);\n};'
            },
            {
                "docs": "",
                "code": "$ ./gradlew run --args=\"fibonacci-recursive.vel\"\n610",
                "is_output": True
            }
        ]
    },
    {
        "id": "higher-order-functions",
        "title": "Higher-Order Functions",
        "intro": "Functions in Velo are first-class values: pass them as arguments, return them, store them in variables. Use <code>func[T]</code> as the parameter type when only the return type matters.",
        "segments": [
            {
                "docs": "A function that takes another function as a parameter. <code>func[int]</code> means \"any callable that returns <code>int</code>\".",
                "code": "func apply(int x, func[int] f) int {\n    f(x);\n};\n\nany square = func(int x) int {\n    x * x;\n};\n\nint result = apply(5, square);  # 25"
            },
            {
                "docs": "The built-in <code>map</code> method on arrays is a higher-order function. It takes a callback with <code>(index, value)</code> parameters.",
                "code": "array[int] nums = new array[int]{1, 2, 3, 4, 5};\n\narray[int] doubled = nums.map(\n    func(int i, int v) int {\n        v * 2\n    }\n);  # [2, 4, 6, 8, 10]"
            },
            {
                "docs": "<code>twice</code> applies a function to its own result.",
                "code": "func twice(int x, func[int] f) int {\n    f(f(x));\n};\n\nany inc = func(int x) int { x + 1; };\nint y = twice(3, inc);  # 5"
            },
            {
                "docs": "Compose two functions to build a new one. The result is itself a <code>func[int]</code> value that can be stored, passed around, or invoked later.",
                "code": "func compose(func[int] f, func[int] g) func[int] {\n    func(int x) int {\n        f(g(x));\n    };\n};\n\nfunc[int] incThenSquare = compose(square, inc);\nint r = incThenSquare(4);  # (4+1)^2 = 25"
            },
            {
                "docs": "A predicate-driven counter. The lambda is built at the call site and captures <code>threshold</code> from the surrounding scope.",
                "code": "func count(array[int] arr, func[bool] pred) int {\n    int n = 0;\n    int i = 0;\n    while (i < arr.len) {\n        if (pred(arr[i])) {\n            n = n + 1;\n        };\n        i = i + 1;\n    };\n    n;\n};\n\nint threshold = 10;\nint big = count(\n    new array[int]{3, 12, 7, 25},\n    func(int v) bool { v > threshold; }\n);  # 2"
            }
        ]
    },
    {
        "id": "closures",
        "title": "Closures",
        "intro": "Functions in Velo capture the variables of their defining scope. The captured environment lives as long as the function value itself, even after the enclosing function has returned.",
        "segments": [
            {
                "docs": "<code>makeAdder</code> returns a lambda that adds a captured number. Each call to <code>makeAdder</code> produces a fresh closure with its own <code>n</code>.",
                "code": "func makeAdder(int n) func[int] {\n    func(int x) int {\n        x + n;\n    };\n};\n\nfunc[int] add5 = makeAdder(5);\nfunc[int] add10 = makeAdder(10);\n\nadd5(3);   # 8\nadd10(3);  # 13"
            },
            {
                "docs": "Closures can capture <em>mutable</em> state. Two counters made from the same factory keep their counts independent.",
                "code": "func makeCounter() func[int] {\n    int count = 0;\n    func() int {\n        count = count + 1;\n        count;\n    };\n};\n\nfunc[int] c1 = makeCounter();\nfunc[int] c2 = makeCounter();\n\nc1();  # 1\nc1();  # 2\nc2();  # 1 — independent state\nc1();  # 3"
            },
            {
                "docs": "A factory can return multiple closures sharing the same captured variable — a classic recipe for encapsulated state.",
                "code": "class Pair(func[void] add, func[int] get) {};\n\nfunc makeAccumulator() Pair {\n    int total = 0;\n    new Pair(\n        func(int v) void { total = total + v; },\n        func() int { total; }\n    );\n};\n\nPair acc = makeAccumulator();\nacc.add(7);\nacc.add(35);\nacc.get();   # 42"
            },
            {
                "docs": "Currying: a function that returns another function lets you bind arguments one at a time.",
                "code": "func add(int a) func[int] {\n    func(int b) int {\n        a + b;\n    };\n};\n\nfunc[int] addTen = add(10);\naddTen(5);   # 15\nadd(3)(4);   # 7"
            },
            {
                "docs": "Closures are ordinary values: store them in fields, arrays, or pass them across function boundaries. Their captured environment is reachable as long as the closure itself is reachable.",
                "code": "class Button(str label, func[void] onClick) {};\n\nint clicks = 0;\nButton b = new Button(\"OK\", func() void {\n    clicks = clicks + 1;\n});\n\nb.onClick();\nb.onClick();\n# clicks is now 2"
            }
        ]
    },
    {
        "id": "arrays",
        "title": "Arrays",
        "intro": "Arrays in Velo are typed collections. They are declared with <code>array[T]</code> syntax.",
        "segments": [
            {
                "docs": "Create arrays with initial values or a fixed size.",
                "code": "array[int] numbers = new array[int]{1, 2, 3, 4, 5};\narray[int] empty = new array[int](10);"
            },
            {
                "docs": "Access elements by index. Zero-based indexing.",
                "code": "array[int] arr = new array[int]{10, 20, 30};\nint first = arr[0];     # 10\nint second = arr[1];    # 20\narr[2] = 40;            # modify element"
            },
            {
                "docs": "The <code>len</code> property returns the array length.",
                "code": "array[int] arr = new array[int]{1, 2, 3};\nint length = arr.len;    # 3"
            },
            {
                "docs": "<code>sub(start, end)</code> returns a subarray. <code>con(other)</code> concatenates two arrays. <code>plus(element)</code> adds an element.",
                "code": "array[int] arr = new array[int]{1, 2, 3, 4, 5};\narray[int] sub = arr.sub(1, 4);   # [2, 3, 4]\n\narray[int] a = new array[int]{1, 2};\narray[int] b = new array[int]{3, 4};\narray[int] combined = a.con(b);    # [1, 2, 3, 4]\n\narray[int] extended = a.plus(3);   # [1, 2, 3]"
            },
            {
                "docs": "<code>map</code> transforms each element using a callback function.",
                "code": "array[int] numbers = new array[int]{1, 2, 3, 4, 5};\narray[int] doubled = numbers.map(\n    func(int index, int value) int {\n        value * 2\n    }\n);  # [2, 4, 6, 8, 10]"
            },
            {
                "docs": "Multidimensional arrays use nested types.",
                "code": "array[array[int]] matrix = new array[array[int]]{\n    new array[int]{1, 2, 3},\n    new array[int]{4, 5, 6}\n};"
            }
        ]
    },
    {
        "id": "dictionaries",
        "title": "Dictionaries",
        "intro": "Dictionaries (<code>dict[K:V]</code>) are key-value collections with typed keys and values.",
        "segments": [
            {
                "docs": "Create a dictionary with initial key-value pairs.",
                "code": 'dict[int:str] d = new dict[int:str]{\n    1: "one",\n    2: "two",\n    3: "three"\n};'
            },
            {
                "docs": "Access values by key. Add or change entries with bracket syntax.",
                "code": 'str value = d[1];     # "one"\nd[4] = "four";        # add new entry'
            },
            {
                "docs": "Use <code>set</code>, <code>del</code>, <code>key</code>, and <code>val</code> methods for common operations.",
                "code": 'd.set(5, "five");\nbool deleted = d.del(2);    # true\nbool exists = d.key(5);     # true\nbool hasVal = d.val("one");  # true'
            },
            {
                "docs": "<code>len</code> gives the count. <code>keys</code> and <code>vals</code> return arrays of keys and values.",
                "code": "int count = d.len;\narray[int] allKeys = d.keys;\narray[str] allValues = d.vals;"
            }
        ]
    },
    {
        "id": "strings",
        "title": "Strings",
        "intro": "Strings in Velo are immutable sequences of characters with built-in methods for manipulation.",
        "segments": [
            {
                "docs": "Strings are created with double quotes. Escape sequences like <code>\\n</code> are supported.",
                "code": 'str greeting = "Hello, World!";\nstr empty = "";\nstr multiline = "Line 1\\nLine 2";'
            },
            {
                "docs": "Use <code>len</code> to get the length and <code>sub(start, end)</code> for substrings.",
                "code": 'str s = "Hello, World!";\nint length = s.len;        # 13\nstr sub = s.sub(7, 12);    # "World"'
            },
            {
                "docs": "Concatenate strings with the <code>+</code> operator or the <code>con</code> method.",
                "code": 'str a = "Hello";\nstr b = "World";\nstr combined = a + ", " + b;        # "Hello, World"\nstr alt = a.con(", ").con(b);       # same result'
            },
            {
                "docs": "Access individual character codes by index. Convert with <code>.char</code>.",
                "code": 'str s = "Hello";\nint charCode = s[0];        # character code for \'H\'\nstr firstChar = s[0].char;  # "H"'
            },
            {
                "docs": "Convert between types using <code>.str</code> and <code>.int</code> properties.",
                "code": 'int num = 42;\nstr numStr = num.str;       # "42"\n\nstr text = "123";\nint parsed = text.int;      # 123'
            }
        ]
    },
    {
        "id": "tuples",
        "title": "Tuples",
        "intro": "Tuples hold a fixed number of values of different types. Elements are accessed by position (1-based).",
        "segments": [
            {
                "docs": "Create tuples with <code>new tuple(...)</code>. The type specifies the element types.",
                "code": 'tuple[int, str] pair = new tuple(1, "second");\ntuple[int, str, float] triple = new tuple(42, "text", 3.14);'
            },
            {
                "docs": "Access elements using <code>.1</code>, <code>.2</code>, etc. Note: indexing is 1-based.",
                "code": 'tuple[int, str] p = new tuple(1, "second");\nint first = p.1;      # 1\nstr second = p.2;     # "second"'
            },
            {
                "docs": "Tuples are mutable — you can reassign individual elements.",
                "code": 'tuple[int, str] p = new tuple(1, "hello");\np.1 = 42;\np.2 = "world";'
            }
        ]
    },
    {
        "id": "classes",
        "title": "Classes",
        "intro": "Classes in Velo have constructor parameters, fields, and methods. Fields are read-only from outside the class.",
        "segments": [
            {
                "docs": "A class is defined with <code>class</code>, constructor parameters in parentheses, and a body with fields and methods.",
                "code": "class Point(int x, int y) {\n    int xCoord = x;\n    int yCoord = y;\n\n    func move(int dx, int dy) void {\n        xCoord = xCoord + dx;\n        yCoord = yCoord + dy;\n    };\n};"
            },
            {
                "docs": "Create instances with <code>new</code>. Access fields (read-only from outside) and call methods.",
                "code": "Point p = new Point(10, 20);\nint x = p.xCoord;       # reading: OK\n# p.xCoord = 15;        # ERROR: read-only\np.move(5, 5);            # modify via method: OK"
            },
            {
                "docs": "The class body acts as a constructor. Fields can be computed from parameters.",
                "code": "class Counter() {\n    int value = 0;\n\n    func increment() void {\n        value = value + 1;\n    };\n\n    func getValue() int {\n        value;\n    };\n};\n\nCounter c = new Counter();\nc.increment();\nc.increment();\nint v = c.getValue();    # 2"
            },
            {
                "docs": "Classes can be nested inside other classes.",
                "code": "class LinkedList() {\n    class Item(Item prev, any value) {};\n\n    Item start;\n    Item end;\n    int length = 0;\n\n    func add(any value) void {\n        end = new Item(end, value);\n        if (length == 0) {\n            start = end;\n        };\n        length = length + 1;\n    };\n};"
            }
        ]
    },
    {
        "id": "extension-functions",
        "title": "Extension Functions",
        "intro": "Extension functions let you add methods to existing types without modifying them. The receiver is declared in parentheses after <code>ext</code>.",
        "segments": [
            {
                "docs": "An extension function for <code>int</code> that returns the maximum of two integers.",
                "code": "ext(int a) max(int b) int {\n    if (a > b) then a else b;\n};\n\nint maxValue = 5.max(10);  # 10"
            },
            {
                "docs": "An extension for <code>str</code> that inserts a substring at a given position.",
                "code": 'ext(str a) insert(int index, str s) str {\n    a.sub(0, index).con(s).con(a.sub(index, a.len));\n};\n\nstr result = "Hello".insert(5, " World");\n# "Hello World"'
            },
            {
                "docs": "Extensions without parameters — parentheses are optional when calling.",
                "code": 'ext(bool b) str() str {\n    if b then "true" else "false"\n};\n\nbool flag = true;\nstr s1 = flag.str();   # with parens\nstr s2 = flag.str;     # without parens'
            },
            {
                "docs": "You can extend classes too.",
                "code": "ext(Terminal t) printInt(int a) str {\n    t.println(a.str);\n};\n\nterm.printInt(42);  # prints \"42\""
            }
        ]
    },
    {
        "id": "pointers",
        "title": "Pointers",
        "intro": "Velo has type-safe, memory-safe pointers for pass-by-reference semantics. No pointer arithmetic is allowed.",
        "segments": [
            {
                "docs": "Create a pointer with an initial value, a null pointer, or take the address of a variable with <code>&amp;</code>.",
                "code": "ptr[int] p = new ptr[int](42);\nptr[int] nullPtr = new ptr[int];\nptr[int] alsoNull = null;\n\nint x = 10;\nptr[int] px = &x;"
            },
            {
                "docs": "Dereference with <code>.val</code>, <code>.*</code>, or the prefix <code>*</code> operator.",
                "code": "ptr[int] p = new ptr[int](42);\n\nint value = p.val;    # 42\nint value2 = *p;      # 42\n\np.val = 100;\n*p = 200;"
            },
            {
                "docs": "The address-of operator creates a reference to an existing variable. Modifying through the pointer changes the original.",
                "code": "int x = 10;\nptr[int] px = &x;\npx.val = 20;\n# x is now 20"
            },
            {
                "docs": "A classic swap function using pointers.",
                "code": "func swap(ptr[int] a, ptr[int] b) void {\n    int tmp = a.val;\n    a.val = b.val;\n    b.val = tmp;\n};\n\nint x = 10;\nint y = 20;\nswap(&x, &y);\n# x = 20, y = 10"
            },
            {
                "docs": "Check for null before dereferencing.",
                "code": 'ptr[int] p = new ptr[int];\n\nif (p != null) {\n    int v = p.val;\n} else {\n    term.println("Pointer is null");\n};'
            }
        ]
    },
    {
        "id": "generics",
        "title": "Generics",
        "intro": "Velo supports generic classes and functions with type parameters in square brackets. Generics are a compile-time feature with no runtime overhead.",
        "segments": [
            {
                "docs": "A generic class with a type parameter <code>T</code>.",
                "code": "class Box[T](T value) {\n    func get() T {\n        value;\n    };\n    func set(T newValue) void {\n        value = newValue;\n    };\n};\n\nBox[int] intBox = new Box[int](42);\nBox[str] strBox = new Box[str](\"hello\");"
            },
            {
                "docs": "Multiple type parameters are separated by commas.",
                "code": "class Pair[T, U](T first, U second) {\n    func getFirst() T { first; };\n    func getSecond() U { second; };\n};\n\nPair[int, str] p = new Pair[int, str](42, \"hello\");\nint n = p.getFirst();     # 42\nstr s = p.getSecond();    # \"hello\""
            },
            {
                "docs": "Generic functions infer type arguments automatically from call arguments — no explicit type needed.",
                "code": "func identity[T](T value) T {\n    value;\n};\n\nint x = identity(42);        # T = int\nstr s = identity(\"hello\");   # T = str"
            },
            {
                "docs": "A generic container (stack) using arrays.",
                "code": "class Stack[T]() {\n    array[T] items = new array[T](0);\n    int size = 0;\n\n    func push(T item) void {\n        items = items.plus(item);\n        size = size + 1;\n    };\n\n    func peek() T {\n        items[size - 1];\n    };\n};\n\nStack[str] stack = new Stack[str]();\nstack.push(\"a\");\nstack.push(\"b\");\nstr top = stack.peek();  # \"b\""
            }
        ]
    },
    {
        "id": "apply-blocks",
        "title": "Apply Blocks",
        "intro": "Apply blocks let you execute operations in the context of an expression's result, using the implicit variable <code>it</code>. Similar to Kotlin's <code>.apply{}</code>.",
        "segments": [
            {
                "docs": "An apply block follows any expression with <code>{ }</code>. Inside, <code>it</code> refers to the expression's value.",
                "code": 'class Person(str name, int age) {\n    func setName(str n) void { name = n; };\n    func setAge(int a) void { age = a; };\n};\n\nPerson p = new Person("", 0) {\n    it.setName("John");\n    it.setAge(25);\n};'
            },
            {
                "docs": "Apply blocks work with arrays and dictionaries for initialization.",
                "code": "array[int] arr = new array[int](5) {\n    it[0] = 1;\n    it[1] = 2;\n    it[2] = 3;\n    it[3] = 4;\n    it[4] = 5;\n};"
            },
            {
                "docs": "Apply blocks can be nested.",
                "code": "class Point(int x, int y) {\n    func setX(int val) void { x = val; };\n    func setY(int val) void { y = val; };\n};\n\nclass Line(Point start, Point end) {};\n\nLine line = new Line(\n    new Point(0, 0), new Point(0, 0)\n) {\n    it.start {\n        it.setX(10);\n        it.setY(20);\n    };\n    it.end {\n        it.setX(100);\n        it.setY(200);\n    };\n};"
            },
            {
                "docs": "They also work with primitives.",
                "code": "int x = 5 {\n    it = it * 2;\n};\n# x = 10"
            }
        ]
    },
    {
        "id": "operator-overloading",
        "title": "Operator Overloading",
        "intro": "Classes can define custom behavior for built-in operators using the <code>operator</code> keyword. Velo supports arithmetic, comparison, unary, and index operators.",
        "segments": [
            {
                "docs": "Define binary operators like <code>+</code>, <code>-</code>, <code>*</code>, <code>/</code> inside a class.",
                "code": "class Vector(int x, int y) {\n    operator +(Vector other) Vector {\n        new Vector(x + other.x, y + other.y);\n    };\n\n    operator -(Vector other) Vector {\n        new Vector(x - other.x, y - other.y);\n    };\n};\n\nVector a = new Vector(1, 2);\nVector b = new Vector(3, 4);\nVector sum = a + b;    # Vector(4, 6)"
            },
            {
                "docs": "Comparison operators return <code>bool</code>.",
                "code": "class Vector(int x, int y) {\n    operator ==(Vector other) bool {\n        x == other.x & y == other.y;\n    };\n\n    operator <(Vector other) bool {\n        x < other.x & y < other.y;\n    };\n};\n\nbool eq = a == a;      # true\nbool lt = a < b;       # true"
            },
            {
                "docs": "Unary negation takes no parameters. The compiler distinguishes it from binary <code>-</code> by the parameter count.",
                "code": "class Vector(int x, int y) {\n    operator -() Vector {\n        new Vector(0 - x, 0 - y);\n    };\n};\n\nVector neg = -a;       # Vector(-1, -2)"
            },
            {
                "docs": "Index operators enable bracket-based read and write access.",
                "code": "class Vector(int x, int y) {\n    operator [](int index) int {\n        if (index == 0) then x else y;\n    };\n\n    operator []=(int index, int value) void {\n        if (index == 0) then x = value\n        else y = value;\n    };\n};\n\nVector v = new Vector(0, 0);\nv[0] = 10;\nv[1] = 20;\nint first = v[0];      # 10"
            },
            {
                "docs": "Compound assignment (<code>+=</code>, <code>-=</code>, etc.) works automatically — it desugars to <code>a = a + b</code>.",
                "code": "Vector v = new Vector(1, 2);\nv = v + new Vector(10, 10);\n# v is now Vector(11, 12)"
            }
        ]
    },
    {
        "id": "modules",
        "title": "Modules",
        "intro": "Velo uses the <code>include</code> directive to import code from other files. Standard library modules live in the <code>lang/</code> directory.",
        "segments": [
            {
                "docs": "Use <code>include</code> to load other <code>.vel</code> files. Paths are relative to the current file.",
                "code": 'include "lang/terminal.vel";\ninclude "lang/filesystem.vel";\n\nTerminal term = new Terminal();\nFileSystem fs = new FileSystem();'
            },
            {
                "docs": "Standard library modules include:",
                "code": '# I/O\ninclude "lang/terminal.vel";    # Terminal I/O\ninclude "lang/filesystem.vel";  # File operations\ninclude "lang/http.vel";        # HTTP client\ninclude "lang/socket.vel";      # TCP sockets\n\n# Types\ninclude "lang/bool.vel";        # Bool extensions\ninclude "lang/int.vel";         # Int extensions\ninclude "lang/str.vel";         # String extensions\ninclude "lang/array.vel";       # Array extensions\ninclude "lang/map.vel";         # Generic hash map\n\n# Utilities\ninclude "lang/time.vel";        # Time operations\ninclude "lang/base64.vel";      # Base64 encoding'
            }
        ]
    },
    {
        "id": "let-bindings",
        "title": "Let Bindings",
        "intro": "The <code>let</code> construct creates scoped local variables, keeping the surrounding scope clean.",
        "segments": [
            {
                "docs": "<code>let</code> declares a variable that is only visible inside the block.",
                "code": 'let(any result = calculate()) {\n    term.println(result.str);\n};\n# result is not accessible here'
            },
            {
                "docs": "This is useful for binding intermediate results.",
                "code": 'let(any res = fib(15)) {\n    term.println(res.str);\n};'
            }
        ]
    },
    {
        "id": "type-conversions",
        "title": "Type Conversions",
        "intro": "Velo provides built-in properties and extension functions for converting between types.",
        "segments": [
            {
                "docs": "Convert integers and other types to strings with <code>.str</code>.",
                "code": 'int num = 42;\nstr s = num.str;       # "42"\n\nfloat pi = 3.14;\nstr fs = pi.str;       # "3.14"'
            },
            {
                "docs": "Parse strings to integers with <code>.int</code>.",
                "code": 'str text = "123";\nint n = text.int;      # 123'
            },
            {
                "docs": "The <code>bool.vel</code> module adds bool conversion extensions.",
                "code": 'include "lang/bool.vel";\n\nbool flag = true;\nstr s = flag.str;      # "true"\nint i = flag.int;      # 1\nbool neg = flag.not;   # false'
            },
            {
                "docs": "The <code>int.vel</code> module adds formatting and math extensions.",
                "code": 'include "lang/int.vel";\n\nint x = -42;\nint absVal = x.abs();          # 42\nstr hex = (255).format(16);    # "ff"\nstr bin = (10).format(2);      # "1010"'
            },
            {
                "docs": "Convert between strings and byte arrays with <code>str.vel</code> and <code>array.vel</code>.",
                "code": 'include "lang/str.vel";\ninclude "lang/array.vel";\n\nstr text = "Hello";\narray[byte] bytes = text.bytes();\nstr back = bytes.str();   # "Hello"'
            }
        ]
    },
    {
        "id": "terminal-io",
        "title": "Terminal I/O",
        "intro": "The Terminal module provides console input and output. It's the most commonly used standard library module.",
        "segments": [
            {
                "docs": "Include the terminal module to get the <code>term</code> object.",
                "code": 'include "lang/terminal.vel";'
            },
            {
                "docs": "<code>print</code> outputs text without a newline. <code>println</code> adds a newline.",
                "code": 'term.print("Hello ");     # no newline\nterm.println("World");    # with newline'
            },
            {
                "docs": "<code>input()</code> reads a line from the console.",
                "code": 'term.print("Enter your name: ");\nstr name = term.input();\nterm.println("Hello, ".con(name));'
            }
        ]
    },
    {
        "id": "file-system",
        "title": "File System",
        "intro": "The FileSystem module provides file and directory operations.",
        "segments": [
            {
                "docs": "Include the filesystem module.",
                "code": 'include "lang/filesystem.vel";\n\nFileSystem fs = new FileSystem();'
            },
            {
                "docs": "Read, write, and append to text files.",
                "code": 'fs.write("file.txt", "Content");\nstr content = fs.read("file.txt");\nfs.append("file.txt", "\\nMore content");'
            },
            {
                "docs": "Check file existence and type.",
                "code": 'bool exists = fs.exists("file.txt");\nbool isFile = fs.isFile("file.txt");\nbool isDir = fs.isDir("directory");'
            },
            {
                "docs": "Directory and file management operations.",
                "code": 'fs.mkdir("new_dir");\narray[str] files = fs.list(".");\n\nfs.copy("source.txt", "dest.txt");\nfs.move("old.txt", "new.txt");\nfs.delete("file.txt");'
            }
        ]
    },
    {
        "id": "http",
        "title": "HTTP Client",
        "intro": "The Http module provides a simple HTTP client for making GET and POST requests.",
        "segments": [
            {
                "docs": "Include the HTTP module and create an instance.",
                "code": 'include "lang/http.vel";\n\nHttp http = new Http();'
            },
            {
                "docs": "Make a GET request and check the status code.",
                "code": 'str response = http.get("https://example.com");\nint status = http.statusCode();'
            },
            {
                "docs": "Make a POST request with a JSON body.",
                "code": 'str body = "{\\"key\\": \\"value\\"}";\nstr result = http.post(\n    "https://api.example.com/data",\n    body,\n    ""\n);'
            }
        ]
    },
    {
        "id": "sockets",
        "title": "TCP Sockets",
        "intro": "The Socket module provides TCP socket communication for both client and server modes.",
        "segments": [
            {
                "docs": "A TCP client connects to a remote host and exchanges data.",
                "code": 'include "lang/socket.vel";\n\nSocket sock = Socket();\nsock.connect("127.0.0.1", 9876);\n\nsock.writeLine("Hello!");\nstr reply = sock.readLine();\n\nsock.close();'
            },
            {
                "docs": "A TCP server binds to a port and accepts incoming connections.",
                "code": 'include "lang/socket.vel";\n\nSocket srv = Socket();\nsrv.bind(9876);\n\nSocket client = srv.accept();\nstr msg = client.readLine();\nclient.writeLine("Echo: " + msg);\n\nclient.close();\nsrv.close();'
            }
        ]
    },
    {
        "id": "hash-map",
        "title": "Hash Map",
        "intro": "The <code>Map[K, V]</code> module provides a generic hash map with operator overloading for bracket access.",
        "segments": [
            {
                "docs": "Create a Map and add entries using bracket syntax or <code>put</code>.",
                "code": 'include "lang/map.vel";\n\nMap[str, int] ages = new Map[str, int]();\n\nages["Alice"] = 30;\nages["Bob"] = 25;\nages.put("Charlie", 35);'
            },
            {
                "docs": "Bracket access returns <code>V</code> directly. Use <code>getOrDefault</code> for a fallback value.",
                "code": 'int age = ages["Alice"];    # 30\n\nint eveAge = ages.getOrDefault("Eve", 0);  # 0'
            },
            {
                "docs": "Check, insert conditionally, and remove entries.",
                "code": 'bool has = ages.key("Bob");                # true\nbool added = ages.putIfAbsent("Diana", 28); # true\nbool removed = ages.del("Charlie");        # true'
            },
            {
                "docs": "Iterate using <code>keys()</code> and <code>vals()</code>.",
                "code": "array[str] k = ages.keys();\narray[int] v = ages.vals();\n\nint i = 0;\nwhile (i < k.len) {\n    # process k[i] and v[i]\n    i = i + 1;\n};"
            }
        ]
    },
    {
        "id": "native-classes",
        "title": "Native Classes",
        "intro": "Native classes bridge Velo to Java/Kotlin classes via reflection, enabling JVM interop.",
        "segments": [
            {
                "docs": "Declare a native class with <code>native class</code> and <code>native func</code> for methods backed by JVM.",
                "code": 'native class Terminal() {\n    native func print(str text) void;\n    native func input() str;\n\n    func println(str text) void {\n        print(text.con("\\n"));\n    };\n};\n\nTerminal term = new Terminal();\nterm.println("Hello!");'
            },
            {
                "docs": "Register native classes in Kotlin using <code>VeloRuntime</code>.",
                "code": '// Kotlin\nval runtime = VeloRuntime()\n    .register(MyClass::class)\n    .register("VeloName", JvmClass::class)\n\nruntime.runFile("script.vel")',
                "lang": "kotlin"
            },
            {
                "docs": "Type mapping between Velo and JVM is automatic for primitives and collections.",
                "code": "# Velo     → JVM\n# int      → Int\n# float    → Float\n# str      → String\n# bool     → Boolean\n# byte     → Byte\n# array[T] → Array<T>\n# dict[K:V]→ Map<K, V>"
            }
        ]
    }
]


def escape(text):
    return html.escape(text)


def highlight_velo(code):
    """Simple syntax highlighting for Velo code."""
    import re

    keywords = [
        'include', 'func', 'class', 'native', 'ext', 'operator',
        'if', 'then', 'else', 'while', 'let', 'new', 'null', 'void',
        'true', 'false',
    ]
    types = [
        'int', 'float', 'str', 'bool', 'byte', 'any',
        'array', 'dict', 'tuple', 'ptr',
    ]

    lines = code.split('\n')
    result_lines = []

    for line in lines:
        chars = list(line)
        tokens = []
        i = 0

        while i < len(chars):
            # Comment
            if chars[i] == '#':
                tokens.append(('comment', line[i:]))
                i = len(chars)
            # String
            elif chars[i] == '"':
                j = i + 1
                while j < len(chars):
                    if chars[j] == '\\' and j + 1 < len(chars):
                        j += 2
                        continue
                    if chars[j] == '"':
                        j += 1
                        break
                    j += 1
                tokens.append(('string', line[i:j]))
                i = j
            # Number
            elif chars[i].isdigit() or (chars[i] == '-' and i + 1 < len(chars) and chars[i+1].isdigit()):
                j = i
                if chars[j] == '-':
                    j += 1
                if j + 1 < len(chars) and chars[j] == '0' and chars[j+1] in 'xXbB':
                    j += 2
                    while j < len(chars) and (chars[j].isalnum()):
                        j += 1
                else:
                    while j < len(chars) and (chars[j].isdigit() or chars[j] == '.'):
                        j += 1
                    if j < len(chars) and chars[j] in 'yf':
                        j += 1
                tokens.append(('number', line[i:j]))
                i = j
            # Word (keyword, type, or identifier)
            elif chars[i].isalpha() or chars[i] == '_':
                j = i
                while j < len(chars) and (chars[j].isalnum() or chars[j] == '_'):
                    j += 1
                word = line[i:j]
                if word in keywords:
                    tokens.append(('keyword', word))
                elif word in types:
                    tokens.append(('type', word))
                elif j < len(chars) and chars[j] == '(' and word[0].isupper():
                    tokens.append(('type', word))
                elif word[0].isupper():
                    tokens.append(('type', word))
                else:
                    tokens.append(('plain', word))
                i = j
            # Operators
            elif chars[i] in '+-*/%=<>!&|^':
                j = i
                while j < len(chars) and chars[j] in '+-*/%=<>!&|^':
                    j += 1
                tokens.append(('operator', line[i:j]))
                i = j
            else:
                tokens.append(('plain', chars[i]))
                i += 1

        highlighted = ''
        for ttype, tval in tokens:
            escaped = escape(tval)
            if ttype == 'keyword':
                highlighted += f'<span class="kw">{escaped}</span>'
            elif ttype == 'type':
                highlighted += f'<span class="ty">{escaped}</span>'
            elif ttype == 'string':
                highlighted += f'<span class="st">{escaped}</span>'
            elif ttype == 'number':
                highlighted += f'<span class="nu">{escaped}</span>'
            elif ttype == 'comment':
                highlighted += f'<span class="cm">{escaped}</span>'
            elif ttype == 'operator':
                highlighted += f'<span class="op">{escaped}</span>'
            else:
                highlighted += escaped

        result_lines.append(highlighted)

    return '\n'.join(result_lines)


def generate_index():
    items_html = ''
    for ex in EXAMPLES:
        items_html += f'        <li><a href="examples/{ex["id"]}.html">{ex["title"]}</a></li>\n'

    return f'''<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Velo by Example</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div class="index-page">
        <div class="index-header">
            <h1>Velo by Example</h1>
            <p class="tagline">Simple. Embeddable. Yours.</p>
        </div>
        <div class="index-intro">
            <p>
                <a href="https://github.com/solkin/velo-lang">Velo Lang</a> is a functional,
                strictly-typed, compilable programming language that runs on a lightweight stack-based
                virtual machine. It features strict typing, higher-order functions, generics, and
                easy embedding into JVM applications.
            </p>
            <p>
                <em>Velo by Example</em> is a hands-on introduction to Velo using annotated example programs.
                Check out the <a href="examples/{EXAMPLES[0]["id"]}.html">first example</a>
                or browse the full list below.
            </p>
        </div>
        <ul class="example-list">
{items_html}        </ul>
        <div class="index-footer">
            <p>by <a href="https://github.com/solkin/velo-lang">Velo Lang</a> &middot;
            <a href="https://github.com/solkin/velo-lang">source</a> &middot;
            <a href="https://github.com/solkin/velo-lang/blob/main/LICENSE">license</a></p>
        </div>
    </div>
</body>
</html>'''


def generate_example(idx, example):
    prev_link = ''
    next_link = ''
    if idx > 0:
        prev_ex = EXAMPLES[idx - 1]
        prev_link = f'<a href="{prev_ex["id"]}.html">&larr; {prev_ex["title"]}</a>'
    if idx < len(EXAMPLES) - 1:
        next_ex = EXAMPLES[idx + 1]
        next_link = f'<a href="{next_ex["id"]}.html">{next_ex["title"]} &rarr;</a>'

    rows_html = ''

    # Intro row
    if example.get("intro"):
        rows_html += f'''            <tr>
                <td class="docs"><p>{example["intro"]}</p></td>
                <td class="code empty"></td>
            </tr>
'''

    for seg in example["segments"]:
        docs_html = f'<p>{seg["docs"]}</p>' if seg.get("docs") else ''
        code = seg.get("code", "")
        is_output = seg.get("is_output", False)
        lang = seg.get("lang", "velo")

        if is_output:
            code_html = f'<pre class="output"><code>{escape(code)}</code></pre>'
        elif lang == "kotlin":
            code_html = f'<pre><code>{escape(code)}</code></pre>'
        else:
            code_html = f'<pre><code>{highlight_velo(code)}</code></pre>'

        code_class = "code" if not is_output else "code output-cell"

        rows_html += f'''            <tr>
                <td class="docs">{docs_html}</td>
                <td class="{code_class}">{code_html}</td>
            </tr>
'''

    nav_html = f'''            <tr>
                <td class="docs nav-footer">
                    <div class="nav-links">
                        <span class="nav-prev">{prev_link}</span>
                    </div>
                </td>
                <td class="code nav-footer">
                    <div class="nav-links">
                        <span class="nav-next">{next_link}</span>
                    </div>
                </td>
            </tr>'''

    return f'''<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Velo by Example: {escape(example["title"])}</title>
    <link rel="stylesheet" href="../style.css">
</head>
<body>
    <div class="example-page">
        <h2><a href="../index.html">Velo by Example</a>: {escape(example["title"])}</h2>
        <table>
{rows_html}{nav_html}
        </table>
    </div>
</body>
</html>'''


def generate_css():
    return '''*,
*::before,
*::after {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
}

:root {
    --bg: #ffffff;
    --bg-code: #f7f7f9;
    --bg-output: #e8e8ea;
    --text: #252525;
    --text-muted: #6b7280;
    --accent: #2563eb;
    --accent-hover: #1d4ed8;
    --border: #e5e7eb;
    --kw: #7c3aed;
    --ty: #0891b2;
    --st: #16a34a;
    --nu: #dc2626;
    --cm: #9ca3af;
    --op: #6b7280;
    --font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    --font-mono: "SF Mono", "Fira Code", "Fira Mono", "Roboto Mono", Menlo, Consolas, monospace;
}

html {
    font-size: 15px;
    -webkit-font-smoothing: antialiased;
}

body {
    font-family: var(--font-sans);
    color: var(--text);
    background: var(--bg);
    line-height: 1.6;
}

a {
    color: var(--accent);
    text-decoration: none;
}

a:hover {
    color: var(--accent-hover);
    text-decoration: underline;
}

code {
    font-family: var(--font-mono);
    font-size: 0.9em;
}

/* ---------- Index page ---------- */

.index-page {
    max-width: 620px;
    margin: 0 auto;
    padding: 60px 24px;
}

.index-header h1 {
    font-size: 2rem;
    font-weight: 700;
    letter-spacing: -0.02em;
    margin-bottom: 4px;
}

.index-header .tagline {
    font-size: 1.05rem;
    color: var(--text-muted);
    margin-bottom: 28px;
}

.index-intro {
    margin-bottom: 32px;
    line-height: 1.7;
}

.index-intro p {
    margin-bottom: 12px;
}

.example-list {
    list-style: none;
    columns: 2;
    column-gap: 24px;
    margin-bottom: 40px;
}

.example-list li {
    padding: 3px 0;
    break-inside: avoid;
}

.example-list a {
    display: inline-block;
    padding: 2px 0;
}

.index-footer {
    border-top: 1px solid var(--border);
    padding-top: 16px;
    color: var(--text-muted);
    font-size: 0.85rem;
}

/* ---------- Example page ---------- */

.example-page {
    max-width: 1200px;
    margin: 0 auto;
    padding: 30px 20px 60px;
}

.example-page h2 {
    font-size: 1.3rem;
    font-weight: 600;
    margin-bottom: 24px;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--border);
}

.example-page h2 a {
    color: var(--text-muted);
    font-weight: 400;
}

.example-page table {
    width: 100%;
    border-collapse: collapse;
}

.example-page td {
    vertical-align: top;
    padding: 4px 0;
}

.example-page td.docs {
    width: 35%;
    padding-right: 28px;
    padding-top: 12px;
    padding-bottom: 12px;
}

.example-page td.docs p {
    line-height: 1.65;
    color: var(--text);
}

.example-page td.docs code {
    background: var(--bg-code);
    padding: 1px 5px;
    border-radius: 3px;
    font-size: 0.85em;
}

.example-page td.code {
    width: 65%;
    padding-top: 4px;
    padding-bottom: 4px;
}

.example-page td.code pre {
    background: var(--bg-code);
    border-radius: 6px;
    padding: 12px 16px;
    overflow-x: auto;
    margin: 4px 0;
    line-height: 1.55;
}

.example-page td.code pre.output {
    background: var(--bg-output);
    border-left: 3px solid var(--text-muted);
}

.example-page td.code pre code {
    font-size: 0.87rem;
}

.example-page td.empty {
    padding: 0;
}

/* Syntax highlighting */
.kw { color: var(--kw); font-weight: 600; }
.ty { color: var(--ty); }
.st { color: var(--st); }
.nu { color: var(--nu); }
.cm { color: var(--cm); font-style: italic; }
.op { color: var(--op); }

/* Navigation */
.nav-footer {
    padding-top: 24px !important;
    border-top: 1px solid var(--border);
}

.nav-links {
    display: flex;
    justify-content: space-between;
}

.nav-prev { text-align: left; }
.nav-next { text-align: right; margin-left: auto; }

/* ---------- Responsive ---------- */

@media (max-width: 768px) {
    .example-page table,
    .example-page tbody,
    .example-page tr,
    .example-page td {
        display: block;
        width: 100%;
    }

    .example-page td.docs {
        width: 100%;
        padding-right: 0;
        padding-bottom: 4px;
    }

    .example-page td.code {
        width: 100%;
        padding-bottom: 16px;
    }

    .example-list {
        columns: 1;
    }
}
'''


def main():
    os.makedirs(os.path.join(OUTPUT_DIR, "examples"), exist_ok=True)

    # Write CSS
    with open(os.path.join(OUTPUT_DIR, "style.css"), 'w') as f:
        f.write(generate_css())
    print("Generated: style.css")

    # Write index
    with open(os.path.join(OUTPUT_DIR, "index.html"), 'w') as f:
        f.write(generate_index())
    print("Generated: index.html")

    # Write example pages
    for i, ex in enumerate(EXAMPLES):
        filename = f'examples/{ex["id"]}.html'
        with open(os.path.join(OUTPUT_DIR, filename), 'w') as f:
            f.write(generate_example(i, ex))
        print(f"Generated: {filename}")

    print(f"\nDone! Generated {len(EXAMPLES) + 2} files in {OUTPUT_DIR}/")


if __name__ == '__main__':
    main()
