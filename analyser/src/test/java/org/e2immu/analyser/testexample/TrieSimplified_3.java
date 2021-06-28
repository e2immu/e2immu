package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.E1Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Nullable;

import java.util.List;
import java.util.Map;

@E1Container
public class TrieSimplified_3<T> {
    private final TrieNode<T> root = new TrieNode<>();

    @Container
    private static class TrieNode<T> {
        Map<String, TrieNode<T>> map;
    }

    @Nullable
    @NotModified
    private TrieNode<T> goTo(String[] strings, int upToPosition) {
        TrieNode<T> node = root;
        for (int i = 0; i < upToPosition; i++) {
            if (node.map == null) return null; // eval to true, always return null
            node = node.map.get(strings[i]); // statement should be unreachable from it 1
            if (node == null) return null; // unreachable from it 1
        }
        return node;
    }
}
