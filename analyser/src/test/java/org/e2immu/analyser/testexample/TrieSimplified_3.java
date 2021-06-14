package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Nullable;

import java.util.List;
import java.util.Map;

public class TrieSimplified_3<T> {
    private final TrieNode<T> root = new TrieNode<>();

    @Container
    private static class TrieNode<T> {
        List<T> data;
        Map<String, TrieNode<T>> map;
    }

    @Nullable
    @NotModified
    private TrieNode<T> goTo(@NotModified String[] strings, int upToPosition) {
        TrieNode<T> node = root;
        for (int i = 0; i < upToPosition; i++) {
            if (node.map == null) return null;
            node = node.map.get(strings[i]);
            if (node == null) return null;
        }
        return node;
    }
}
