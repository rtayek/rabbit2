package q;
import static java.lang.Math.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import p.Enums.*;
import p.Enums.MenuItem;
import p.Main.Group;
import p.Main;
import p.Model;
import static p.Main.*;
import static p.IO.*;
enum Where {
    top(BorderLayout.PAGE_START),bottom(BorderLayout.PAGE_END),right(BorderLayout.LINE_END),left(BorderLayout.LINE_START),center(BorderLayout.CENTER);
    Where(String k) {
        this.k=k;
    }
    public final String k;
    public Color color;
    public static void init() {
        for(Where where:values())
            where.color=Color.getHSBColor((float)(where.ordinal()*1./values().length),.9f,.9f);
    }
    public Character toCharacter() {
        return name().charAt(0);
    }
    public static Boolean[] from(Integer x) {
        x%=(int)round(pow(2,values().length));
        String string=Integer.toBinaryString(x);
        while(string.length()<values().length)
            string='0'+string;
        Boolean[] b=new Boolean[values().length];
        for(int i=0;i<values().length;i++)
            b[i]=string.charAt(i)=='1';
        return b;
    }
    public static EnumSet<Where> set(Boolean[] bits) {
        EnumSet<Where> set=EnumSet.noneOf(Where.class);
        for(int i=0;i<values().length;i++)
            if(bits[i]) set.add(values()[i]);
        return set;
    }
}
public class Swing extends MainGui implements Observer,ActionListener {
    // http://www.javaknowledge.info/android-like-toast-using-java-swing/
    private Swing(Main main) {
        super();
        this.main=main;
        buttons=new AbstractButton[colors.n];
    }
    @Override public JFrame frame() {
        @SuppressWarnings("serial") JFrame frame=new JFrame() {
            @Override public void dispose() { // dispose of associated text view
                super.dispose();
            }
        };
        // frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        return frame;
    }
    @Override public void initialize() { // as much as we can/need off the
                                             // awt/edt thread
    }
    @Override public String title() {
        return "Buttons";
    }
    @Override public void addContent() {
        JMenuBar jMenuBar=createMenuBar();
        frame.setJMenuBar(jMenuBar);
        JPanel top=new JPanel();
        JLabel topLabel=new JLabel("no name");
        Font current=topLabel.getFont();
        // p(topLabel.getFont().toString());
        add(top,BorderLayout.PAGE_START);
        buildCenter(buttonSize);
        Font small=new Font(current.getName(),current.getStyle(),2*current.getSize()/3);
        topLabel.setFont(small);
        top.add(topLabel);
        JPanel bottom=new JPanel();
        JLabel bottomLabel=new JLabel("bottom");
        bottomLabel.setFont(small);
        bottom.add(bottomLabel);
        add(bottom,BorderLayout.PAGE_END);
        add(new JLabel("left"),BorderLayout.LINE_START);
        add(new JLabel("right"),BorderLayout.LINE_END);
        frame.getContentPane().addHierarchyBoundsListener(hierarchyBoundsListener);
    }
    void buildCenter(int size) {
        JPanel center=new JPanel();
        center.setBackground(new Color(colors.background));
        EmptyBorder e=new EmptyBorder(10,10,10,10);
        JPanel box=new JPanel();
        box.setBackground(new Color(colors.background));
        box.setLayout(new BoxLayout(box,BoxLayout.LINE_AXIS));
        EmptyBorder e2=new EmptyBorder(size*12/10,size,size*12/10,size);
        box.setBorder(e2);
        //Insets x=box.getInsets();
        // p(x.toString());
        JPanel left=new JPanel();
        GridLayout grid=new GridLayout(colors.rows,colors.columns,size*2/10,size*2/10);
        // left.setBorder(BorderFactory.createLineBorder(Color.red));
        left.setLayout(grid);
        left.setBackground(new Color(colors.background));
        // Font sans=new Font(Font.SANS_SERIF,Font.PLAIN,size*6/10);
        // Font serif=new Font(Font.SERIF,Font.PLAIN,size*4/10);
        // p("serif size: "+serif.getSize());
        actionListener=new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(e.getSource() instanceof AbstractButton) {
                    AbstractButton button=(AbstractButton)e.getSource();
                    String name=button.getName(); // name starts at zero
                    Integer index=Integer.valueOf(name);
                    processClick(index);
                }
            }
        };
        Font font=null;
        for(int i=0;i<colors.rows*colors.columns;i++) {
            JButton button=new JButton();
            button.setText(""+(i+1));
            button.setName(""+i); // name starts at zero, text starts at one!
            left.add(button,i);
            button.addActionListener(actionListener);
            button.setBackground(new Color(colors.color(i,false)));
            button.setPreferredSize(new Dimension(size,size));
            if(i==0) {
                font=button.getFont();
                int fontsize=font.getSize();
                font=new Font(font.getFontName(),font.getStyle(),3*fontsize);
            }
            button.setFont(font);
            buttons[i]=button;
            buttons[i].setFont(font);
        }
        box.add(left);
        JPanel middle=new JPanel();
        middle.setLayout(new BoxLayout(middle,BoxLayout.PAGE_AXIS));
        // middle.setBorder(BorderFactory.createLineBorder(Color.blue));
        middle.setPreferredSize(new Dimension(size*8/10,2*size));
        JButton small=new JButton();
        small.setPreferredSize(new Dimension(size/2,size/2));
        // middle.add(small);
        box.add(middle);
        JPanel right=new JPanel();
        right.setBackground(new Color(colors.background));
        // right.setLayout(null);
        // right.setLayout(new BoxLayout(right,BoxLayout.PAGE_AXIS));
        right.setLayout(new GridLayout(colors.rows,1,10,10));
        // right.setBorder(BorderFactory.createLineBorder(Color.green));
        JButton button=new JButton();
        button.setText(""+main.model.resetButtonId);
        button.setBackground(new Color(colors.color(colors.rows*colors.columns,false)));
        buttons[colors.rows*colors.columns]=button;
        button.setText("R");
        button.setFont(font);
        button.setName(""+(colors.rows*colors.columns));
        button.addActionListener(actionListener);
        button.setMinimumSize(new Dimension(size,size));
        button.setPreferredSize(new Dimension(size,size));
        button.setMaximumSize(new Dimension(size,size));
        right.add(button,0);
        right.setPreferredSize(new Dimension(size,2*size));
        // button.setFont(sans);
        box.add(right);
        center.add(box);
        add(center,Where.center.k);
    }
    ActionListener actionListener;
    public void processClick(int index) {
        int id=index+1;
        if(1<=id&&id<=main.model.buttons) main.instance().click(id);
        else p(id+" is bad button id!");
    }
    public void setButtonText(final int id,final String string) {
        final Integer index=id-1; // model uses 1-n
        if(SwingUtilities.isEventDispatchThread()) {
            buttons[index].setText(string);
        } else SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                buttons[index].setText(string);
            }
        });
    }
    public void setButtonState(final int id,final boolean state) {
        final Integer index=id-1; // model uses 1-n
        if(SwingUtilities.isEventDispatchThread()) {
            buttons[index].setSelected(state);
            buttons[index].setBackground(new Color(colors.color(index,state)));
        } else SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                buttons[index].setSelected(state);
                buttons[index].setBackground(new Color(colors.color(index,state)));
            }
        });
    }
    @Override public void update(Observable o,Object hint) {
        if(o instanceof Model&&o.equals(main.model)) {
            for(Integer buttonId=1;buttonId<=main.model.buttons;buttonId++) {
                setButtonState(buttonId,main.model.state(buttonId));
                setButtonText(buttonId,"id");
            }
        } else l.warning("not a model or not our model!");
    }
    @Override public void actionPerformed(ActionEvent e) {
        l.info("action performed: "+e);
        if(e.getActionCommand().startsWith("192.168.")) {
            if(PropertiesSubMenuItem.map.keySet().contains(e.getActionCommand())) {
                PropertiesSubMenuItem item=PropertiesSubMenuItem.map.get(e.getActionCommand());
                p("item: "+item.name()+" "+item.string());
                PropertiesSubMenuItem.map.get(e.getActionCommand()).doItem(main);
            } else l.info("action not handled: "+e.getActionCommand());
        } else try {
            MenuItem item=MenuItem.valueOf(e.getActionCommand());
            if(item!=null) {
                if(item.equals(MenuItem.Log)) { // no text view on android
                    p("what do we do here?");
                    //if(textView!=null) textView.frame.setVisible(!textView.frame.isVisible());
                    //else l.info("no log window to toggle!");
                } else item.doItem(main);
            } else l.info("action not handled: "+e.getActionCommand());
        } catch(Exception e2) {
            p("caught: "+e2);
            try {
                LevelSubMenuItem item=LevelSubMenuItem.valueOf(e.getActionCommand());
                if(item!=null) {
                    item.doItem((Main)null);
                } else l.info("action not handled: "+e.getActionCommand());
            } catch(Exception e3) {
                // add in new properties sub menu here?
                p("caught: "+e3);
            }
        }
    }
    JMenuItem addMenuItem(JMenu menu,Item item) {
        JMenuItem menuItem=item.isCheckable()?new JCheckBoxMenuItem(item.string()):new JMenuItem(item.string());
        int vk=(KeyEvent.VK_A)+(item.string().toUpperCase().charAt(0)-'A');
        menuItem.setAccelerator(KeyStroke.getKeyStroke(vk,ActionEvent.ALT_MASK));
        menuItem.getAccessibleContext().setAccessibleDescription(item.string());
        menuItem.addActionListener(this);
        menu.add(menuItem);
        return menuItem;
    }
    public JMenuBar createMenuBar() {
        JMenuBar menuBar=new JMenuBar();
        JMenu menu=new JMenu("Options");
        menu.setMnemonic(KeyEvent.VK_O);
        menu.getAccessibleContext().setAccessibleDescription("Options menu");
        // Reset,Ping,Disconnect,Connect,Log;
        for(MenuItem menuItem:MenuItem.values())
            addMenuItem(menu,menuItem);
        if(false) {
            JMenuItem menuItem=null;
            menuItem=new JMenuItem("Log",KeyEvent.VK_C);
            menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L,ActionEvent.ALT_MASK));
            menuItem.getAccessibleContext().setAccessibleDescription("Log");
            menuItem.addActionListener(this);
            menu.add(menuItem);
        }
        menuBar.add(menu);
        JMenu level=new JMenu("Level");
        menu.setMnemonic(KeyEvent.VK_L);
        menu.getAccessibleContext().setAccessibleDescription("Level menu");
        for(LevelSubMenuItem levelSubMenuItem:LevelSubMenuItem.values())
            addMenuItem(level,levelSubMenuItem);
        menuBar.add(level);
        JMenu propertiesMenu=new JMenu("Properties");
        menu.setMnemonic(KeyEvent.VK_P);
        menu.getAccessibleContext().setAccessibleDescription("properties menu");
        for(PropertiesSubMenuItem propertiesSubMenuItem:PropertiesSubMenuItem.values()) {
            p(propertiesSubMenuItem+" "+main.properties.getProperty(propertiesSubMenuItem.name()));
            JMenuItem jMenuItem=addMenuItem(propertiesMenu,propertiesSubMenuItem);
            if(propertiesSubMenuItem.isCheckable()) {
                p("enum string: "+propertiesSubMenuItem.string);
                String property=main.properties.getProperty(propertiesSubMenuItem.string,"");
                p("property: "+property);
                boolean check=property.equals("true");
                p("check: "+check);
                ((JCheckBoxMenuItem)jMenuItem).setSelected(check);
                //((JCheckBoxMenuItem)jMenuItem).setEnabled(main.properties.getProperty(propertiesSubMenuItem.string,"").equals("true"));
            }
        }
        menuBar.add(propertiesMenu);
        return menuBar;
    }
    public static Swing create(final Main main) { // subclass instead
        final Swing gui=new Swing(main);
        gui.run();
        return gui;
    }
    public static void main(String[] arguments) throws Exception {
        logging();
        Properties properties=Main.properties(new File(Main.propertiesFilename));
        p("rounters we can ping: "+routersWeCanPing(5));
        addFileHandler(l,new File(logFileDirectory),"main");
        p("local host: "+InetAddress.getLocalHost());
        while(properties.getProperty("router","").equals("")) {
            findRouter(properties);
            Thread.sleep(1_000);
        }
        Integer first=new Integer(properties.getProperty("first"));
        Integer last=new Integer(properties.getProperty("last"));
        Group group=new Group(first,last,false);
        Main main=new Main(properties,group,Model.mark1);
        new Thread(main,"rabbit 2 main").start();
        main.model.addObserver(create(main));
    }
    final Colors colors=new Colors();
    final AbstractButton[] buttons;
    int buttonSize=100;
    HierarchyBoundsListener hierarchyBoundsListener=new HierarchyBoundsListener() {
        @Override public void ancestorMoved(HierarchyEvent e) {
            // p(e.toString());
        }
        @Override public void ancestorResized(HierarchyEvent e) {
            if(e.getID()==HierarchyEvent.ANCESTOR_RESIZED) {
                Dimension d=frame.getContentPane().getSize();
                buttonSize=d.width/10;
            }
        }
    };
    final Main main;
    private static final long serialVersionUID=1L;
}
