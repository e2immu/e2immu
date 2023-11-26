package org.e2immu.analyser.resolver.testexample;

import java.util.List;
import java.util.Map;

public class MethodReference_4 {

    private static void biConsumer(String s, List<Integer> list) {
        System.out.println(s + ": " + list.size());
    }

    public void method(Map<String, List<Integer>> map) {
        map.forEach(MethodReference_4::biConsumer);
    }

    // ---------
    private static String biFunction(String s1, String s2) {
        return s1.length() > s2.length() ? s1 : s2;
    }

    private static Map.Entry<String, String> biFunction(Map.Entry<String, String> e1, Map.Entry<String, String> e2) {
        return e1.getKey().length() > e2.getKey().length() ? e1 : e2;
    }

    public int method2(Map<String, String> map) {
        return map.entrySet().stream().reduce(MethodReference_4::biFunction).orElseThrow().getKey().length();
    }
}

