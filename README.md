# Velo Lang
Simple. Embeddable. Yours.

Velo Lang is a functional, strict-typed compilable programming language. It runs on top of lightweight stack virtual machine.

![Cache icon](/velo-logo.png)

### Intro
**Hello, World!** This is simple Velo program
```
auto hello = "Hello, World!";
println(hello);
```
**Data types** There are several data types supported for now
```
str s = "s";
bool b = true;
int i = 1;
float f = 3.0;
auto l = arrayOf[array[int]]();
auto n = func() void {};
auto p = pairOf[int,str](1, "second");
```
**Functions and lambdas.** Velo lang supports functions
```
func functionName(int a, int b) int {
    a + b;
};
println(functionName(1, 2));
```
... and lambdas
```
auto lambda = func(int a, int b) int {
    a + b;
};
println(lambda(1, 2));
```
Recursive calls are also supported
```
func fib(int n) int {
    if n < 2 then n else fib(n - 1) + fib(n - 2);
};
println(fib(15));
```
**Conditional operators** If-then-else
```
int a = 5;

if a == 2 then "two" else "not two";

auto s = if a == 2 then {
    "two"
} else {
    "not two"
};
println(s);
```
**Cycles**
```
int i = 1;
while (i <= 5) {
    println(i);
    i = i + 1;
};
```
**Arrays**
```
auto s = arrayOf[int](37, 58, 25, 17, 19);
println(s.len);           # 5
println(s[3]);            # 17
println(s.sub(1, 4)[1]);  # 25
auto ns = s.map(
    func(int i, int v) int {
        i + v
    }
);
println(ns[3]);           # 20
```
**Dictionary**
```
dict[int:str] d = dictOf[int:str](
    1:"a",
    2:"b",
    3:"c"
);
d.set(5, "e");
println(d.del(2));        # true
println(d.del(20));       # false
println(d.len);           # 3
println(d[5]);            # e
println(d.key(5));        # true
println(d.key(50));       # false
println(d.val("c"));      # true
println(d.val("r"));      # false
println(d.keys[0]);       # <first key>
println(d.vals[1]);       # <second value>
```
**Strings**
```
str s = "Test String";
println(s.len);        # 11
println(s.sub(5, 11)); # String
```
**Pairs**
```
auto p = pairOf[int,str](1, "second");
println(p.first);  # 1
println(p.second); # second
```
**Classes**
```
class Random(int seed) {
    int a = 252149039;    # These Values for a and c are the actual values found
    int c = 11;           # in the implementation of java.util.Random(), see link
    int previous = 0;

    func setSeed(int seed) void {
        previous = seed;
    };

    func next() int {
        int r = a * previous + c;
        # Note: typically, one chooses only a couple of bits of this value, see link
        previous = r;
        r;
    }
};

auto random = Random(12345);
int i = 5;
while (i > 0) {
    auto r = random.next();
    println(r);
    i = i - 1;
};
```

### Run program

#### Sample from resources
You can run sample programs from resources by adding argument, started with `res://` and sample name. For example: `res://primes.vel`

#### Program from file
To compile and run program from file you need to specify file path started with `file://` and sample name. For example: `file://home/user/primes.vel`


### License
    MIT License
    
    Copyright (c) 2024 Igor Solkin
    
    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:
    
    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.
    
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
