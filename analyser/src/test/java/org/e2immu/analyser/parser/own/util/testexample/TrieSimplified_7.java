package org.e2immu.analyser.parser.own.util.testexample;

import org.e2immu.annotation.Modified;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.BiConsumer;

public class TrieSimplified_7<T> {

    private static class TrieNode<T> {
        List<T> data; // always null
    }

    private static <T> void method(@Modified TrieNode<T> node,
                                   Stack<String> strings,
                                   BiConsumer<String[], List<T>> visitor) {
        if (node.data != null) {
            visitor.accept(strings.toArray(String[]::new), node.data);
        }
    }
}
