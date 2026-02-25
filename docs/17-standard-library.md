# Standard Library

## Boolean Extensions

Extension functions for boolean type:

```velo
include "lang/bool.vel";

bool flag = true;
str s = flag.str;        # "true" or "false" (parentheses optional)
int i = flag.int;        # 1 or 0
bool neg = flag.not;     # Logical NOT
```

## Integer Extensions

Extension functions for integer type:

```velo
include "lang/int.vel";

int x = -42;
int absVal = x.abs();           # 42
int negVal = x.neg();           # -42 (always negative)
str hex = (255).format(16);     # "ff"
str bin = (10).format(2);       # "1010"
```

## String Extensions

Extension functions for string type:

```velo
include "lang/str.vel";

str text = "Hello";
array[byte] bytes = text.bytes();    # [72, 101, 108, 108, 111]
```

## Array Extensions

Extension functions for array type:

```velo
include "lang/array.vel";

array[byte] bytes = new array[byte]{72, 101, 108, 108, 111};
str text = bytes.str();              # "Hello"
```

Both extensions enable roundtrip conversion: `text.bytes().str() == text`.

## Terminal

Class for terminal operations:

```velo
include "lang/terminal.vel";

Terminal term = new Terminal();
term.print("Hello");           # Output without newline
term.println("World");         # Output with newline
str input = term.input();      # Read string from console
```

## Time

Class for time operations:

```velo
include "lang/time.vel";

Time time = new Time();
time.sleep(1000);              # Sleep for 1000 milliseconds
int unixTime = time.unix();     # Unix timestamp in seconds
```

## Http

Class for making HTTP requests:

```velo
include "lang/http.vel";

Http http = new Http();
str response = http.get("https://example.com");
int status = http.statusCode();

# POST request
str jsonBody = "{\"key\": \"value\"}";
str postResponse = http.post("https://api.example.com/data", jsonBody, "");
```

## FileSystem

Class for file system operations:

```velo
include "lang/filesystem.vel";

FileSystem fs = new FileSystem();

# Reading and writing strings
fs.write("file.txt", "Content");
str content = fs.read("file.txt");
fs.append("file.txt", "\nMore content");

# Reading and writing byte arrays
include "lang/str.vel";
array[byte] data = "binary data".bytes();
fs.writeBytes("file.bin", data);
array[byte] loaded = fs.readBytes("file.bin");
fs.appendBytes("file.bin", data);

# Checks
bool exists = fs.exists("file.txt");
bool isFile = fs.isFile("file.txt");
bool isDir = fs.isDir("directory");

# Directory operations
fs.mkdir("new_dir");
array[str] files = fs.list(".");

# File operations
fs.copy("source.txt", "dest.txt");
fs.move("old.txt", "new.txt");
fs.delete("file.txt");

# Information
int fileSize = fs.size("file.txt");
```

## Socket

Class for TCP socket communication. Supports both client and server modes.

### Client

```velo
include "lang/socket.vel";

Socket sock = Socket();
sock.connect("127.0.0.1", 9876);

sock.writeLine("Hello!");
str reply = sock.readLine();

sock.close();
```

### Server

```velo
include "lang/socket.vel";

Socket srv = Socket();
srv.bind(9876);

Socket client = srv.accept();         # blocks until a client connects
str msg = client.readLine();
client.writeLine("Echo: " + msg);

client.close();
srv.close();
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
include "lang/map.vel";

Map[str, int] ages = new Map[str, int]();

# Operator syntax
ages["Alice"] = 30;
ages["Bob"] = 25;

# Method syntax
ages.put("Charlie", 35);
```

### Lookup

```velo
# get() returns ptr[V] — null if key is not found
ptr[int] val = ages["Alice"];
if (val != null) {
    int age = val.val;              # 30
};

# getOrDefault() returns V directly, with a fallback
int age = ages.getOrDefault("Eve", 0);  # 0
```

### Checking and Conditional Insert

```velo
bool has = ages.containsKey("Bob");     # true
bool empty = ages.empty();              # false
int count = ages.size;                  # number of entries

# putIfAbsent — inserts only if key is missing, returns true if inserted
bool added = ages.putIfAbsent("Diana", 28);     # true
bool again = ages.putIfAbsent("Diana", 99);     # false, value stays 28
```

### Removing Entries

```velo
bool removed = ages.remove("Charlie");  # true
bool noop = ages.remove("Charlie");     # false (already gone)
```

### Iterating

```velo
array[str] k = ages.keys();     # array of all keys
array[int] v = ages.values();   # array of all values

int i = 0;
while (i < k.len) {
    # process k[i] and v[i]
    i = i + 1;
};
```

### Clearing

```velo
ages.clear();
# ages.size is 0, ages.empty() is true
```

### Automatic Resizing

The map starts with capacity 16 and doubles when the load factor exceeds 75%. All entries are rehashed into the new table automatically. This is transparent — no API changes are needed:

```velo
Map[int, int] big = new Map[int, int]();
int n = 0;
while (n < 100) {
    big[n] = n * n;
    n = n + 1;
};
# capacity has grown automatically, all 100 entries are accessible
```

### Full API Reference

| Method / Operator | Signature | Description |
|---|---|---|
| `operator []` | `(K key) ptr[V]` | Lookup by key (null if missing) |
| `operator []=` | `(K key, V value) void` | Insert or update |
| `put` | `(K key, V value) void` | Insert or update |
| `get` | `(K key) ptr[V]` | Lookup (null if missing) |
| `getOrDefault` | `(K key, V defaultValue) V` | Lookup with fallback |
| `containsKey` | `(K key) bool` | Check key existence |
| `putIfAbsent` | `(K key, V value) bool` | Insert if missing, returns true if inserted |
| `remove` | `(K key) bool` | Remove entry, returns true if found |
| `keys` | `() array[K]` | All keys as array |
| `values` | `() array[V]` | All values as array |
| `clear` | `() void` | Remove all entries |
| `empty` | `() bool` | Check if map has no entries |
| `size` | `int` (field) | Number of entries |

---

[Previous: Modules and Includes ←](16-modules-and-includes.md) | [Next: Running Programs →](18-running-programs.md)

