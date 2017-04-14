package p;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;
import static p.IO.*;
import p.IO.Acceptor;
public class Main implements Runnable {
    // make this work without knowing the ssid of the router
    // use un-routable or specify prefix or take whatever?
    // lets use our router for a while
    public Main(Properties properties,Logger logger,Group group,Model model) {
        this(properties,logger,group,model,null);
        if(group.sameInetAddress) throw new RuntimeException("use ctor with service!");
    }
    public Main(Properties properties,Logger logger,Group group,Model model,Integer myService) {
        // group and model are required to construct.
        // router is required to construct.
        // ip address is not required to construct
        // ip address is required to broadcast or receive
        // so maybe make these setable
        // and guard everything with a test for non null?
        router=properties.getProperty("router");
        logServerHost=properties.getProperty("logServerHost");
        l=logger;
        l.warning(this+" constructed at: "+new Date());
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
    public static class Group { // no need to clone unless we start storing
        // history or something in here.
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
        InetAddress findMyInetAddress(String router) {
            Set<InterfaceAddress> networkInterfaces;
            networkInterfaces=IO.findMyInterfaceAddressesOnRouter(router);
            if(networkInterfaces.size()>0) {
                if(networkInterfaces.size()>1) p("more than one network interface: "+networkInterfaces);
                InetAddress inetAddress=networkInterfaces.iterator().next().getAddress();
                if(isInGroup(inetAddress)) {
                    p("using: "+inetAddress);
                    return inetAddress;
                } else p(inetAddress+" is not in group: "+this);
            } else p("no inetAddresses!");
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
            Socket socket;
            boolean old=false;
            if(old) {
                try {
                    socket=new Socket(inetSocketAddress.getAddress(),inetSocketAddress.getPort());
                } catch(IOException e) {
                    //p("caught: "+e);
                    return false;
                }
            } else {
                Et et=new Et();
                socket=new Socket();
                try {
                    socket.connect(inetSocketAddress,connectionTimeout);
                } catch(Exception e) {
                    //p("caught: "+e);
                    return false;
                }
            }
            Connection connection=new Connection(socket,null,null,true);
            boolean ok=connection.send(string);
            connection.close();
            return ok;
        }
        @Override public String toString() {
            return "Group [first="+first+", last="+last+", other="+other+", other2="+other2+", serviceBase="+serviceBase+", sameInetAddress="+sameInetAddress+"]";
        }
        private final Integer first,last,other,other2;
        public int connectionTimeout=300;
        public final int serviceBase=10_000;
        final boolean sameInetAddress;
    }
    public class Tablet {
        private Tablet() {}
        boolean receive(String string,InetAddress inetAddress) {
            boolean rc=false;
            if(!group.isInGroup(inetAddress)) l.warning("message from: "+inetAddress+" is not in group: "+string);
            else if(string==null) ;
            else if(string.isEmpty()) p("empty message from: "+inetAddress);
            else if(string.length()!=model.buttons) p("bad message: "+string);
            else {
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
                else p("address: "+inetAddress+" is out of range: "+group.first+':'+group.last);
            }
            return rc;
        }
        public synchronized boolean isListening() {
            return isListening;
        }
        // looks like we need to get rid of the lambdas
        // since they need api 24.
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
        private void broadcast(final String string,Integer myService) {
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
                        if(ok) p("send #"+broadcasts+" to: "+inetSocketAddress+" took: "+et);
                        else {
                            sendFailures[k]++;
                            p("send #"+broadcasts+" to: "+inetSocketAddress+" failed after: "+et);
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
    public static boolean isAndroid() {
        return System.getProperty("http.agent")!=null;
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
    void printStats() {
        p("failures: "+Arrays.asList(sendFailures));
        p("sends:    "+Arrays.asList(sends));
        p("receives: "+Arrays.asList(receives));
    }
    @Override public void run() {
        p("router: "+router);
        l.info("enter run");
        while(true) {
            l.info(myInetAddress+" "+instance().isListening());
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
                    sleep(sleep);
                }
                while(!isRouterOk()) { // maybe this should be first?
                    l.warning("router is not up!");
                    myInetAddress=null; // may change when router cycles power
                    sleep(sleep);
                }
                if(instance().isListening) {
                    if(socketHandler==null) {
                        socketHandler=IO.socketHandler(logServerHost,LogServer.defaultLogServerService);
                        if(socketHandler!=null) {
                            l.addHandler(socketHandler);
                            l.warning("added socket handler to: "+logServerHost);
                        } else p("could not add socket handler to: "+logServerHost);
                    }
                } else {
                    l.warning("not listening.");
                    boolean ok=instance().startListening();
                    if(ok) l.warning("start listening on: "+instance().acceptor.toString());
                    else l.warning("can not start listening");
                }
                if(!isRouterOk()) {
                    l.warning("router is not ok.");
                    if(socketHandler!=null) {
                        l.removeHandler(socketHandler);
                        socketHandler=null;
                    }
                    if(instance().isListening) {
                        p("something is not working, stopping listening.");
                        instance().stopListening();
                    }
                }
                printStats();
                printThreads();
                sleep(sleep);
            } catch(Exception e) {
                p(this+" caught: "+e);
            }
        }
    }
    public static Properties properties(File file) {
        Properties properties=new Properties(defaultProperties);
        if(!file.exists()) try {
            Writer writer=new FileWriter(file);
            defaultProperties.store(writer,"initial");
            writer.close();
            p("created: "+defaultProperties);
        } catch(IOException e) {
            p("can not store: "+file);
        }
        try {
            FileReader fileReader=new FileReader(file);
            properties.load(fileReader);
            p("loaded: "+properties);
        } catch(IOException e) {
            p("can not load: "+file);
        }
        return properties;
    }
    public static void main(String[] args) throws Exception {
        p("default properties: "+defaultProperties);
        Properties properties=properties(new File(propertiesFilename));
        Logger logger=Logger.getLogger("xyzzy");
        addFileHandler(logger,new File(logFileDirectory),"main");
        int first=100,n=20;
        Group group=new Group(first,first+n-1,false);
        p("group: "+group);
        new Main(properties,logger,group,Model.mark1).run();
    }
    public int sleep=10_000;
    public final String router,logServerHost;
    public final Integer myService; // just for testing
    public volatile InetAddress myInetAddress;
    public final Integer[] sends,sendFailures,receives;
    public final Model model;
    private Tablet instance;
    final Group group;
    public final Logger l;
    SocketHandler socketHandler;
    public static Level defaultLevel=Level.WARNING;
    public static final String propertiesFilename="tablet.properties";
    public static final Properties defaultProperties=new Properties();
    static {
        defaultProperties.setProperty("router","192.168.1.1");
        defaultProperties.setProperty("logServerHost","192.168.1.108");
        defaultProperties.setProperty("first","100");
        defaultProperties.setProperty("last","131");
    }
}
