# Data Types

## Primitive Types

### Integers (`int`)

```velo
int decimal = 42;
int hex = 0xCAFE;        # Hexadecimal notation
int binary = 0b101010;    # Binary notation
```

### Floating-Point Numbers (`float`)

```velo
float pi = 3.14;
float e = 2.71828;
float withSuffix = 3.0f;  # Explicit type specification
```

### Bytes (`byte`)

```velo
byte b = 65;
byte withSuffix = 2y;     # Explicit type specification
```

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

### Functions (`func[ReturnType]` or `func(Params) ReturnType`)

```velo
func[int] add = func(int a, int b) int {
    a + b;
};
```

---

[Previous: Language Basics ←](02-language-basics.md) | [Next: Variables and Assignment →](04-variables-and-assignment.md)

