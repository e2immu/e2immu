package org.e2immu.intellij.highlighter.example;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.NotModified;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

public class Example1 {

    private final List<String> strings;

    public Example1() {
        strings = new ArrayList<>();
    }

    @Contract()
    void add(String message) {
        strings.add("message: "+message);
    }

    String getFirst() {
        System.out.println("Debug");
        if(strings.isEmpty()) return "Nothing!";
        return strings.get(0);
    }
}
