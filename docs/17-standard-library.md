# Standard Library

## Boolean Extensions

Extension functions for boolean type:

```velo
import "std/bool"

bool flag = true
str s = flag.str()  # "true" or "false"
int i = flag.int()  # 1 or 0
bool neg = !flag  # logical NOT — use the ! operator
```

`bool.str()` is a compiler built-in, available without any import; `import
"std/bool"` is what adds `.int()`.

## Integer Extensions

Extension functions for integer type:

```velo
import "std/int"

int x = -42
int absVal = x.abs()  # 42
str hex = (255).format(16)  # "ff"  — format(radix), radix 2..36
str bin = (10).format(2)  # "1010"
```

## String Extensions

Extension functions for string type:

```velo
import "std/str"

str text = "Hello"
array[byte] bytes = text.bytes()  # [72, 101, 108, 108, 111]
```

## Array Extensions

Extension functions for array type:

```velo
import "std/array"

array[byte] bytes = new array[byte]{72, 101, 108, 108, 111}
str text = bytes.str()  # "Hello"
```

Both extensions enable roundtrip conversion: `text.bytes().str() == text`.

The classes below — Terminal, Time, Http, FileSystem, Socket — are native
classes provided by the runtime, so using them needs no `import`; the
compiler knows their types from the registration. Just construct one. The
examples use `term` as the conventional name for a `Terminal` instance.

## Terminal

Class for terminal operations:

```velo
Terminal term = new Terminal()
term.print("Hello")  # Output without newline
term.println("World")  # Output with newline
str input = term.input()  # Read string from console
```

## Time

Class for time operations:

```velo
Time time = new Time()
time.sleep(1000)  # Sleep for 1000 milliseconds
int unixTime = time.unix()  # Unix timestamp in seconds
int t = time.millis()  # monotonic milliseconds (for measuring elapsed time)
```

## Http

Class for making HTTP requests:

```velo
Http http = new Http()
str response = http.get("https://example.com")
int status = http.statusCode()

# POST request
str jsonBody = "{\"key\": \"value\"}"
str postResponse = http.post("https://api.example.com/data", jsonBody, "")
```

## FileSystem

Class for file system operations:

```velo
FileSystem fs = new FileSystem()

# Reading and writing strings
fs.write("file.txt", "Content")
str content = fs.read("file.txt")
fs.append("file.txt", "\nMore content")

# Reading and writing byte arrays
import "std/str"
array[byte] data = "binary data".bytes()
fs.writeBytes("file.bin", data)
array[byte] loaded = fs.readBytes("file.bin")
fs.appendBytes("file.bin", data)

# Checks
bool exists = fs.exists("file.txt")
bool isFile = fs.isFile("file.txt")
bool isDir = fs.isDir("directory")

# Directory operations
fs.mkdir("new_dir")
array[str] files = fs.list(".")

# File operations
fs.copy("source.txt", "dest.txt")
fs.move("old.txt", "new.txt")
fs.delete("file.txt")

# Information
int fileSize = fs.size("file.txt")
```

## Socket

Class for TCP socket communication. Supports both client and server modes.

### Client

```velo
Socket sock = new Socket()
sock.connect("127.0.0.1", 9876)

sock.writeLine("Hello!")
str reply = sock.readLine()

sock.close()
```

### Server

```velo
Socket srv = new Socket()
srv.bind(9876)

Socket client = srv.accept()  # blocks until a client connects
str msg = client.readLine()
client.writeLine("Echo: " + msg)

client.close()
srv.close()
```

### Full API Reference

| Method | Signature | Description |
|---|---|---|
| `connect` | `(str host, int port) void` | Connect to a remote host |
| `bind` | `(int port) void` | Bind and listen on a port |
| `accept` | `() Socket` | Accept an incoming connection (blocking) |
| `write` | `(str data) void` | Send a string |
| `writeLine` | `(str data) void` | Send a string followed by a newline |
| `writeBytes` | `(array[byte] data) void` | Send a byte array |
| `readLine` | `() str` | Read a line (blocking, strips newline) |
| `read` | `(int size) str` | Read up to `size` characters |
| `readBytes` | `(int size) array[byte]` | Read up to `size` bytes |
| `available` | `() int` | Bytes available without blocking |
| `connected` | `() bool` | Check if socket is connected |
| `close` | `() void` | Close the socket |
| `remoteAddress` | `() str` | Remote peer IP address |
| `remotePort` | `() int` | Remote peer port |
| `setTimeout` | `(int millis) void` | Set timeout for blocking operations |

## Map

A generic hash map (`Map[K, V]`) with separate chaining and automatic resizing. Supports operator overloading for bracket-based access.

### Creating and Populating

```velo
import "std/map"

Map[str, int] ages = new Map[str, int]()

# Operator syntax
ages["Alice"] = 30
ages["Bob"] = 25

# Method syntax
ages.put("Charlie", 35)
```

### Lookup

```velo
# operator [] returns V directly
int age = ages["Alice"]  # 30

# get() returns ptr[V] — null if key is not found
ptr[int] val = ages.get("Alice")
if (val != null) {
    int a = val.val()
}

# getOrDefault() returns V directly, with a fallback
int eveAge = ages.getOrDefault("Eve", 0)  # 0
```

