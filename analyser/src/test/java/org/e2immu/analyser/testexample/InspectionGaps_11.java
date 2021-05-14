package org.e2immu.analyser.testexample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/*
Arrays.ArrayList competes with ArrayList. It is clear that the latter should win!
 */
public class InspectionGaps_11 {
    private final String s1;
    private final String s2;
    private final List<String> list = new ArrayList<String>();

    public InspectionGaps_11(String s1, List<String> list, String s2) {
        this.s1 = s1;
        this.s2 = s2;
        this.list.addAll(list);
    }

    public Stream<String> getStream() {
        return Arrays.stream(new String[]{s1, s2});
    }

    public InspectionGaps_11 of(String s1, List<String> list, String s2) {
        return new InspectionGaps_11(s1, createUnmodifiable(list), s2);
    }

    private static <T> List<T> createUnmodifiable(List<? extends T> list) {
        return new ArrayList<>(list);
    }

    public List<String> getList() {
        return list;
    }

    public String getS1() {
        return s1;
    }

    public String getS2() {
        return s2;
    }
}
