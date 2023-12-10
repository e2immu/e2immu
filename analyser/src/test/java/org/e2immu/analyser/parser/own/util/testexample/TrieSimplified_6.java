package org.e2immu.analyser.parser.own.util.testexample;

import org.e2immu.annotation.Modified;

import java.util.*;
import java.util.function.BiConsumer;

public class TrieSimplified_6<T> {

    private static class TrieNode<T> {
        List<T> data;
    }

    private static <T> void method(@Modified TrieNode<T> node,
                                   Stack<String> strings,
                                   BiConsumer<String[], List<T>> visitor) {
        if (node.data != null) {
            visitor.accept(strings.toArray(String[]::new), node.data);
        }
    }
}
