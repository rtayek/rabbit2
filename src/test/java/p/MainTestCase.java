package p;
import static org.junit.Assert.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;
import org.junit.*;
import org.junit.rules.TestRule;
import p.Main.Group;
import p.Main.Tablet;
import static p.Main.*;
import static p.IO.*;
public class MainTestCase {
    @Rule public TestRule watcher=new MyTestWatcher(true);
    @BeforeClass public static void setUpBeforeClass() throws Exception {
        // router=Inet4Address.getByName("10.0.0.1");
    }
    @AfterClass public static void tearDownAfterClass() throws Exception {}
    @Before public void setUp() throws Exception {
        Main.defaultLevel=Level.OFF;
        threads=Thread.activeCount();
        myInetAddress=InetAddress.getLocalHost();
        // does not test much.
        // maybe use a real address?
        int first=toUnsignedInt(myInetAddress.getAddress()[3]);
        Group group=new Group(first,first,false); // only one so it does not matter
        main=new Main(testProperties,group,Model.mark1.clone());
    }
    @After public void tearDown() throws Exception {
        int active=Thread.activeCount();
        if(printExtraThreads) {
            // p("active: "+active);
            if(active>threads) {
                p("extra threads: "+(active-threads));
                printThreads(excluded);
            }
        }
    }
    @Test public void testCtor() throws Exception {
        assertNotNull(main);
    }
    void check(Set<InetSocketAddress> socketAddresses) {
        // test is ugly!
    }
    @Test public void testGroupSocketAddresses() throws Exception {
        int first=toUnsignedInt(myInetAddress.getAddress()[3]);
        int n=32;
        Group group=new Group(first,first+n-1,false);
        Main main=new Main(defaultProperties,group,Model.mark1.clone());
        Set<InetSocketAddress> socketAddresses=main.group.socketAddresses(myInetAddress);
        p("false: "+socketAddresses);
        Iterator<InetSocketAddress> i=socketAddresses.iterator();
        for(int j=0;j<n;j++) { // tests that addresses and ports are sequential
            InetSocketAddress inetSocketAddress2=i.next();
            assertEquals(first+j,toUnsignedInt(inetSocketAddress2.getAddress().getAddress()[3]));
            assertEquals(main.group.serviceBase+first+j,inetSocketAddress2.getPort());
        }
        Group group2=new Group(first,first+n-1,true);
        // looks like main gets constructed with different services!
        // really, how does it do that?
        main=new Main(defaultProperties,group2,Model.mark1.clone(),first);
        socketAddresses=main.group.socketAddresses(myInetAddress);
        p("true: "+socketAddresses);
        i=socketAddresses.iterator();
        for(int j=0;j<n;j++) { // just tests that addresses are the same and ports are sequential
            InetSocketAddress inetSocketAddress2=i.next();
            assertEquals(first,toUnsignedInt(inetSocketAddress2.getAddress().getAddress()[3]));
            assertEquals(group2.serviceBase+first+j,inetSocketAddress2.getPort());
        }
    }
    @Test public void testTabletStartListeningAndStopListening() throws Exception {
        Tablet tablet=main.instance();
        main.myInetAddress=myInetAddress;
        boolean ok=tablet.startListening();
        assertTrue(ok);
        tablet.stopListening();
    }
    @Test public void testTabletStartListeningAndStopListeningTwice() throws Exception {
        main.myInetAddress=myInetAddress;
        Tablet tablet=main.instance();
        boolean ok=tablet.startListening();
        assertTrue(ok);
        tablet.stopListening();
        ok=tablet.startListening();
        assertTrue(ok);
        tablet.stopListening();
    }
    @Test public void testTabletStartListeningAndStopListeningManyTimes() throws Exception {
        main.myInetAddress=myInetAddress;
        Tablet tablet=main.instance();
        int n=10;
        int first=toUnsignedInt(myInetAddress.getAddress()[3]);
        for(int i=1;i<=n;i++) {
            // pn("i="+i+", ");
            boolean ok=tablet.startListening();
            assertTrue(ok);
            tablet.stopListening();
            // some of these are in state new after join;
        }
    }
    @Test public void testClick() throws Exception {
        main.myInetAddress=myInetAddress;
        Tablet tablet=main.instance();
        boolean ok=tablet.startListening();
        assertTrue(ok);
        tablet.click(1);
        assertTrue(main.model.state(1));
        Thread.sleep(200);
        tablet.stopListening();
    }
    @Test public void testClickWithSomeTablets() throws Exception {
        // seems to work on real router?
        // so why don't the cb2 installs work?
        int first=toUnsignedInt(myInetAddress.getAddress()[3]);
        // maybe make first 101?
        int n=32;
        Group group=new Group(first,first+n-1,true);
        p(group.toString());
        ArrayList<Main> mains=new ArrayList<>();
        for(int i=0;i<n;i++) {
            int myService=group.serviceBase+first+i;
            Main main=new Main(defaultProperties,group,Model.mark1.clone(),myService);
            mains.add(main);
            Tablet tablet=main.instance();
            main.myInetAddress=myInetAddress;
            tablet.startListening();
        }
        Main m1=mains.get(0);
        m1.instance().click(1);
        Thread.sleep(100);
        for(Main main:mains)
            p("model: "+main.model);
        for(Main main:mains)
            assertTrue(main.model.state(1));
        for(Main main:mains) {
            Tablet tablet=main.instance();
            tablet.stopListening();
        }
    }
    @Test public void testFindMyDhcpInetAddressOnMyRouter() throws Exception {
        int first=100,n=32;
        Group group=new Group(first,first+n-1,false);
        Main main=new Main(testProperties,group,Model.mark1);
        Set<InterfaceAddress> set=findMyInterfaceAddressesOnRouter(main.router);
        //p("interface addresses: "+set);
        assertTrue(set.size()>0);
        InetAddress inetAddress=set.iterator().next().getAddress();
        if(!group.isInGroup(inetAddress)) p("dhcp inet address: "+inetAddress+" is not in group: "+group);
    }
    @Test public void testFindMyStaticInetAddressOnMyRouter() throws Exception {
        if(false) { // not using static ip address
            int first=11,n=32;
            Group group=new Group(first,first+n-1,false);
            Main main=new Main(testProperties,group,Model.mark1);
            Set<InterfaceAddress> set=findMyInterfaceAddressesOnRouter(main.router);
            //p("interface addresses: "+set);
            assertTrue(set.size()>0);
            InetAddress inetAddress=set.iterator().next().getAddress();
            if(!group.isInGroup(inetAddress)) p("dhcp inet address: "+inetAddress+" is not in group: "+group);
        }
    }
    @Test public void testFindMyDhcpInetAddressOnDefaultRouter() throws Exception {
        int first=100,n=32; // may fail is router is down or incorect in properties file.
        Group group=new Group(first,first+n-1,false);
        String router=defaultProperties.getProperty("router");
        if(Exec.canWePing(router,1_000)) {
            /*Main main=*/new Main(defaultProperties,group,Model.mark1);
            Set<InterfaceAddress> set=findMyInterfaceAddressesOnRouter(router);
            p("interface addresses: "+set);
            assertTrue(set.size()>0);
            InetAddress inetAddress=set.iterator().next().getAddress();
            if(!group.isInGroup(inetAddress)) p("dhcp inet address: "+inetAddress+" is not in group: "+group);
        } else {
            p("can not ping: "+router);
            p("router or interface is not up!");
        }
    }
    @Test public void testFindMyIpAddress() throws Exception {
        int first=100,n=32;
        Group group;
        Main main;
        Set<InterfaceAddress> x;
        // add tests with same ip address
        // this can't work on a real tablet
        // since we don't know what service to use
        group=new Group(11,11+n-1,true);
        int myService=group.serviceBase+first+0;
        main=new Main(defaultProperties,group,Model.mark1,myService);
        InetAddress inetAddress=group.findMyInetAddress(main.router);
        //p("inet address: "+inetAddress);
        assertTrue(inetAddress==null);
    }
    @Test public void testClickOnSecondTablet() throws Exception {
        int first=toUnsignedInt(myInetAddress.getAddress()[3]);
        int n=3;
        Group group=new Group(first,first+n-1,true);
        ArrayList<Main> mains=new ArrayList<>();
        // use group to get list of socket addresses?
        for(int i=0;i<n;i++) {
            int myService=group.serviceBase+first+i;
            Main main=new Main(testProperties,group,Model.mark1.clone(),myService);
            mains.add(main);
            Tablet tablet=main.instance();
            main.myInetAddress=myInetAddress;
            tablet.startListening();
            assertTrue(tablet.isListening());
        }
        Main m1=mains.get(0);
        m1.instance().click(1);
        Thread.sleep(m1.group.connectionTimeout+100);
        p(m1.statistics());
        for(Main main:mains)
            assertTrue(main.model.state(1));
        Main m2=mains.get(1);
        m2.instance().click(1);
        Thread.sleep(m2.group.connectionTimeout+100);
        for(Main main:mains)
            assertFalse(main.model.state(1));
        Main m3=mains.get(2);
        m3.instance().click(Model.mark1.resetButtonId);
        Thread.sleep(100);
        for(Main main:mains)
            for(int i=1;i<=Model.mark1.buttons;i++)
                assertFalse(main.model.state(1));
        for(Main main:mains) {
            Tablet tablet=main.instance();
            tablet.stopListening();
        }
    }
    int threads;
    Main main;
    InetAddress myInetAddress;
    boolean printExtraThreads=true;
    static List<String> excluded=Arrays.asList(new String[] {"main","ReaderThread"});
}
