# `janini`: A Simple and Lightweight Java Script Runner Server

Currently deployed at https://cs125.cs.illinois.edu/janini/.

## TODO

* Support other [Janino](http://janino-compiler.github.io/janino/) execution
  modes through submission flags, including class body and simple compilers.
* Try to integrate a standard Java in-memory compiler to avoid some of Janino's
  syntax limitations, maybe along the lines of [this
  tool](https://github.com/trung/InMemoryJavaCompiler). Benchmark to compare
  with Janino and potentially remove Janino entirely if performance is similar.
* Containerize for broader deployment.
* Performance testing using [artillery.io](https://artillery.io/).
