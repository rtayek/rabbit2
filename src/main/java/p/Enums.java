package p;
import java.io.File;
import java.util.Properties;
import java.util.logging.Level;
import static p.IO.*;
import static p.Main.*;
interface Item {
    void doItem(Main main); 
}
public class Enums {
    public enum LevelSubMenuItem implements Item {
        all(Level.ALL),finest(Level.FINEST),finer(Level.FINER),fine(Level.FINE),config(Level.CONFIG),info(Level.INFO),warning(Level.WARNING),sever(Level.SEVERE),none(Level.OFF);
        LevelSubMenuItem(Level level) {
            this.level=level;
        }
        @Override public void doItem(Main main) {
            doItem(this,main);
        }
        public static boolean isItem(int ordinal) {
            return item(ordinal)!=null;
        }
        public static LevelSubMenuItem item(int ordinal) {
            return 0<=ordinal&&ordinal<values().length?values()[ordinal]:null;
        }
        public static void doItem(int ordinal) { // used by android
            if(0<=ordinal&&ordinal<values().length) values()[ordinal].doItem((Main)null);
            else l.severe(ordinal+" is invalid ordinal for!");
        }
        public static void doItem(LevelSubMenuItem levelSubMenuItem,Main main) {
            l.setLevel(levelSubMenuItem.level);
        }
        private final Level level;
    }
    public enum MenuItem implements Item {
        ResetPreferences,Statistics,ToggleLogging,Log,Sound,Quit,Level;
        @Override public void doItem(Main main) {
            doItem(this,main);
        }
        public static boolean isItem(int ordinal) {
            return item(ordinal)!=null;
        }
        public static MenuItem item(int ordinal) {
            return 0<=ordinal&&ordinal<values().length?values()[ordinal]:null;
        }
        public static void doItem(int ordinal,Main main) { // used by android
            if(main!=null) if(0<=ordinal&&ordinal<values().length) values()[ordinal].doItem(main);
            else l.severe(ordinal+" is invalid ordinal for menu item!");
            else l.severe("main is null in do item!");
        }
        public static void doItem(MenuItem tabletMenuItem,final Main main) {
            if(main==null) l.severe("main is null in doItem: "+tabletMenuItem);
            switch(tabletMenuItem) {
                case Statistics:
                    main.printStats();
                break;
                case ResetPreferences:
                    l.info("reset preferences to: "+defaultProperties+" "+Main.propertiesFilename);
                    store(new File(Main.propertiesFilename),defaultProperties);
                    break;
                case ToggleLogging:
                    //LoggingHandler.toggleSockethandlers();
                    break;
                case Log:
                    // gui.textView.setVisible(!gui.textView.isVisible());
                    break;
                case Level: // handled by submenu 
                    break; // no, it's not
                case Sound:
                    Audio.Instance.sound=!Audio.Instance.sound;
                    l.info("sound: "+Audio.Instance.sound);
                    break;
                case Quit:
                    if(!isAndroid()) {
                        //p("calling System.exit().");
                        //System.exit(0);
                    }
                    break;
                default:
                    l.warning(tabletMenuItem+" was not handled!");
            }
        }
    }
}
