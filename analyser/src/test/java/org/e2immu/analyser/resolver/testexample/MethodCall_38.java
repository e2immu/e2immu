package org.e2immu.analyser.resolver.testexample;

import java.io.IOException;

import java.io.Writer;

public class MethodCall_38 {

    public static class ArrayList<I> extends java.util.ArrayList<I> {
    }

    private ArrayList<String> list;

    public void update(Writer writer) throws IOException {
        // important for this test is to have the list.get(i) as the argument to write
        // noinspection ALL
        for (int i = 0; i < list.size(); i++) {
            writer.write(list.get(i));
        }
    }

    public void setList(ArrayList<String> list) {
        this.list = list;
    }
}
