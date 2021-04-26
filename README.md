# e2immu: Practical Immutability for Java

More information on the project website [https://www.e2immu.org](https://www.e2immu.org).

## About the project

_e2immu_ is a static code analyser for modern Java. As for every code analyser, it aims to help you write better code by
making you aware of unwanted constructs, possible causes for exceptions etc. Even if it includes many "standard"
warnings, _e2immu_ is not a general code analyser:
it focuses on modification and immutability. It is able to detect that classes are immutable in practice, or not, and
why they are not.

It also provides a practical definition of such immutability, called "effective immutability". It can detect that
classes are _eventually_ immutable, i.e., they become effectively immutable after an initialisation phase. It provides
some out-of-the-box classes that help with making your own code eventually immutable.

Ideally, the results of the analyser are shown directly in your programming environment. At the moment, a plugin for
IntelliJ is in development. Support for Eclipse and Visual Studio Code are planned.

The following links point towards in-depth reading material:

- [The Road to Immutability](https://www.e2immu.org/docs/road-to-immutability.html) A gentle introduction to the concepts of effective and eventual immutability
- [_e2immu_ manual](https://www.e2immu.org/docs/manual.html) The manual of the _e2immu_ project.


## Current status

**TL;DR**: The _e2immu_ analyser is not yet ready for general use. Please follow the project on GitHub to stay in touch
if you "just" want to use the analyser.

All core concepts are solid, however. There is a full tutorial about the how and why of effective and eventual
immutability, and all related properties. There are more than 250 code snippets running green, so there is a ton of
example material already present. **You can already improve your code by introducing eventually immutable constructs. The
analyser is "nothing but" a support tool to help you along.**

In descending order of priority, we are now focusing on

- fixing bugs and crashes;
- enabling the analyser to process its own code base. This partially depends on advances in
  the [JavaParser](http://javaparser.org/) library, since the analyser uses Java 16 syntax and constructs;
- providing one working, informative IDE plugin;
- determining a selection of the most important Java APIs, and providing annotations for them.

As soon as these four milestones have been met, the analyser will be promoted as "ready for general use".
