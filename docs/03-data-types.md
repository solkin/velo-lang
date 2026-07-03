# Data Types

## Primitive Types

### Integers (`int`)

```velo
int decimal = 42;
int negative = -10;       # Negative numbers
int hex = 0xCAFE;         # Hexadecimal notation
int binary = 0b101010;    # Binary notation
```

### Floating-Point Numbers (`float`)

```velo
float pi = 3.14;
float e = 2.71828;
float negative = -1.5;    # A '.' makes a literal a float
float whole = 5;          # An int literal widens to a float (-> 5.0)
```

### Bytes (`byte`)

```velo
byte b = 65;              # An int literal in range (-128..255) fits a byte
byte negative = -5;
```

### Long Integers (`long`)

`long` is a 64-bit signed integer, one rank wider than `int`:

```velo
long big = 5000000000;    # too large for a 32-bit int → a long literal
long wide = 0x1_0000_0000; # a hex literal wider than 32 bits is a long
long fromInt = 42;        # an int literal widens to long
```

A decimal literal that overflows a signed 32-bit `int`, or a hex/binary literal
wider than 32 bits, is automatically a `long`; smaller literals stay `int` and
widen on assignment. The numeric rank is `byte < int < long < float`.

> Numeric literals have no type suffixes (`2y`, `3.0f` are gone). A literal takes
> the type of its target: an int literal widens to `long` or `float`, or fits
> into a `byte` (out-of-range values are a compile error). A `.` makes a literal
> a `float`.

### Numeric conversions

Widening is implicit and lossless — a `byte` flows into an `int`, an `int` into
a `long`, and any of them into a `float` (so `float f = 5` really holds `5.0`
and `f / 2` is `2.5`, not `2`). Mixed arithmetic promotes to the wider type.

Narrowing loses data, so it must be explicit:

```velo
float pi = 3.75;
int   i  = pi.int();      # 3 — truncates toward zero
byte  b  = 322.byte();    # 66 — low 8 bits
long  l  = i.long();      # widen to 64-bit (also works implicitly)
int   j  = l.int();       # narrow a long back to 32 bits (low 32 bits)
float f  = i.float();     # widen back (i.float() also works implicitly)
```

`int x = pi` (float → int) and `byte b = someInt` (int → byte) are **compile
errors** that tell you to convert with `.int()` / `.byte()`. The conversion
methods are `.byte()`, `.int()`, `.long()`, and `.float()`.

### Strings (`str`)

```velo
str greeting = "Hello";
str multiline = "Line 1\nLine 2";
```

### Boolean Type (`bool`)

```velo
bool isTrue = true;
bool isFalse = false;
```

### Universal Type (`any`)

The `any` type allows storing values of any type:

```velo
any value = 42;        # Can be int
value = "Hello";       # Can be str
value = true;          # Can be bool
```

## Composite Types

### Arrays (`array[T]`)

```velo
array[int] numbers = new array[int]{1, 2, 3};
array[str] words = new array[str]{"hello", "world"};
array[array[int]] matrix = new array[array[int]]{};
```

### Dictionaries (`dict[K:V]`)

```velo
dict[int:str] map = new dict[int:str]{
    1: "one",
    2: "two",
    3: "three"
};
```

### Tuples (`tuple[T1, T2, ...]`)

```velo
tuple[int, str] pair = new tuple(1, "second");
tuple[int, str, float] triple = new tuple(42, "text", 3.14);
```

### Functions (`func[(Params) ReturnType]` or loose `func[ReturnType]`)

```velo
# Full signature — checked at every call site (preferred):
func[(int, int) int] add = func(int a, int b) int {
    return a + b;
};

# Loose form — only the return type; an unchecked escape hatch:
func[int] callback = add;
```

See [Functions](08-functions.md#function-values-and-types) for when to use each.

### Pointers (`ptr[T]`)

Pointers allow pass-by-reference semantics:

```velo
# Pointer with initial value
ptr[int] p = new ptr[int](42);

# Null pointer
ptr[int] nullPtr = new ptr[int];

# Pointer to existing variable
int x = 10;
ptr[int] px = &x;

# Dereference
int value = p.val();  # read: or *p
p.val = 100;          # write: modify through pointer (bare val as assignment target)
```

See [Pointers](21-pointers.md) for detailed documentation.

---

[Previous: Language Basics ←](02-language-basics.md) | [Next: Variables and Assignment →](04-variables-and-assignment.md)

