# Native Classes

Native classes allow using Java/Kotlin classes in Velo Lang through reflection.

## Defining Native Classes

```velo
native class Terminal() {
    native func print(str text) void;
    native func input() str;
    
    # Regular methods can use native ones
    func println(str text) void {
        print(text.con("\n"));
    };
};

Terminal term = new Terminal();
term.println("Hello, World!");
```

## Requirements for Native Classes

1. The class name in Velo Lang must match the Java/Kotlin class name (or use custom mapping via `VeloRuntime.register()`)
2. Method signatures must match the expected JVM types
3. The constructor must have the same parameters

## Type Mapping: Velo ↔ JVM

### Primitives (Full Support)

| Velo Type | JVM Type | Notes |
|-----------|----------|-------|
| `int` | `Int` | ✅ Automatic conversion |
| `float` | `Float` | ✅ Automatic conversion |
| `str` | `String` | ✅ Automatic conversion |
| `bool` | `Boolean` | ✅ Automatic conversion |
| `byte` | `Byte` | ✅ Automatic conversion |

### Native Classes

| Direction | Support | Notes |
|-----------|---------|-------|
| Pass native class as argument | ✅ | Works automatically |
| Return native class | ✅ | Wrapped via `NativeWrap` |
| Return array of native classes | ❌ | Not supported |

**Example: Passing native class to native method**

```velo
native class Terminal() {
    native func print(str text) void;
};

native class Time() {
    native func print(Terminal term) void;  # Pass Terminal to Time
};

Terminal term = new Terminal();
Time time = new Time();
time.print(term);  # Works!
```

```kotlin
// Kotlin implementation
class Time {
    fun print(term: Terminal) {
        term.print(System.currentTimeMillis().toString())
    }
}
```

**Example: Returning native class from native method**

```velo
native class Terminal() {
    native func print(str text) void;
};

native class TerminalFactory() {
    native func create() Terminal;  # Returns Terminal
};

TerminalFactory factory = new TerminalFactory();
Terminal t = factory.create();
t.print("Hello!");  # Works!
```

```kotlin
// Kotlin implementation
class TerminalFactory {
    fun create(): Terminal {
        return Terminal()
    }
}
```

### Collections (Full Type Support)

Collections are now fully supported with automatic type conversion in both directions.

#### Passing Collections to Native Methods (✅ Full Support)

| Velo Type | JVM Parameter Type | Notes |
|-----------|-------------------|-------|
| `array[int]` | `Array<Int>` | ✅ Auto-converted |
| `array[str]` | `Array<String>` | ✅ Auto-converted |
| `array[float]` | `Array<Float>` | ✅ Auto-converted |
| `array[bool]` | `Array<Boolean>` | ✅ Auto-converted |
| `dict[K:V]` | `Map<K, V>` | ✅ Auto-converted |

**Example: Passing arrays and maps to native methods**

```velo
native class CollectionProcessor() {
    native func joinStrings(array[str] arr, str separator) str;
    native func sumInts(array[int] arr) int;
    native func getFromMap(dict[str:int] map, str key) int;
};

CollectionProcessor processor = new CollectionProcessor();

array[str] words = new array[str]{"hello", "world", "velo"};
str joined = processor.joinStrings(words, " ");  # "hello world velo"

array[int] numbers = new array[int]{1, 2, 3, 4, 5};
int sum = processor.sumInts(numbers);  # 15

dict[str:int] scores = new dict[str:int]{"alice": 100, "bob": 85};
int score = processor.getFromMap(scores, "alice");  # 100
```

```kotlin
// Kotlin implementation - use natural typed signatures
class CollectionProcessor {
    fun joinStrings(arr: Array<String>, separator: String): String {
        return arr.joinToString(separator)
    }
    
    fun sumInts(arr: Array<Int>): Int {
        return arr.sum()
    }
    
    fun getFromMap(map: Map<String, Int>, key: String): Int {
        return map[key] ?: -1
    }
}
```

#### Returning Collections from Native Methods (✅ Full Support)

| Velo Type | JVM Return Type | Notes |
|-----------|-----------------|-------|
| `array[int]` | `Array<Int>` | ✅ Auto-converted |
| `array[str]` | `Array<String>` | ✅ Auto-converted |
| `dict[K:V]` | `Map<K, V>` | ✅ Auto-converted |
| `array[NativeClass]` | `Array<NativeClass>` | ❌ Not supported |

**Example: Returning collections from native methods**

```velo
native class DataProvider() {
    native func getNames() array[str];
    native func getNumbers() array[int];
    native func getConfig() dict[str:int];
};

DataProvider provider = new DataProvider();

array[str] names = provider.getNames();
names[0];  # "Alice"

array[int] nums = provider.getNumbers();
nums[0] + nums[1];  # Works!

dict[str:int] config = provider.getConfig();
config["timeout"];  # Works!
```

```kotlin
// Kotlin implementation
class DataProvider {
    fun getNames(): Array<String> {
        return arrayOf("Alice", "Bob", "Charlie")
    }
    
    fun getNumbers(): Array<Int> {
        return arrayOf(10, 20, 30)
    }
    
    fun getConfig(): Map<String, Int> {
        return mapOf("timeout" to 1000, "retries" to 3)
    }
}
```

## Registering Native Classes

Use `VeloRuntime` to register native classes:

```kotlin
val runtime = VeloRuntime()
    .register(MyClass::class)                    // Same name in Velo
    .register("VeloName", JvmClass::class)       // Custom Velo name

runtime.runFile("script.vel")
```

### Default Native Classes

The following classes are registered by default:
- `Terminal` - console I/O
- `Time` - time operations
- `FileSystem` - file operations
- `Http` - HTTP client
- `Socket` - TCP socket communication

## Summary Table

| Feature | Velo → JVM | JVM → Velo |
|---------|------------|------------|
| Primitives | ✅ Auto | ✅ Auto |
| Native class | ✅ Auto | ✅ Auto (via NativeWrap) |
| Array of primitives | ✅ Auto (typed) | ✅ Auto |
| Map of primitives | ✅ Auto (typed) | ✅ Auto |
| Array of native classes | ❌ Not supported | ❌ Not supported |

---

[Previous: Extension Functions ←](14-extension-functions.md) | [Next: Modules and Includes →](16-modules-and-includes.md)