### Checking and Conditional Insert

```velo
bool has = ages.key("Bob")  # true
bool empty = ages.empty()  # false
int count = ages.len  # number of entries (a field — no parentheses)

# putIfAbsent — inserts only if key is missing, returns true if inserted
bool added = ages.putIfAbsent("Diana", 28)  # true
bool again = ages.putIfAbsent("Diana", 99)  # false, value stays 28
```

### Removing Entries

```velo
bool removed = ages.del("Charlie")  # true
bool noop = ages.del("Charlie")  # false (already gone)
```

### Iterating

```velo
array[str] k = ages.keys()  # array of all keys
array[int] v = ages.vals()  # array of all values

int i = 0
while (i < k.len()) {
    # process k[i] and v[i]
    i = i + 1
}
```

### Clearing

```velo
ages.clear()
# ages.len is 0, ages.empty() is true
```

### Automatic Resizing

The map starts with capacity 16 and doubles when the load factor exceeds 75%. All entries are rehashed into the new table automatically. This is transparent — no API changes are needed:

```velo
Map[int, int] big = new Map[int, int]()
int n = 0
while (n < 100) {
    big[n] = n * n
    n = n + 1
}
# capacity has grown automatically, all 100 entries are accessible
```

### Full API Reference

| Method / Operator | Signature | Description |
|---|---|---|
| `operator []` | `(K key) V` | Lookup by key |
| `operator []=` | `(K key, V value) void` | Insert or update |
| `put` | `(K key, V value) void` | Insert or update |
| `get` | `(K key) ptr[V]` | Lookup (null if missing) |
| `getOrDefault` | `(K key, V defaultValue) V` | Lookup with fallback |
| `at` | `(K key) V` | Get value directly (error if missing) |
| `key` | `(K key) bool` | Check key existence |
| `val` | `(V value) bool` | Check if value exists |
| `putIfAbsent` | `(K key, V value) bool` | Insert if missing, returns true if inserted |
| `del` | `(K key) bool` | Remove entry, returns true if found |
| `keys` | `() array[K]` | All keys as array |
| `vals` | `() array[V]` | All values as array |
| `arr` | `() array[tuple[K, V]]` | All entries as array of tuples |
| `clear` | `() void` | Remove all entries |
| `empty` | `() bool` | Check if map has no entries |
| `len` | `int` (field) | Number of entries |

## Random

`Random` is a pure-Velo reimplementation of `java.util.Random` — the same 48-bit
linear congruential generator (multiplier `0x5DEECE66D`, addend `0xB`, modulus
`2^48`), so a given seed produces **exactly** the same sequence as the JVM.

```velo
import "std/random"

Random r = new Random(42)  # seed it like java.util.Random(long)
r.nextInt()  # -1170105035 (same as the JVM)
r.nextIntBound(100)  # uniform in [0, 100)
r.nextBoolean()  # true / false
r.nextFloat()  # float in [0, 1)
r.setSeed(12345)  # reseed in place
```

Velo has no `double`, so the `nextDouble` and `nextGaussian` parts of
`java.util.Random` are intentionally omitted (as is `nextLong` — the generator
is written with 32-bit `int` arithmetic). Because Velo has no method
overloading, the bounded draw is named `nextIntBound` rather than overloading
`nextInt`.

### Full API Reference

| Method | Signature | Description |
|---|---|---|
| constructor | `(int seed)` | Seed the generator (like `java.util.Random(long)`) |
| `setSeed` | `(int seed) void` | Reseed in place |
| `nextInt` | `() int` | Next pseudorandom 32-bit `int` (full range) |
| `nextIntBound` | `(int bound) int` | Uniform `int` in `[0, bound)` |
| `nextBoolean` | `() bool` | Next pseudorandom `bool` |
| `nextFloat` | `() float` | Uniform `float` in `[0, 1)` |

## Encoding & Compression

Pure-Velo modules for byte-level work. Each exposes a class and a ready-made
global instance you can use directly after importing:

```velo
import "std/base64"
import "std/crc32"
import "std/deflate"
import "std/zip"
import "std/str"  # for .bytes()

# Base64 — text in, text out
str encoded = base64.encode("hello")  # "aGVsbG8="
str decoded = base64.decode(encoded)  # "hello"

# CRC-32 checksum of a byte array
int sum = crc32.checksum("hello".bytes())  # 907060870

# Raw DEFLATE compression (inflate with any standard zlib, wbits = -15)
array[byte] packed = rawDeflate.compress("hello".bytes())

# Build a single-entry ZIP archive
array[byte] archive = zip.create("hello".bytes(), "greeting.txt")
```

| Module | Global | Method |
|---|---|---|
| `std/base64` | `base64` | `encode(str) str`, `decode(str) str` |
| `std/crc32` | `crc32` | `checksum(array[byte]) int` |
| `std/deflate` | `rawDeflate` | `compress(array[byte]) array[byte]` |
| `std/zip` | `zip` | `create(array[byte] data, str entryName) array[byte]` |

---

[Previous: Modules and Imports ←](16-modules-and-imports.md) | [Next: Running Programs →](18-running-programs.md)

