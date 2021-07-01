package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

import java.util.*;

public class TrieSimplified_5<T> {

    private final TrieNode<T> root = new TrieNode<>();

    private static class TrieNode<T> {
        List<T> data;
        Map<String, TrieNode<T>> map;
    }

    @NotNull
    public TrieNode<T> add(String[] strings, T data) {
        TrieNode<T> node = root;
        for (String s : strings) {
            TrieNode<T> newTrieNode;
            if (node.map == null) {
                node.map = new HashMap<>();
                newTrieNode = new TrieNode<>();
                node.map.put(s, newTrieNode); // 1.0.1.0.2
            } else {
                newTrieNode = node.map.get(s);
                if (newTrieNode == null) {
                    newTrieNode = new TrieNode<>();
                    node.map.put(s, newTrieNode);
                }
            }
            node = newTrieNode;
        }
        if (node.data == null) node.data = new LinkedList<>();
        node.data.add(Objects.requireNonNull(data));
        return node;
    }
}
