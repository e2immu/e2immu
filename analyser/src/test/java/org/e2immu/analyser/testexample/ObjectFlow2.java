package org.e2immu.analyser.testexample;

import org.e2immu.analyser.util.SetUtil;

import java.util.HashSet;
import java.util.Set;

public class ObjectFlow2 {

    /*
    flow of of#0 (param s): pass on + access substring
    flow of res: creation, modify, modify again in new flow object + return
    flow of sMinus = result of call + pass on

    flow of "abc"
    flow of set1 = result of call + access
    flow of "def"
    flow of set2 = result of call + access
    flow of set3 = result of call
     */

    static Set<String> of(String s) {
        Set<String> res = new HashSet<>();
        res.add(s);
        String sMinus = s.substring(1);
        res.add(sMinus);
        return res;
    }

    static final Set<String> set1 = of("abc");
    static final Set<String> set2 = of("def");
    static final Set<String> set3 = SetUtil.immutableUnion(set1, set2);
}
