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

- [The Road to Immutability](http://) A gentle introduction to the concepts of effective and eventual immutability
- [_e2immu_ manual](http://) The manual of the _e2immu_ project
- [_e2immu_ - Effective and Eventual Immutability](http://) A PowerPoint presentation for those who prefer their text in
  bullet points.

## Current status

**TL;DR**: The _e2immu_ analyser is not yet ready for general use. Please follow the project on GitHub to stay in touch
if you "just" want to use the analyser.

All core concepts are solid, however. There is a full tutorial about the how and why of effective and eventual
immutability, and all related properties. There are more than 200 code snippets running green, so there is a ton of
example material already present. You can already improve your code by introducing eventually immutable constructs. The
analyser is "nothing but" a support tool to help you along.

In descending order of priority, we are now focusing on

- fixing bugs and crashes;
- enabling the analyser to process its own code base. This partially depends on advances in
  the [JavaParser](http://javaparser.org/) library, since the analyser uses Java 15 syntax and constructs;
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

## Project Governance

The e2immu project is sponsored by IBTECH BV, a limited liability company under Belgian law. The project welcomes
contributions from the community.

### Goal

IBTECH aims for the e2immu analyser to become a solid, dependable tool in promoting and enforcing immutability
constraints in recent versions of Java. Until this goal is reached, it will try to avoid widening the scope of the
analyser in the direction of, for example, a general code analyser, or the ability to analyser other programming
languages.

### Contributions

All contributors are expected to sign a [Contributor License Agreement](http://) (CLA) that protects their intellectual
property rights, and at the same time gives IBTECH the legal means to manage the project.

The CLA contains a provision that prevents IBTECH from making the license more restrictive. It does allow IBTECH to make
the license more permissive (e.g. from LGPLv3 to Apache License v2).

Git and Github will be used to track individual contributions.

### Copyright

At the point of introduction of the project to the open source community, all source code is "(C) IBTECH BV and Bart
Naudts, 2020-2021."

Other contributors will be mentioned explicitly for major contributions, as in
"(C) IBTECH BV and Jane Doe"
or, for minor contributions,
"(C) IBTECH BV and contributors to the e2immu project".

## Covenant

The project will be managed by IBTECH and community project leads according the professional and civil collaboration
rules described in the [Contributor Covenant](https://www.contributor-covenant.org/version/2/0/code_of_conduct/).

## How to contribute

Once you have agreed to the CLA, there are many parts of the analyser ecosystem where you can make valuable
contributions, according to your specialisation, interests or capabilities.

Always use issues to attach a merge request, and try to be as concise as possible in defining issues.

### Bug reports, minor fixes, and test examples

Especially now in the early phase, this area offers rich pickings, as the analyser is not that stable yet. Make sure
you're testing against the latest version of the analyser.

It is important to write test examples that are as concise as possible.

### Library annotations

The topic of library annotations can be worked on indefinitely. Once the analyser is sufficiently stable, it can propose
an annotated API, which can then be improved manually.

### User interfaces

This topic can generally be split into two important aspects: design, and the different implementations.

The design aspect centers around the most informative and least intrusive way of conveying the analyser's information.

Implementations focus on the different IDEs, and the infrastructure necessary to run the analyser in the background.

### Documentation and tutorials

The educational aspect of the analyser is important. I'd almost say there cannot be sufficient material to promote good
software engineering practices.

Secondly, the analyser is a living piece of code, and catching up with the technical specification is a task in itself.

Translations of key documents are welcomed as well, especially to promote the immutability concepts to aspiring or
starting software developers. No one should be constrained in their programming skills by a lack of understanding of
English.

Finally, because many non-native speakers contribute in their second or third language, we welcome improved re-phrasings
in case the text deviates too much from natural language.

### The analyser core

Contributing to the analyser core is not for the faint of heart, and probably requires a decent investment in time and
effort before you can make meaningful improvements or extensions.

## Coding Guidelines

Contributions are expected to follow the coding guidelines promoted by the e2immu project itself as described in
the [Road to Immutability](http://). This includes:

- making use of immutable or eventually immutable types as much as possible
- avoiding types that cannot be marked `@Container`

Code formatting follows the default rules of the IntelliJ IDEA. IntelliJ's green tick box is mandatory.
