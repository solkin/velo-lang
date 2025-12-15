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

# Reading and writing
fs.write("file.txt", "Content");
str content = fs.read("file.txt");
fs.append("file.txt", "\nMore content");

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

---

[Previous: Modules and Includes ←](16-modules-and-includes.md) | [Next: Running Programs →](18-running-programs.md)

