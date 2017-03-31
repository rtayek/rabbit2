package p;
import static org.junit.Assert.*;
import org.junit.*;
import p.Main.Group;
import static p.IO.*;
public class GroupTestCase {
    @BeforeClass public static void setUpBeforeClass() throws Exception {}
    @AfterClass public static void tearDownAfterClass() throws Exception {}
    @Before public void setUp() throws Exception {}
    @After public void tearDown() throws Exception {}
    @Test public void test() {
        // no real testing going on here!
        Group g1=new Group(1,2,false);
        p(g1.toString());
        Group g2=new Group(1,2,true);
        p(g2.toString());
    }
}
