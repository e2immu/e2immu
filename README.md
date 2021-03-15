# e2immu: Practical Immutability for Java


## License: Open Source

The e2immu project (the code analyser and all its related libraries, tools, etc.) are licensed under the LGPLv3, the GNU Lesser General Public License.

Quoting from [Wikipedia](https://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License): The GNU Lesser General Public License is a  [free-software license](https://en.wikipedia.org/wiki/Free-software_license)  published by the  [Free Software Foundation](https://en.wikipedia.org/wiki/Free_Software_Foundation)  (FSF). The license allows developers and companies to use and integrate a software component released under the LGPL into their own (even  [proprietary](https://en.wikipedia.org/wiki/Proprietary_software)) software without being required by the terms of a strong  [copyleft](https://en.wikipedia.org/wiki/Copyleft)  license to release the source code of their own components. However, any developer who modifies an LGPL-covered component is required to make their modified version available under the same LGPL license. For proprietary software, code under the LGPL is usually used in the form of a  [shared library](https://en.wikipedia.org/wiki/Shared_library), so that there is a clear separation between the proprietary and LGPL components. The LGPL is primarily used for  [software libraries](https://en.wikipedia.org/wiki/Library_(computing)), although it is also used by some stand-alone applications.

The most immediate consequence is that 

  *you are completely free to use the analyser where and when you like, insert e2immu annotations in your proprietary code, and build on the immutability stepping stones such as the `SetOnce` type*.


## Project Governance

The e2immu project is sponsored by IBTECH BV, a limited liability company under Belgian law. 
The project welcomes contributions from the community.

### Goal

IBTECH aims for the e2immu analyser to become a solid, dependable tool in promoting and enforcing immutability constraints in recent versions of Java. 
Until this goal is reached, it will try to avoid widening the scope of the analyser in the direction of, for example, a general code analyser, or the ability to analyser other programming languages.


### Contributions

All contributers are expected to sign a [Contributer License Agreement](http://) (CLA) that protects their intellectual property rights, and at the same time gives IBTECH the legal means to manage the project.

The CLA contains a provision that prevents IBTECH from making the license more restrictive. It does allow IBTECH to make the license more permissive (e.g. from LGPLv3 to Apache License v2).

Git and Github will be used to track individual contributions.


### Copyright

At the point of introduction of the project to the open source community, all source code is "(C) IBTECH BV and Bart Naudts, 2020-2021."

Other contributers will be mentioned explicitly for major contributions, as in 
"(C) IBTECH BV and Jane Doe"
or, for minor contributions,
"(C) IBTECH BV and contributors to the e2immu project".


## Convenant 

The probject will be managed by IBTECH and community project leads according the professional and civil collaboration rules described in the [Contributer Convenant](https://www.contributor-covenant.org/version/2/0/code_of_conduct/).


## How to contribute

Once the CLA is out of the way, there are many parts of the analyser ecosystem where you can make valuable contributions, according to your specialisation or interests.

Always use issues to attach a merge request, and try to be as concise as possible in defining issues.


### Bug reports, minor fixes, and test examples

Especially now in the early phase, this area offers rich pickings, as the analyser is not that stable yet. Make sure you're testing againgst the latest version of the analyser.

It is important to write test examples that are as concise as possible.

### Library annotations

The topic of library annotations can be worked on indefinitely.
Once the analyser is sufficiently stable, it can propose an annotated API, which can then be improved manually.


### User interfaces

This topic can generally be split into two important aspects: design, and the different implementations.

The design aspect centers around the most informative and least intrusive way of conveying the analyser's information.

Implementations focus on the different IDEs and the infrastructure necessary to run the analyser in the background.


### Documentation and tutorials

The educational aspect of the analyser is important. I'd almost say there cannot be sufficient material to promote good software engineering practices.

Secondly, the analyser is a living piece of code, and catching up with the technical specification is a task in itself. 

Translations of key documents are welcomed as well, especially to promote the immutability concepts to aspiring or starting software developers. No one should be constrained in their programming skills by a lack of understanding of English.

Finally, because many non-native speakers contribute in their second or third language, we welcome improved rephrasings in case the text deviates too much from natural language.


### The analyser core

Contributing to the analyser core is not for the faint of heart, and probably requires a decent investment in time before you can make meaningful improvements or extensions.



## Coding Guidelines

Contributions are expected to follow the coding guidelines promoted by the e2immu project itself as described in the [Road to Immutability](http://). This includes:

- making use of immutable or eventually immutable types as much as possible
- avoiding types that cannot be marked `@Container`


Code formatting follows the default rules of the IntelliJ IDEA.
IntelliJ's green tick box is mandatory.
