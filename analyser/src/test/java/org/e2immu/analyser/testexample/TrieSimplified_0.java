package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

import java.util.HashMap;
import java.util.Map;

public class TrieSimplified_0<T> {

    private final TrieNode<T> root = new TrieNode<>();

    private static class TrieNode<T> {
        Map<String, TrieNode<T>> map;
    }

    @NotNull
    public TrieNode<T> add(@NotNull String[] strings) {
        TrieNode<T> node = root;
        for (String s : strings) {
            TrieNode<T> newTrieNode;
            if (node.map == null) {
            //    node.map = new HashMap<>();
            } else {
                newTrieNode = node.map.get(s);
                if (newTrieNode == null) {
                    newTrieNode = new TrieNode<>();
                    node.map.put(s, newTrieNode); // null ptr
                }
            }
        }
        return node;
    }


    @NotNull
    public TrieNode<T> addSynchronized(@NotNull String[] strings) {
        TrieNode<T> node = root;
        for (String s : strings) {
            TrieNode<T> newTrieNode;
            synchronized (root) {
                if (node.map == null) {
                    //    node.map = new HashMap<>();
                } else {
                    newTrieNode = node.map.get(s);
                    if (newTrieNode == null) {
                        newTrieNode = new TrieNode<>();
                        node.map.put(s, newTrieNode); // NO null ptr
                    }
                }
            }
        }
        return node;
    }
}
