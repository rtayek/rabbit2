package p;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;
import static p.IO.*;
import static p.IO.Connection.*;
import p.Enums.PropertiesSubMenuItem;
import p.IO.Acceptor;
//git fetch origin
//git reset --hard origin/master
// make sure the firewalls are off in windows!
//adb shell "ip route show"
public class Main implements Runnable {
    public Main(Properties properties,Group group,Model model) {
        this(properties,group,model,null);
        if(group.sameInetAddress) throw new RuntimeException("use ctor with service!");
    }
    public Main(Properties properties,Group group,Model model,Integer myService) {
        // group and model are required to construct.
        // router is required to construct.
        // ip address is not required to construct
        // ip address is required to broadcast or receive
        // so maybe make these setable
        // and guard everything with a test for non null?
        this.properties=properties;
        router=properties.getProperty("router","");
        logServerHost=properties.getProperty("logServerHost");
        l.info(this+" constructed at: "+new Date());
        if(group.sameInetAddress!=(myService!=null)) throw new RuntimeException("use ctor with service!");
        this.group=group;
        this.model=model;
        this.myService=myService;
        sends=new Integer[group.last-group.first+1];
        sendFailures=new Integer[group.last-group.first+1];
        receives=new Integer[group.last-group.first+1];
        for(int i=group.first;i<=group.last;i++) {
            sends[i-group.first]=0;
            sendFailures[i-group.first]=0;
            receives[i-group.first]=0;
        }
        model.addObserver(new Audio.AudioObserver(model));
    }
    public static class Statistics { // if we use a set of static addresses
        Integer sends=0,sendFailures=0,receives=0;
    }
    public static class Group { // no need to clone unless we start storing
        // Map<SocketAddress,Statistics> statistics=new LinkedHashMap<>();
        // use the above if we go to a set of static addresses.
        // no need to clone unless we store history or something in here.
        // maybe needed if we have a strange hybrid combination of tablets?
        public Group(int first,int last,boolean sameInetAddress) {
            this.first=first;
            this.last=last;
            other=0;
            other2=0;
            this.sameInetAddress=sameInetAddress;
        }
        // try to make this just a set of addresses as opposed to only a range
        // no, that may not work
        // why not?
        InetAddress findMyInetAddress(String router) {
            Set<InterfaceAddress> networkInterfaces;
            networkInterfaces=IO.findMyInterfaceAddressesOnRouter(router);
            if(networkInterfaces.size()>0) {
                if(networkInterfaces.size()>1) p("route: "+router+" has more than one network interface: "+networkInterfaces);
                InetAddress inetAddress=networkInterfaces.iterator().next().getAddress();
                if(isInGroup(inetAddress)) {
                    p("using inet address: "+inetAddress);
                    return inetAddress;
                } else p(inetAddress+" is not in group: "+this);
            } else p("no inetAddresses on: "+router);
            return null;
        }
        boolean isInGroup(InetAddress ipAddress) {
            int lowOrderOctet=lowOrderOctet(ipAddress);
            boolean isInRange=first<=lowOrderOctet&&lowOrderOctet<=last;
            return isInRange;
        }
        int service(InetAddress inetAddress,Integer myService) {
            int service=myService!=null?myService:(serviceBase+lowOrderOctet(inetAddress));
            return service;
        }
        Set<InetSocketAddress> socketAddresses(InetAddress myInetAddress) {
            Set<InetSocketAddress> socketAddresses=new LinkedHashSet<>();
            if(sameInetAddress) { // only used for testing on one machine
                for(int i=first;i<=last;i++)
                    socketAddresses.add(new InetSocketAddress(myInetAddress,serviceBase+i));
            } else { // actually only needs the network prefix here
                byte[] bytes=myInetAddress.getAddress();
                for(int i=first;i<=last;i++) {
                    bytes[3]=(byte)i;
                    try {
                        InetAddress inetAddress=InetAddress.getByAddress(bytes);
                        socketAddresses.add(new InetSocketAddress(inetAddress,serviceBase+i));
                    } catch(UnknownHostException e) {
                        p("can not get inet address for: "+bytes[0]+'.'+bytes[1]+'.'+bytes[2]+'.'+bytes[3]);
                    }
                }
            }
            return socketAddresses;
        }
        private boolean send(String string,InetSocketAddress inetSocketAddress) {
            Socket socket=connect(inetSocketAddress,connectionTimeout);
            if(socket==null) return false;
            Connection connection=new Connection(socket,null,null,true);
            boolean ok=connection.send(string);
            connection.close();
            return ok;
        }
        @Override public String toString() {
            return "Group [first="+first+", last="+last+", other="+other+", other2="+other2+", serviceBase="+serviceBase+", sameInetAddress="+sameInetAddress+"]";
        }
        private final Integer first,last,other,other2;
        public int connectionTimeout=200;
        public final int serviceBase=10_000;
        final boolean sameInetAddress;
    }
    public class Tablet {
        private Tablet() {}
        boolean receive(String string,InetAddress inetAddress) {
            // maybe pass socket or socket address in here?
            boolean rc=false;
            if(!group.isInGroup(inetAddress)) l.severe("message from: "+inetAddress+" is not in group: "+string);
            else if(string==null) ;
            else if(string.isEmpty()) l.severe("empty message from: "+inetAddress);
            else if(string.length()!=model.buttons) l.severe("bad message: "+string);
            else {
                l.info("received: "+string+" from: "+inetAddress);
                for(int i=0;i<string.length();i++) {
                    char c=string.charAt(i);
                    if(c=='T'||c=='F') {
                        boolean newState=c=='T'?true:false;
                        model.setState(i+1,newState);
                        rc=true;
                    } else {
                        p("bad string: "+string);
                        break;
                    }
                }
                rc=true;
                if(group.isInGroup(inetAddress)) receives[lowOrderOctet(inetAddress)-group.first]++;
                else l.severe("address: "+inetAddress+" is out of range: "+group.first+':'+group.last);
            }
            return rc;
        }
        public synchronized boolean isListening() {
            return isListening;
        }
        public synchronized boolean startListening() {
            if(myInetAddress==null) return false;
            if(isListening) stopListening();
            int service=group.service(myInetAddress,myService);
            SocketAddress socketAddress=new InetSocketAddress(myInetAddress,service);
            Consumer<Socket> socketConsumer=new Consumer<Socket>() {
                @Override public void accept(final Socket socket) {
                    Consumer<String> stringConsumer=new Consumer<String>() {
                        @Override public void accept(final String string) {
                            if(group.isInGroup(socket.getInetAddress())) receive(string,socket.getInetAddress());
                            else l.warning("message from: "+socket.getInetAddress()+" is not in group: "+string);
                        }
                    };
                    Consumer<Exception> exceptionConsumer=new Consumer<Exception>() {
                        @Override public void accept(final Exception exception) {
                            l.warning("caught: "+exception);
                        }
                    };
                    Connection connection=new Connection(socket,stringConsumer,exceptionConsumer,false);
                    connection.start();
                }
            };
            acceptor=Acceptor.acceptor(socketAddress,socketConsumer);
            if(acceptor!=null) {
                acceptor.start();
                isListening=true;
                // how to tell if this guy crashes?
                // done will be true if we left the run loop,
                // maybe use that?
                return true;
            }
            return false;
        }
        public synchronized void stopListening() {
            acceptor.close();
            isListening=false;
            // how about the connections?
            // close them also?
        }
        private synchronized void broadcast(final String string,Integer myService) {
            broadcasts++;
            Set<InetSocketAddress> inetSocketAddresses=group.socketAddresses(myInetAddress);
            Iterator<InetSocketAddress> j=inetSocketAddresses.iterator();
            for(int i=0;i<inetSocketAddresses.size();i++) {
                final int k=i;
                final InetSocketAddress inetSocketAddress=j.next();
                new Thread(new Runnable() {
                    @Override public void run() {
                        Et et=new Et();
                        boolean ok=group.send(string,inetSocketAddress);
                        if(ok) l.info("send #"+broadcasts+" to: "+inetSocketAddress+" took: "+et);
                        else {
                            sendFailures[k]++;
                            l.info("send #"+broadcasts+" to: "+inetSocketAddress+" failed after: "+et);
                        }
                        // if we use some random set of addresses instead of a range
                        // then we will need to keep the stats in maps.
                        sends[k]++;
                    }
                },"send #"+broadcasts+" to: "+inetSocketAddress).start();
            }
        }
        public void click(int id) {
            p("tablet click: "+id);
            try {
                if(1<=id&&id<=model.buttons) {
                    if(model.resetButtonId!=null&&id==model.resetButtonId) model.reset();
                    else model.setState(id,!model.state(id));
                    if(myInetAddress!=null) {
                        String message=model.toCharacters();
                        broadcast(message,myService);
                    } else l.warning("inet address is null!");
                } else l.warning(id+" is not a model button!");
            } catch(Exception e) {
                l.severe("click caught: "+e);
                e.printStackTrace();
            }
        }
        Acceptor acceptor;
        private boolean isListening;
        int broadcasts;
    }
    public Tablet instance() {
        if(instance==null) instance=new Tablet();
        return instance;
    }
    public static int toUnsignedInt(byte x) {
        return ((int)x)&0xff;
    }
    public static int lowOrderOctet(InetAddress ipAddress) {
        int lowOrderOctet=toUnsignedInt(ipAddress.getAddress()[3]);
        return lowOrderOctet;
    }
    public boolean isRouterOk() {
        return router!=null&&Exec.canWePing(router,1_000);
    }
    static void sleep(int n) {
        try {
            Thread.sleep(n);
        } catch(InterruptedException e) {
            p("sleep caught: "+e);
        }
    }
    public String statistics() {
        String string="";
        string+="failures: "+Arrays.asList(sendFailures)+'\n';
        string+="sends:    "+Arrays.asList(sends)+'\n';
        string+="receives: "+Arrays.asList(receives);
        return string;
    }
    protected void loop() {
        if(socketHandler!=null) if(socketHandler.failed) {
            l.warning("socket handler failed");
            socketHandler=null;
        }
        l.info("loop: "+loops+" "+myInetAddress+" "+instance().isListening());
        try {
            while(myInetAddress==null) { // may change when router cycles power
                l.warning("we do not know our ip address!");
                if(false) try {
                    myInetAddress=InetAddress.getLocalHost();
                } catch(UnknownHostException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                else myInetAddress=group.findMyInetAddress(router);
                if(myInetAddress!=null) l.warning("found my ip address: "+myInetAddress);
                else sleep(sleep);
            }
            while(!isRouterOk()) { // maybe this should be first?
                l.warning("router is not up!");
                myInetAddress=null; // may change when router cycles power
                sleep(sleep);
            }
            if(instance().isListening) {
                boolean ok=Exec.canWePing(logServerHost,1_000);
                if(ok) {
                    l.info("we can ping the log server: "+logServerHost);
                    if(socketHandler==null) {
                        socketHandler=IO.mySocketHandler(logServerHost,LogServer.defaultLogServerService,l);
                        if(socketHandler!=null) {
                            l.addHandler(socketHandler);
                            l.info("added socket handler to: "+logServerHost);
                        } else l.info("could not add socket handler to: "+logServerHost);
                    } else {
                        l.info("socket handler is probably logging");
                    }
                } else {
                    l.warning("can not ping log server: "+logServerHost);
                    if(socketHandler!=null) {
                        p("closing socket handler.");
                        socketHandler.close();
                        l.removeHandler(socketHandler);
                        socketHandler=null;
                    }
                }
            } else {
                l.warning("not listening.");
                boolean ok=instance().startListening();
                if(ok) l.info("start listening on: "+instance().acceptor.toString());
                else l.warning("can not start listening");
            }
            if(!isRouterOk()) {
                l.warning("router is not ok.");
                if(socketHandler!=null) {
                    p("closing socket handler.");
                    socketHandler.close();
                    l.removeHandler(socketHandler);
                    socketHandler=null;
                }
                if(instance().isListening) {
                    l.warning("something is not working, stopping listening.");
                    instance().stopListening();
                }
            }
            l.info("\n"+statistics());
            //printStats();
            p("-----");
            printThreads();
            p("-----");
            if(socketHandler!=null) {
                socketHandler.flush();
            }
            sleep(sleep);
        } catch(Exception e) {
            l.severe(this+" caught: "+e);
        }
        loops++;
        if(loops>200) sleep=longSleep;
        else if(loops>10) sleep=mediumSleep;
    }
    @Override public void run() {
        Audio.audio.play(Audio.Sound.store_door_chime_mike_koenig_570742973);
        p("router: "+router);
        l.info("enter run");
        while(true) {
            loop();
        }
    }
    public static void store(File file,Properties properties) {
        try {
            Writer writer=new FileWriter(file);
            properties.store(writer,"initial");
            writer.close();
            p("created: "+properties);
        } catch(IOException e) {
            p("can not store: "+file);
        }
    }
    public static Properties properties(File file) {
        Properties properties=new Properties(defaultProperties);
        if(!file.exists()) store(file,defaultProperties);
        try {
            FileReader fileReader=new FileReader(file);
            properties.load(fileReader);
            p("loaded: "+properties);
            fileReader.close();
            String ignore=properties.getProperty(ignorePropertyName);
            if(ignore.equals("true")) { // so we do not have to edit this file on the tablet
                p("ingoring properties file, using defaults: "+defaultProperties);
                properties=defaultProperties;
            }
        } catch(IOException e) {
            p("can not load: "+file);
            p("using: "+properties);
        }
        return properties;
    }
    public static void findRouter(Properties properties) {
        String router=properties.getProperty("router","");
        p("router: "+router);
        Set<String> excludedRouters=new TreeSet<>();
        for(PropertiesSubMenuItem item:PropertiesSubMenuItem.values()) {
            p(item+" "+item.string);
            if(item.name().startsWith("excludedRouter")) {
                p(item+" "+properties.getProperty(item.string,""));
                if(properties.getProperty(item.string,"").equals("true")) excludedRouters.add(item.string);
            }
        }
        p("excluded routers: "+excludedRouters);
        // this is broken, fix it!
        if(router.equals("")) {
            Set<InetAddress> routersWeCanPing=routersWeCanPing(5);
            if(routersWeCanPing.size()>0) {
                for(InetAddress inetAddress:routersWeCanPing) {
                    String address=inetAddress.getHostAddress();
                    p("trye address: "+address);
                    if(excludedRouters.contains(address)) l.config("skipping excluded router: "+address);
                    else {
                        l.config("using first pingable router: "+address);
                        router=address;
                        break;
                    }
                }
                if(router.equals("")) l.warning("no routers that are not excluded!");
            } else l.warning("no routers we can ping!");
        }
        if(router.equals("")) l.severe("can not find router!");
        else {
            properties.setProperty("router",router);
            store(new File(Main.propertiesFilename),properties);
        }
    }
    public static void main(String[] args) throws Exception {
        String currentIP=InetAddress.getLocalHost().toString();
        String subnet=getSubnet(currentIP);
        p(currentIP);
        p(subnet);
        // tests will put their log files in the same place?
        // we can make this find the router?
        // yes, we could iterate through interfaces, but they may not be up.
        // we could see what we can ping, but they may not be up.
        // so worst case, we need to know the router.
        // and we can probably get by trying 192.168.0.1 or 192.168.1.1. 
        // we need to know log server's address which is not static
        // so after a download or an install, we must edit the properties file
        // and enter the address of the log server if there is one,
        //
        // cycling power on the router causes the tablets to connect to my router.
        // try cycling power on the laptop
        // and see if tablets can reestablish socket handler. 
        //
        // if we come up and there is no wifi connected
        // we need to try to connect
        // if we are connected
        // we could just use the ip and deduce the router
        // case 1: we know the ssid and the router
        //              make sure the correct wifi is connected
        //              ensure that the router agrees with the ip address
        // case 2: we know the ssid and do not know the router
        //              make sure the correct wifi is connected
        //              deduce the router from the ip address
        // case 3: we do not know the ssid and do know the router
        //              connect to some wifi
        //              ensure that the router agrees with the ip address
        // case 4: we do not know the ssid and do not know the router
        //              connect to some wifi
        //              deduce the ssid from wifi manager and deduce router from the ip address
        // maybe start out with no ssid and no router
        // and save what we can deduce in a properties file.
        Properties properties=properties(new File(propertiesFilename));
        p("propertied: "+properties);
        logging();
        p("rounters we can ping: "+routersWeCanPing(5));
        addFileHandler(l,new File(logFileDirectory),"main");
        p("local host: "+InetAddress.getLocalHost());
        while(properties.getProperty("router","").equals("")) {
            findRouter(properties);
            Thread.sleep(1_000);
        }
        Integer first=new Integer(properties.getProperty("first","100"));
        Integer last=new Integer(properties.getProperty("last","131"));
        Group group=new Group(first,last,false);
        Main main=new Main(properties,group,Model.mark1);
        main.run();
    }
    public final Properties properties;
    public int sleep=shortSleep;
    public final String router,logServerHost;
    public final Integer myService; // just for testing
    public volatile InetAddress myInetAddress;
    public final Integer[] sends,sendFailures,receives;
    public final Model model;
    private Tablet instance;
    Integer loops=0;
    final Group group;
    MySocketHandler socketHandler;
    public static Level defaultLevel=Level.WARNING;
    public static String ignorePropertyName="ignore";
    public static String propertiesFilename="tablet.properties"; // may screw up testing
    public static final Properties defaultProperties=new Properties();
    static {
        defaultProperties.setProperty(ignorePropertyName,"true");
        //defaultProperties.setProperty("ssid","\"Linksys48993\"");
        defaultProperties.setProperty(PropertiesSubMenuItem.excludedRouter0.string,"true");
        //defaultProperties.setProperty(PropertiesSubMenuItem.excludedRouter1.string,"true");
        defaultProperties.setProperty(PropertiesSubMenuItem.excludedRouter2.string,"false");
        //defaultProperties.setProperty("router","192.168.2.1");
        defaultProperties.setProperty("logServerHost","192.168.2.127");
        defaultProperties.setProperty("first","100");
        defaultProperties.setProperty("last","131");
    }
    public static final Properties testProperties=new Properties();
    static {
        testProperties.setProperty(ignorePropertyName,"false");
        testProperties.setProperty("router","192.168.1.1");
        testProperties.setProperty("logServerHost","localhost");
        testProperties.setProperty("first","100");
        testProperties.setProperty("last","131");
    }
    public static final Integer shortSleep=1_000,mediumSleep=10_000,longSleep=100_000;
}
