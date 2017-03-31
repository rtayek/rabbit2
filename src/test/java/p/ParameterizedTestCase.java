package p;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.*;
import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import static p.IO.*;
@RunWith(Parameterized.class) public class ParameterizedTestCase {
    @Rule public TestRule watcher=new MyTestWatcher(false);
    @BeforeClass public static void setUpBeforeClass() throws Exception {}
    @AfterClass public static void tearDownAfterClass() throws Exception {}
    @Before public void setUp() throws Exception {}
    @After public void tearDown() throws Exception {}
    public ParameterizedTestCase(String host) {
        this.host=host;
    }
    @Parameters public static Collection<Object[]> data() throws UnknownHostException,InterruptedException,ExecutionException {
        Set<String> hosts=hosts();
        p("hosts: "+hosts);
        List<Object[]> parameters=new ArrayList<Object[]>();
        for(String string:hosts) {
            parameters.add(new Object[] {string});
        }
        return parameters;
    }
    @Test public void test() throws IOException,InterruptedException {
        p("host: "+host);
    }
    final String host;
}
