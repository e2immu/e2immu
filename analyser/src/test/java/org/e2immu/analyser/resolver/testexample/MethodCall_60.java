package org.e2immu.analyser.resolver.testexample;

public class MethodCall_60 {

    static class ArrayList<I> extends java.util.ArrayList<I> {
    }

    ArrayList<String[]> list;

    String accept(String[][] nameValuePairs) {
        return nameValuePairs.length + "?";
    }

    String method() {
        String[][] a = new String[list.size()][2];
        return accept(list.toArray(a));
    }
}
