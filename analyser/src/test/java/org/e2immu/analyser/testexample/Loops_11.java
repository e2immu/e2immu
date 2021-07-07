package org.e2immu.analyser.testexample;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Loops_11 {

    // semantic nonsense, but used to verify which variables are assigned in the loop
    public static Map<String, String> method(Set<String> queried, Map<String, String> map) {
        Map<String, String> result = new HashMap<>(); // NOT ASSIGNED
        Instant now = LocalDateTime.now().toInstant(ZoneOffset.UTC); // NOT ASSIGNED
        int count = 0; // ASSIGNED
        for (Map.Entry<String, String> entry : map.entrySet()) { // LOOP VARIABLE, NOT ASSIGNED
            String key = entry.getKey(); // local variable
            if (!queried.contains(key)) { // NOT ASSIGNED
                String container = entry.getValue(); // local variable
                if (container != null && container.compareTo(now.toString()) > 0) {
                    result.put(key, container);
                    count++;
                }
            }
        }
        System.out.println(count);
        return result;
    }
}
