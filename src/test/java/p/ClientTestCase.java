package p;
import static org.junit.Assert.*;
import static p.IO.*;
import static p.Main.*;
import static p.MainTestCase.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
@RunWith(Parameterized.class) public class ClientTestCase {
    @Rule public TestRule watcher=new MyTestWatcher(false);
    @BeforeClass public static void setUpBeforeClass() throws Exception {
        // router=Inet4Address.getByName("10.0.0.1");
    }
    @AfterClass public static void tearDownAfterClass() throws Exception {}
    public ClientTestCase(String host) {
        this.host=host;
    }

    @Before public void setUp() throws Exception {
        Main.defaultLevel=Level.ALL;
        threads=Thread.activeCount();
        inetAddress=InetAddress.getByName(host);
        ServerSocket serverSocket=new ServerSocket(service);
        if(true) acceptor=new Acceptor(serverSocket,socket-> {
            incoming=new Connection(socket,null,null,false);
            incoming.start();
        });
        else {
            Consumer<Socket> consumer=new Consumer<Socket>() {
                @Override public void accept(Socket socket) {
                    incoming=new Connection(socket,string->p("incoming received: "+string),e->p("incoming caught exception: "+e),false);
                    incoming.start();
                }
            };
            acceptor=new Acceptor(serverSocket,consumer);
        }
        acceptor.start();
    }
    @After public void tearDown() throws Exception {
        // maybe move outside of tear down?
        acceptor.close();
        if(outgoing!=null) outgoing.close();
        if(incoming!=null) incoming.close();
        int active=Thread.activeCount();
        if(printExtraThreads) {
            if(active>threads) {
                p("extra threads: "+(active-threads));
                printThreads(excluded);
            }
        }
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
    @Test public void testConnectAndClose() throws UnknownHostException,IOException,InterruptedException {
        Socket socket=new Socket(inetAddress,service);
        Consumer<String> stringConsumer=new Consumer<String>() {
            @Override public void accept(String string) {
                p("received: "+string);
            }
        };
        Consumer<Exception> exceptionConsumer=new Consumer<Exception>() {
            @Override public void accept(Exception exception) {
                p("exception: "+exception);
            }
        };
        outgoing=new Connection(socket,stringConsumer,exceptionConsumer,true);
        // teardown does the close
        // maybe test order of closes?
    }
    @Test public void testIncomingUnexpectedCloseByServer() throws UnknownHostException,IOException,InterruptedException {
        Socket socket=new Socket(inetAddress,service);
        Thread.sleep(10);
        incoming.socket.close(); // trigger unexpected exception
        Thread.sleep(10);
        assertEquals(1,incoming.unexpected);
        socket.close();
        // bad test
        // and it's also on the server, not the client :(
    }
    @Test public void testClientSend() throws UnknownHostException,IOException,InterruptedException {
        Socket socket=new Socket(inetAddress,service);
        outgoing=new Connection(socket,string->p("received: "+string),e->p("exception: "+e),true);
        outgoing.send("foo");
        assertEquals(1,outgoing.sent);
        Thread.sleep(10);
        outgoing.close();
        acceptor.close();
        assertEquals(1,incoming.received);
    }
    final String host;
    boolean printExtraThreads=true;
    int threads;
    InetAddress inetAddress;
    Acceptor acceptor;
    Connection incoming,outgoing;
    final Integer service=testService++;
}
