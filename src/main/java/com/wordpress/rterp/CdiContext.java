/*
 * © Rob Terpilowski, original source: https://rterp.wordpress.com/2015/05/15/using-dependency-injection-in-a-java-se-application/
 */
package com.wordpress.rterp;

import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;


/**
 * It would be nice to decouple components in client applications the way that we have become accustom to doing in server side applications and providing a way to use mock implementations for unit testing.
 * Fortunately it is fairly straightforward to configure a Java SE client application to use a dependency injection framework such as Weld.
 *
 * @author https://twitter.com/RobTerpilowski
 * @since 23-09-2016
 * @see <a href="https://rterp.wordpress.com/2015/05/15/using-dependency-injection-in-a-java-se-application/">
 *     https://rterp.wordpress.com/2015/05/15/using-dependency-injection-in-a-java-se-application/
 *     </a>
 */
public class CdiContext {

    public static final CdiContext INSTANCE = new CdiContext();

    private final Weld weld;
    private final WeldContainer container;

    private CdiContext() {
        this.weld = new Weld();
        this.container = weld.initialize();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                weld.shutdown();
            }
        });
    }

    public <T> T getBean(Class<T> type) {
        return container.instance().select(type).get();
    }
}
