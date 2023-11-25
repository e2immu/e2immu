package org.e2immu.analyser.resolver.testexample;

import java.util.*;
import java.util.stream.Stream;

public class Lambda_18 {

    // original issue

    long method1(Map<String, ArrayList<ArrayList<String>>> map) {
        Stream<ArrayList<String>> stream = map.values().stream()
                .flatMap(List::stream)
                .filter(s -> s.size() > 1);
        return stream.count();
    }

    // variants

    long method2(Map<String, ArrayList<ArrayList<String>>> list) {
        Stream<ArrayList<String>> arrayListStream = list.values().stream().flatMap(List::stream);
        Stream<ArrayList<String>> stream = arrayListStream.filter(ids -> ids.size() > 1);
        return stream.count();
    }

    long method3(Map<String, List<String>> map) {
        Stream<String> stream = map.values().stream()
                .flatMap(List::stream)
                .filter(s -> s.length() > 1);
        return stream.count();
    }

    long method4(Map<String, List<List<String>>> map) {
        Stream<List<String>> stream = map.values().stream()
                .flatMap(List::stream)
                .filter(s -> s.size() > 1);
        return stream.count();
    }

    long method5(Map<String, List<ArrayList<String>>> map) {
        Stream<ArrayList<String>> stream = map.values().stream()
                .flatMap(List::stream)
                .filter(s -> s.size() > 1);
        return stream.count();
    }

    long method6(Map<String, ArrayList<ArrayList<String>>> map) {
        Collection<ArrayList<ArrayList<String>>> values = map.values();
        Stream<ArrayList<ArrayList<String>>> aasStream = values.stream();
        Stream<ArrayList<String>> stream = aasStream.flatMap(ArrayList::stream).filter(s -> s.size() > 1);
        return stream.count();
    }

    // smallest failing one?

    long method7(Map<String, ArrayList<ArrayList<String>>> map) {
        Collection<ArrayList<ArrayList<String>>> values = map.values();
        Stream<ArrayList<ArrayList<String>>> aasStream = values.stream();
        // List::stream instead of ArrayList::stream, and the current test fails
        Stream<ArrayList<String>> stream = aasStream.flatMap(List::stream).filter(s -> s.size() > 1);
        return stream.count();
    }

    long method8(Map<String, ArrayList<LinkedList<String>>> map) {
        Collection<ArrayList<LinkedList<String>>> values = map.values();
        Stream<ArrayList<LinkedList<String>>> aasStream = values.stream();
        // List::stream instead of ArrayList::stream, and the current test fails
        Stream<LinkedList<String>> stream = aasStream.flatMap(List::stream).filter(s -> s.size() > 1);
        return stream.count();
    }

    // works correctly
    long method9(Map<String, ArrayList<LinkedList<String>>> map) {
        Collection<ArrayList<LinkedList<String>>> values = map.values();
        Stream<ArrayList<LinkedList<String>>> aasStream = values.stream();
        // List::stream instead of ArrayList::stream, and the current test fails
        Stream<LinkedList<String>> stream = aasStream.flatMap(l -> l.stream()).filter(s -> s.size() > 1);
        return stream.count();
    }
}
