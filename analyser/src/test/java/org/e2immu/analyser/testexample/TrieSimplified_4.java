package org.e2immu.analyser.testexample;

import java.util.List;
import java.util.Map;

// semantic nonsense, infinite loop

public class TrieSimplified_4<T> {

    private final TrieNode<T> root = new TrieNode<>();

    private static class TrieNode<T> {
        List<T> data;
        Map<String, TrieNode<T>> map;

        @Override
        public String toString() {
            return "TrieNode{" +
                    "data=" + data +
                    ", map=" + map +
                    '}';
        }
    }

    public TrieNode<T> add() {
        TrieNode<T> node = root;

        // this loop causes trouble, even if it has nothing to do with node/return variable
        for (String s : new String[]{"a"}) {
        }

        return node;
    }
}
