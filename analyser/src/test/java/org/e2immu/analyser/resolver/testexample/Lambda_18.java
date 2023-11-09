package org.e2immu.analyser.resolver.testexample;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Lambda_18 {

    // problem with result of .flatMap: Stream<E> FIXME
   /* long method(Map<String, ArrayList<ArrayList<String>>> allInsuranceProductCodes) {
        Stream<ArrayList<String>> stream = allInsuranceProductCodes.values().stream()
                .flatMap(List::stream)
                .filter(theProductComponentIds -> theProductComponentIds.size() > 1);
        return stream.count();
    }*/
}
