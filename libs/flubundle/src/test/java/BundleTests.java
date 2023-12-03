import arc.files.Fi;
import com.ospx.flubundle.Bundle;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BundleTests {
    @Test
    public void testMultipleEqualLocales() {
        var bundle = Bundle.INSTANCE;
        bundle.addSource(new Fi("src/test/resources/bundles"));

        assertEquals("Hello, Billy!", bundle.format(new Locale("ru"), "hello-user",
                Map.of("userName", "Billy")));

        assertEquals("Hello, Billy!", bundle.format(new Locale("ru"),"hello-user2",
                Map.of("userName", "Billy")));

        assertEquals("Hello, Billy!", bundle.format(new Locale("ru"),"hello-user",
                Bundle.args("userName", "Billy")));

        assertEquals("Hello, Billy!", bundle.format(new Locale("ru"),"hello-num-user",
                Bundle.numArgs("Billy")));
    }

    @Test
    public void performanceTest() {
        var bundle = Bundle.INSTANCE;
        bundle.addSource(new Fi("src/test/resources/bundles"));

        var start = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            bundle.format(new Locale("ru"), "hello-user",
                    Map.of("userName", "Billy"));
        }
        var end = System.currentTimeMillis();
        System.out.println("100000 iterations took " + (end - start) + "ms");
    }

    @Test
    public void averageOneFormatTest() {
        var bundle = Bundle.INSTANCE;
        bundle.addSource(new Fi("src/test/resources/bundles"));

        long[] times = new long[100];
        for (int i = 0; i < 100; i++) {
            var start = System.nanoTime();
            bundle.format(new Locale("ru"), "hello-user",
                    Map.of("userName", "Billy"));
            var end = System.nanoTime();

            times[i] = end - start;
        }

        System.out.println("Average time: " + Arrays.stream(times).sum() / times.length + "ns");
    }
}
