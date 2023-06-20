package org.e2immu.analyser.parser.functional.testexample;

import org.e2immu.annotation.NotModified;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/*
same2 causes an infinite loop (20230425)
 */
public abstract class Lambda_15 {

    @NotModified
    protected abstract boolean testString(String s);

    public List<String> same1(List<String> list) {
        Predicate<String> stringPredicate = s -> testString(s);
        return list.stream().filter(stringPredicate).toList();
    }

    public List<String> same2(List<String> list) {
        Predicate<String> stringPredicate = new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return test(s);
            }
        };
        return list.stream().filter(stringPredicate).toList();
    }

    public List<String> same3(List<String> list) {
        return list.stream().filter(this::testString).toList();
    }

    public List<String> same4(List<String> list) {
        List<String> result = new ArrayList<>(list.size());
        for (String s : list) {
            if (testString(s)) result.add(s);
        }
        return List.copyOf(result);
    }
}
