# Dictionaries

## Creating Dictionaries

```velo
dict[int:str] map = new dict[int:str]{
    1: "one",
    2: "two",
    3: "three"
};
```

## Accessing Elements

```velo
dict[int:str] d = new dict[int:str]{
    1: "a",
    2: "b"
};

str value = d[1];        # "a"
d[3] = "c";              # Add/change
```

## Dictionary Methods

### `len` — Number of Elements

```velo
int count = d.len;
```

### `set(key, value)` — Set Value

```velo
d.set(5, "e");
```

### `del(key)` — Delete Element

```velo
bool deleted = d.del(2);  # true if element was deleted
```

### `key(key)` — Check Key Existence

```velo
bool exists = d.key(5);   # true if key exists
```

### `val(value)` — Check Value Existence

```velo
bool hasValue = d.val("c");  # true if value exists
```

### `keys` — Array of Keys

```velo
array[int] allKeys = d.keys;
```

### `vals` — Array of Values

```velo
array[str] allValues = d.vals;
```

### `arr` — Array of Tuples (key, value)

```velo
array[tuple[int:str]] pairs = d.arr;
```

---

[Previous: Arrays ←](09-arrays.md) | [Next: Strings →](11-strings.md)

