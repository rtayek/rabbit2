package p;
import static org.junit.Assert.*;
import java.io.IOException;
import java.net.ConnectException;
import java.util.logging.*;
import org.junit.*;
import static p.LogServer.*;
import static p.IO.*;
public class LogServerOnLaptopTestCase {
    @BeforeClass public static void setUpBeforeClass() throws Exception {}
    @AfterClass public static void tearDownAfterClass() throws Exception {}
    @Before public void setUp() throws Exception {}
    @After public void tearDown() throws Exception {}
    @Test(timeout=1_000) public void test() throws IOException {
        String laptopToday="192.168.0.100";
        p(laptopToday+":"+defaultLogServerService);
        if(runTest) try {
            SocketHandler socketHandler=new SocketHandler(laptopToday,defaultLogServerService);
            socketHandler.setLevel(Level.ALL);
            Logger l=Logger.getLogger(testLoggerName);
            l.addHandler(socketHandler);
            l.warning("foo");
        } catch(ConnectException e) {
            fail("make sure log server is running on laptop");
        }
    }
    static boolean runTest=false;
}
