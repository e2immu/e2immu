package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

import java.util.HashMap;
import java.util.Map;

/*
In add(), the for loop AND the if-else (including the else!) need to be present.
(Looks like there must be at least one assignment to node.map in each branch of the if-else.)
NOTE: lines have been removed; as with most of the __Simplified_ tests, semantically this code is meaningless!!
 */
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
                    newTrieNode = new TrieNode<>(); // comment out this line -> all is fine
                    node.map.put(s, newTrieNode); // ditto
                }
            }
        }
        return node;
    }
}
