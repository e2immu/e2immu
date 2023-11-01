package org.e2immu.analyser.resolver.testexample;

import org.e2immu.analyser.resolver.testexample.a.Resource;

import static org.e2immu.analyser.resolver.testexample.Annotations_4.XX;

@Resource(name = XX, lookup = Annotations_4.ZZ, authenticationType = Resource.AuthenticationType.CONTAINER)
public class Annotations_4 {
    static final String XX = "xx";
    static final String ZZ = "zz";
}

