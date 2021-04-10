# e2immu: Practical Immutability for Java

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

- [The Road to Immutability](https://www.e2immu.org/road-to-immutability/000-main.html) A gentle introduction to the concepts of effective and eventual immutability
- [_e2immu_ manual](https://www.e2immu.org/manual/000-main.html) The manual of the _e2immu_ project.

More information on the project website [https://www.e2immu.org](https://www.e2immu.org).

## Current status

**TL;DR**: The _e2immu_ analyser is not yet ready for general use. Please follow the project on GitHub to stay in touch
if you "just" want to use the analyser.

All core concepts are solid, however. There is a full tutorial about the how and why of effective and eventual
immutability, and all related properties. There are more than 200 code snippets running green, so there is a ton of
example material already present. **You can already improve your code by introducing eventually immutable constructs. The
analyser is "nothing but" a support tool to help you along.**

In descending order of priority, we are now focusing on

- fixing bugs and crashes;
- enabling the analyser to process its own code base. This partially depends on advances in
  the [JavaParser](http://javaparser.org/) library, since the analyser uses Java 16 syntax and constructs;
- providing one working, informative IDE plugin;
- determining a selection of the most important Java APIs, and providing annotations for them.

As soon as these four milestones have been met, the analyser will be promoted as "ready for general use".

Please read the section on [How to contribute](#HowToContribute) to see how you can help advance the `e2immu` code base.

## How to install, use, build

See the separate documents [Installing e2immu](/INSTALL.md), [Building e2immu](/BUILD.md) and

## License: Open Source

The e2immu project (the code analyser and all its related libraries, tools, etc.) are licensed under the LGPLv3, the GNU
Lesser General Public License.

Quoting from [Wikipedia](https://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License): The GNU Lesser General Public
License is a  [free-software license](https://en.wikipedia.org/wiki/Free-software_license)  published by
the  [Free Software Foundation](https://en.wikipedia.org/wiki/Free_Software_Foundation)  (FSF). The license allows
developers and companies to use and integrate a software component released under the LGPL into their own (
even  [proprietary](https://en.wikipedia.org/wiki/Proprietary_software)) software without being required by the terms of
a strong  [copyleft](https://en.wikipedia.org/wiki/Copyleft)  license to release the source code of their own
components. However, any developer who modifies an LGPL-covered component is required to make their modified version
available under the same LGPL license. For proprietary software, code under the LGPL is usually used in the form of
a  [shared library](https://en.wikipedia.org/wiki/Shared_library), so that there is a clear separation between the
proprietary and LGPL components. The LGPL is primarily used
for  [software libraries](https://en.wikipedia.org/wiki/Library_(computing)), although it is also used by some
stand-alone applications.

The most immediate consequence is that

*you are completely free to use the analyser where and when you like, insert e2immu annotations in your proprietary
code, and build on the immutability stepping stones such as the `SetOnce` type*.
