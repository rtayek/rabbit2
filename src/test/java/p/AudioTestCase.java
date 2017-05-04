package p;
import p.Audio.*;
import p.MyTestWatcher;
import static p.IO.*;
import static org.junit.Assert.*;
import java.util.logging.Level;
import org.junit.*;
import org.junit.rules.TestRule;
//import junit.framework.TestCase;
//https://svn.apache.org/repos/asf/harmony/enhanced/java/trunk/classlib/modules/sound/src/test/java/org/apache/harmony/sound/tests/javax/sound/sampled/AudioSystemTest.java
public class AudioTestCase  {
    @Rule public TestRule watcher=new MyTestWatcher();
    @Test public void testOne() throws InterruptedException {
        Audio.Instance.sound=true;
        p("sound: "+Audio.Instance.sound);
        Audio.audio.play(Sound.electronic_chime_kevangc_495939803);
        Thread.sleep(5_000);
    }
    @Test public void testAll() throws InterruptedException {
        Audio.Instance.sound=true;
        p("sound: "+Audio.Instance.sound);
        for(Sound sound:Sound.values()) {
            p("sound: "+sound);
            Audio.audio.play(sound);
            Thread.sleep(3_000);
        }
    }
    @Test public void testAudioObserver(){
        Model model=Model.mark1.clone();
        AudioObserver audioObserver=new AudioObserver(model);
        model.addObserver(audioObserver);
        Integer expected=1;
        assertEquals(expected,(Integer)model.countObservers());
        p("count: "+model.countObservers());
        model.setState(1,true);
        //model.setChangedAndNotify("foo");
    }
}
