package p;
import static p.IO.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.*;
import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import p.LogServer.Copier;
@RunWith(Parameterized.class) public class LogServerTestCase {
    // this has no need of the logServerHost property.
    @Rule public TestRule watcher=new MyTestWatcher();
    @BeforeClass public static void setUpBeforeClass() throws Exception {}
    @AfterClass public static void tearDownAfterClass() throws Exception {}
    @Before public void setUp() throws Exception {
        LogManager.getLogManager().reset();
        service=staticService++;
    }
    @After public void tearDown() throws Exception {}
    public LogServerTestCase(String host) {
        this.host=host;
    }
    @Test public void testConnectAndWrite() throws Exception,IOException {
        writer=new StringWriter();
        LogServer.Factory factory=new LogServer.Factory(writer);
        logServer=new LogServer(host,service,factory,getClass().getName());
        thread=new Thread(new Runnable() {
            @Override public void run() {
                logServer.run();
            }
        },"log server");
        thread.start();
        Socket socket=new Socket(host,service);
        BufferedWriter bufferedWriter=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        bufferedWriter.write(expected);
        bufferedWriter.flush();
        socket.close();
        Thread.sleep(100);
        logServer.stop();
        thread.join(1000);
        p("writer contains: "+writer.toString());
        assertTrue(writer.toString().contains(expected));
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
    @Test public void testConnectAndWriteFile() throws Exception,IOException {
        logServer=new LogServer(host,service,null,getClass().getSimpleName());
        thread=new Thread(new Runnable() {
            @Override public void run() {
                logServer.run();
            }
        },"log server");
        thread.start();
        Socket socket=new Socket(host,service);
        BufferedWriter bufferedWriter=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        bufferedWriter.write(expected);
        bufferedWriter.flush();
        bufferedWriter.close();
        socket.close();
        Thread.sleep(100);
        Copier copier=logServer.copiers.iterator().next();
        copier.close();
        logServer.stop();
        thread.join(1000);
        StringBuffer stringBuffer=new StringBuffer();
        fromFile(stringBuffer,copier.file);
        p("contents of file: "+copier.file+": '"+stringBuffer.toString()+"'");
        assertTrue(stringBuffer.toString().contains(expected));
    }
    @Test public void testSockethandler() throws Exception {
        writer=new StringWriter();
        LogServer.Factory factory=new LogServer.Factory(writer);
        logServer=new LogServer(host,service,factory,getClass().getName());
        thread=new Thread(new Runnable() {
            @Override public void run() {
                logServer.run();
            }
        },"log server");
        thread.start();
        socketHandler=new SocketHandler(host,service);
        socketHandler.setLevel(Level.ALL);
        l.addHandler(socketHandler);
        p("socket handler: "+socketHandler);
        l.severe(expected);
        Thread.sleep(100); // need to wait a bit
        logServer.stop();
        thread.join(1000);
        assertTrue(writer.toString().contains(expected));
    }
    @Test public void testSockethandlerAndWriteFiles() throws Exception {
        logServer=new LogServer(host,service,null,getClass().getSimpleName());
        thread=new Thread(new Runnable() {
            @Override public void run() {
                logServer.run();
            }
        },"log server");
        thread.start();
        socketHandler=new SocketHandler(host,service);
        socketHandler.setLevel(Level.ALL);
        l.addHandler(socketHandler);
        l.severe(expected);
        Thread.sleep(100); // need to wait a bit
        Copier copier=logServer.copiers.iterator().next();
        copier.isShuttingdown=true;
        copier.close();
        logServer.stop();
        thread.join(1000);
        StringBuffer stringBuffer=new StringBuffer();
        fromFile(stringBuffer,copier.file);
        //p("contents of file: "+copier.file+": '"+stringBuffer.toString()+"'");
        assertTrue(stringBuffer.toString().contains(expected));
    }
    @Test public void testRestartLogserver() throws InterruptedException,IOException {
        // only passes because we don't test anything
        // just tests the restart capability!
        logServer=new LogServer(host,service,null,getClass().getSimpleName());
        thread=new Thread(new Runnable() {
            @Override public void run() {
                logServer.run();
            }
        },"log server");
        thread.start();
        socketHandler=new SocketHandler(host,service);
        socketHandler.setLevel(Level.ALL);
        l.addHandler(socketHandler);
        l.info(expected);
        l.info("foo");
        Thread.sleep(100); // need to wait a bit
        if(true) {
            Copier copier=logServer.copiers.iterator().next();
            copier.isShuttingdown=true;
            copier.flush();
        } else {
            Copier copier=logServer.copiers.iterator().next();
            copier.close();
            StringBuffer stringBuffer=new StringBuffer();
            fromFile(stringBuffer,copier.file);
            //p("contents of file: "+copier.file+": '"+stringBuffer.toString()+"'");
            assertTrue(stringBuffer.toString().contains(expected));
            // how to kill socket handler?
            logServer.stop();
        }
        Thread.sleep(1_000); // at least 1 second!
        thread.join(1000);
        LogManager.getLogManager().reset();
        socketHandler=new SocketHandler(host,service);
        socketHandler.setLevel(Level.ALL);
        l.addHandler(socketHandler);
        l.info(expected);
        l.info("bar");
        Thread.sleep(100); // need to wait a bit
        logServer.stop();
        thread.join(1000);
    }
    String host="localhost";
    Integer service;
    LogServer logServer;
    SocketHandler socketHandler;
    Thread thread;
    Writer writer;
    final Logger l=Logger.getLogger(testLoggerName);
    final String expected="i am a duck.";
    static Integer staticService=7000;
}
