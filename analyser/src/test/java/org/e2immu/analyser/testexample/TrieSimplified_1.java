package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Map;

/*
Variant on _0; we have 2 null ptr warnings. Completely incorrect, I'd say.
 */
public class TrieSimplified_1<T> {

    @NotModified
    private final TrieNode<T> root = new TrieNode<>();

    private static class TrieNode<T> {
        Map<String, TrieNode<T>> map;
    }

    @NotNull
    @NotModified
    public TrieNode<T> add(@NotNull String s) {
        if (root.map == null) {
            //
        } else {
            TrieNode<T> newTrieNode = root.map.get(s);
            if (newTrieNode == null) {
                newTrieNode = new TrieNode<>();
                root.map.put(s, newTrieNode);
            }
        }
        return root;
    }

    @NotNull
    @NotModified
    public synchronized TrieNode<T> addSynchronized(@NotNull String s) {
        if (root.map == null) {
            //
        } else {
            TrieNode<T> newTrieNode = root.map.get(s);
            if (newTrieNode == null) {
                newTrieNode = new TrieNode<>();
                root.map.put(s, newTrieNode);
            }
        }
        return root;
    }
}
