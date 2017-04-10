package p;
import static org.junit.Assert.*;
import org.junit.*;
import static p.IO.*;
public class IOTestCase {
    @BeforeClass public static void setUpBeforeClass() throws Exception {}
    @AfterClass public static void tearDownAfterClass() throws Exception {}
    @Before public void setUp() throws Exception {}
    @After public void tearDown() throws Exception {}
    @Test public void test() {
        boolean fail=false;
        for(String host:hosts()) {
            p("try: "+host);
            if(!Exec.canWePing(host,1_000)) {
                fail=true;
                p("can not ping: "+host);
            }
        }
        if(fail) fail("can not ping some hosts!");
    }
}
