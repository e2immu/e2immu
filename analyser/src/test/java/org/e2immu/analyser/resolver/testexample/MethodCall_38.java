package org.e2immu.analyser.resolver.testexample;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

public class MethodCall_38 {

    private ArrayList<String> list = null;

    public void start() {
        list = new ArrayList<>();
    }

    public void update(OutputStream outputStream) {
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(outputStream);
            for (int i = 0; i < list.size(); i++) {
                printWriter.write(list.get(i));
            }
        } finally {
            printWriter.close();
        }
    }
}
