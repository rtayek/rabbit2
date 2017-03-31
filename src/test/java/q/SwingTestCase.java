package q;
import static org.junit.Assert.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import p.*;
import p.Main.*;
import p.Model;
import static p.Main.*;
import static p.IO.*;
import p.Audio.AudioObserver;
public class SwingTestCase {
    @BeforeClass public static void setUpBeforeClass() throws Exception {}
    @AfterClass public static void tearDownAfterClass() throws Exception {}
    @Before public void setUp() throws Exception {
        InetAddress inetAddress=InetAddress.getLocalHost();
        int first=toUnsignedInt(inetAddress.getAddress()[3]);
        Group group=new Group(first,first,false);
        int service=group.serviceBase+first;
        InetSocketAddress inetSocketAddress=new InetSocketAddress(inetAddress,service);
        Main main=new Main(logger,routerOnMyPc,group,Model.mark1.clone());
        Tablet tablet=main.instance();
        main.model.addObserver(swing=Swing.create(main));
        main.model.addObserver(new AudioObserver(main.model));
        boolean ok=tablet.startListening();
        if(!ok) p("not listening!");
    }
    @After public void tearDown() throws Exception {}
    @Test public void test() {
        swing.guiAdapter.processClick(0);
        assertTrue(swing.main.model.state(1));
        if(swing.main.instance().isListening()) swing.main.instance().stopListening();
    }
    Swing swing;
    final Logger logger=Logger.getLogger("xyzzy");
}
