package p;
import static org.junit.Assert.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import static p.IO.*;
import static p.IO.Acceptor.*;
@RunWith(Parameterized.class) public class BindTestCase {
    @Rule public TestRule watcher=new MyTestWatcher(false);
    @BeforeClass public static void setUpBeforeClass() throws Exception {}
    @AfterClass public static void tearDownAfterClass() throws Exception {}
    public BindTestCase(String host) {
        this.host=host;
    }
    @Before public void setUp() throws Exception {
        InetAddress inetAddress=InetAddress.getByName(host);
        socketAddress=new InetSocketAddress(inetAddress,service);
    }
    @After public void tearDown() throws Exception {
        int active=Thread.activeCount();
        if(printExtraThreads) {
            if(active>threads+2/* junit has 2 extra threads */) {
                p("extra threads: "+(active-threads));
                printThreads(excluded);
            }
        }
    }
    @Parameters public static Collection<Object[]> data() throws UnknownHostException,InterruptedException,ExecutionException {
        Set<String> hosts=moreHosts();
        p("hosts: "+hosts);
        List<Object[]> parameters=new ArrayList<Object[]>();
        for(String string:hosts) {
            parameters.add(new Object[] {string});
        }
        return parameters;
    }
    boolean close() {
        if(serverSocket!=null) {
            if(serverSocket.isBound()) {
                try {
                    serverSocket.close();
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
            if(serverSocket.isClosed()) return true;
            else {
                try {
                    serverSocket.close();
                    return true;
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        } else return true;
        return false;
    }
    @Test public void testStopListeningWithNullServerSocket() throws Exception {
        boolean ok=close();
        assertTrue(ok);
    }
    @Test public void testStopListeningWithNonNullServerSocket() throws Exception {
        serverSocket=new ServerSocket();
        boolean ok=close();
        assertTrue(ok);
    }
    @Test public void testStartListening() throws Exception {
        serverSocket=new ServerSocket();
        boolean ok=bind(serverSocket,socketAddress);
        assertTrue(ok);
    }
    @Test public void testStartAndStopLstening() throws Exception {
        serverSocket=new ServerSocket();
        boolean ok=bind(serverSocket,socketAddress);
        assertTrue(ok);
        ok=close();
        assertTrue(ok);
    }
    @Test public void testStartAndStopLsteningManyTimes() throws Exception {
        for(int i=1;i<=10;i++) {
            serverSocket=new ServerSocket();
            boolean ok=bind(serverSocket,socketAddress);
            assertTrue(ok);
            close();
            ok=close();
            assertTrue(ok);
        }
    }
    private final String host;
    private int threads;
    private SocketAddress socketAddress;
    private ServerSocket serverSocket;
    private boolean printExtraThreads=true;
    private final Integer service=testService++; // any race conditions here?
    private static List<String> excluded=Arrays.asList(new String[] {"main","ReaderThread"});
}
