package testSupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method as a speed/performance test.
 * Tests with this annotation will not be subject to the logger's
 * timing limits and will not accumulate time that affects other tests.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SpeedTest {
}
