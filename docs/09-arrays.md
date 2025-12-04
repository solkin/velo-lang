# Arrays

## Creating Arrays

```velo
# With initialization
array[int] numbers = new array[int]{1, 2, 3, 4, 5};

# With size specification
array[int] empty = new array[int](10);

# Multidimensional arrays
array[array[int]] matrix = new array[array[int]]{
    new array[int]{1, 2, 3},
    new array[int]{4, 5, 6}
};
```

## Accessing Elements

```velo
array[int] arr = new array[int]{10, 20, 30};
int first = arr[0];      # 10
int second = arr[1];     # 20
arr[2] = 40;             # Change element
```

## Array Properties and Methods

### `len` — Array Length

```velo
array[int] arr = new array[int]{1, 2, 3};
int length = arr.len;     # 3
```

### `sub(start, end)` — Subarray

```velo
array[int] arr = new array[int]{1, 2, 3, 4, 5};
array[int] sub = arr.sub(1, 4);  # [2, 3, 4]
```

### `con(other)` — Concatenation

```velo
array[int] a = new array[int]{1, 2};
array[int] b = new array[int]{3, 4};
array[int] combined = a.con(b);  # [1, 2, 3, 4]
```

### `plus(element)` — Add Element

```velo
array[int] arr = new array[int]{1, 2};
array[int] extended = arr.plus(3);  # [1, 2, 3]
```

### `map(func)` — Transform Elements

```velo
array[int] numbers = new array[int]{1, 2, 3, 4, 5};
array[int] doubled = numbers.map(
    func(int index, int value) int {
        value * 2
    }
);  # [2, 4, 6, 8, 10]
```

---

[Previous: Functions ←](08-functions.md) | [Next: Dictionaries →](10-dictionaries.md)

