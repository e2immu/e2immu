package org.e2immu.analyser.testexample;

import java.util.List;
import java.util.Map;

// semantic nonsense, infinite loop

public class TrieSimplified_2<T> {

    private final TrieNode<T> root = new TrieNode<>();

    private static class TrieNode<T> {
        List<T> data;
        Map<String, TrieNode<T>> map;
    }

    public TrieNode<T> add(String[] strings) {
        TrieNode<T> node = root;
        for (String s : strings) {
        }
        return node;
    }
}
