package p;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.util.*;
import java.util.logging.*;
public class IO {
    public interface Consumer<T> { // instead of 1.8 definition
        void accept(T t);
    }
    public static class Et {
        public Et() {
            reset();
        }
        public void reset() {
            t0=System.nanoTime();
        }
        public long t0() {
            return t0;
        }
        public long dt() {
            return System.nanoTime()-t0();
        }
        public double etms() {
            return etms(dt());
        }
        @Override public String toString() {
            return etms()+" ms.";
        }
        public static double etms(long dt) {
            return dt/1000000.; // 1_000_000. breaks cobertura
        }
        private Long t0;
    }
    static class Connection extends Thread {
        Connection(Socket socket,Consumer<String> consumer,Consumer<Exception> exceptionConsumer,boolean outGoing) {
            this.socket=socket;
            this.stringConsumer=consumer;
            this.exceptionConsumer=exceptionConsumer;
            setName("connection #"+serialNumber+(outGoing?" to: ":" from: ")+socket);
            InputStream inputStream=null;
            try {
                inputStream=socket.getInputStream();
            } catch(IOException e) {
                e.printStackTrace();
            }
            in=new BufferedReader(new InputStreamReader(inputStream));
            try {
                out=new OutputStreamWriter(socket.getOutputStream());
            } catch(IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        boolean send(String string) {
            try {
                out.write(string+'\n');
                out.flush();
                sent++;
                return true;
            } catch(IOException e) {
                if(isClosing) ;
                else e.printStackTrace();
                return false;
            }
        }
        @Override public void run() {
            try {
                while(true) {
                    String string=in.readLine();
                    if(stringConsumer!=null) stringConsumer.accept(string);
                    if(string!=null) received++;
                    else break;
                }
            } catch(Exception e) {
                if(isClosing) ;
                else {
                    p(this+" caught unexpected: "+e);
                    //e.printStackTrace();
                    unexpected++;
                    // maybe keep the names of these in a list or ?
                    if(exceptionConsumer!=null) exceptionConsumer.accept(e);
                }
            }
        }
        public void close() {
            synchronized(isClosing) {
                isClosing=true;
                try {
                    socket.close();
                    // p(Thread.currentThread().getName()+" joining with:
                    // "+this);
                    try {
                        this.join(0);
                        // p("joined with: "+this);
                        // p("thread: "+toS(this));
                        if(this.getState().equals(Thread.State.TERMINATED)||this.getState().equals(Thread.State.NEW)) ;
                        else p("thread has strange state after join: "+toS(this));
                    } catch(InterruptedException e) {
                        p("oops 1");
                        e.printStackTrace();
                    }
                } catch(IOException e) {
                    p("oops 2");
                    e.printStackTrace();
                }
            }
        }
        final Consumer<String> stringConsumer;
        final Consumer<Exception> exceptionConsumer;
        final Socket socket;
        final BufferedReader in;
        final Writer out;
        volatile Boolean isClosing=false;
        int sent,received,unexpected; // mostly for testing
        final int serialNumber=++serialNumbers;
        static int serialNumbers=0;
    }
    static class Acceptor extends Thread {
        protected Acceptor(ServerSocket serverSocket,Consumer<Socket> consumer) {
            this.serverSocket=serverSocket;
            this.consumer=consumer;
            setName("acceptor #"+serialNumber+" on: "+serverSocket);
        }
        @Override public void run() {
            //p(toS(this));
            while(!done)
                try {
                    Socket socket=serverSocket.accept();
                    // should check socket's address in group's range for
                    // security
                    if(consumer!=null) consumer.accept(socket);
                } catch(IOException e) {
                    if(isClosing) ;// p("closing, caught: "+e);
                    else {
                        p("unexpected, caught: "+e);
                        e.printStackTrace();
                    }
                    done=true;
                }
        }
        public void close() {
            synchronized(isClosing) {
                isClosing=true;
                try {
                    // p("closing server socket.");
                    serverSocket.close();
                    // p(Thread.currentThread().getName()+" joining with:
                    // "+this);
                    try {
                        this.join(3);
                        // p("joined with: "+this);
                        // p("thread: "+toS(this));
                        if(!this.getState().equals(Thread.State.TERMINATED)) p("2 thread has strange state after join: "+toS(this));
                    } catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
        static boolean bind(ServerSocket serverSocket,SocketAddress socketAddress) { // move to acceptor?
            if(serverSocket==null) try {
                serverSocket=new ServerSocket();
            } catch(IOException e) {
                e.printStackTrace();
            }
            if(serverSocket!=null) {
                if(!serverSocket.isBound()) try {
                    if(socketAddress!=null) {
                        serverSocket.bind(socketAddress);
                        return true;
                    }
                } catch(IOException e) {
                    p("bind exception!");
                    // e.printStackTrace();
                }
            }
            return false;
        }
        static Acceptor acceptor(SocketAddress socketAddress,Consumer<Socket> socketConsumer) {
            try {
                ServerSocket serverSocket=new ServerSocket();
                boolean ok=bind(serverSocket,socketAddress);
                if(ok) return new Acceptor(serverSocket,socketConsumer);
            } catch(IOException e) {
                e.printStackTrace();
            }
            return null;
        }
        public final ServerSocket serverSocket;
        final Consumer<Socket> consumer;
        boolean done;
        volatile Boolean isClosing=false;
        final int serialNumber=++serialNumbers;
        static int serialNumbers=0;
    }
    public static void pn(PrintStream out,String string) {
        out.print(string);
        out.flush();
    }
    public static void pn(String string) {
        synchronized(System.out) {
            pn(System.out,string);
        }
    }
    public static void p(PrintStream out,String string) {
        synchronized(out) {
            pn(out,string);
            pn(out,System.getProperty("line.separator"));
        }
    }
    public static void p(String string) {
        p(System.out,string);
    }
    public static String toS(Thread thread) {
        return "thread: name: "+thread.getName()+", state: "+thread.getState()+", is alive: "+thread.isAlive()+", is interrupted:  "+thread.isInterrupted();
    }
    public static String toS(ServerSocket serverSocket) {
        return serverSocket+": isBound: "+serverSocket.isBound()+", isClosed: "+serverSocket.isClosed();
    }
    public static String toS(InterfaceAddress interfaceAddress) {
        InetAddress a=interfaceAddress.getAddress();
        String s=""+interfaceAddress.getAddress()+" "+interfaceAddress.getNetworkPrefixLength();
        s+=" "+a.isSiteLocalAddress()+" "+a.isAnyLocalAddress()+" "+a.isLinkLocalAddress();
        return s;
    }
    public static void fromFile(final StringBuffer stringBuffer,final File file) {
        try {
            Reader r=new FileReader(file);
            int c=0;
            while((c=r.read())!=-1)
                stringBuffer.append((char)c);
            r.close();
        } catch(FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static Thread[] getThreads() {
        int big=2*Thread.activeCount();
        Thread[] threads=new Thread[big];
        Thread.enumerate(threads);
        return threads;
    }
    public static void printThreads(List<String> excluded) {
        // p("enter print threads");
        Thread[] threads=getThreads();
        for(Thread thread:threads)
            if(thread!=null&&excluded!=null&&!excluded.contains(thread.getName())) p(toS(thread));
        // p("exit print threads");
    }
    public static void printThreads() {
        printThreads(Collections.<String> emptyList());
    }
    static String toHexString(byte[] bytes) {
        String theBytes="";
        for(int i=0;i<bytes.length;i++) {
            String theByte=Integer.toHexString(bytes[i]&0xff);
            if(theByte.length()<2) theByte='0'+theByte;
            theBytes+=theByte;
        }
        return theBytes;
    }
    static void filterNetworkInterface(NetworkInterface networkInterface,InterfaceAddress interfaceAddress,Consumer<InterfaceAddress> consumer) {
        //p("Name: "+networkInterface.getName());
        //p("networkInterface: "+networkInterface);
        try {
            if(!networkInterface.isLoopback()) {
                //p("interfaceAddress: "+toS(interfaceAddress));
                consumer.accept(interfaceAddress);
            } else;
        } catch(SocketException e) {
            p(networkInterface+" isLoopback() caught: "+e);
        } //p("loopback.");
    }
    static void filterNetworkInterface(NetworkInterface networkInterface,Consumer<InterfaceAddress> consumer) {
        Enumeration<InetAddress> inetAddresses=networkInterface.getInetAddresses();
        List<InterfaceAddress> interfaceAddresses=networkInterface.getInterfaceAddresses();
        if(interfaceAddresses.size()>0) {
            //p("display name: "+networkInterface.getDisplayName());
            for(InterfaceAddress interfaceAddress:interfaceAddresses)
                filterNetworkInterface(networkInterface,interfaceAddress,consumer);
        } else;//p(networkInterface.getDisplayName()+" has no addresses.");
    }
    public static void filterNetworkInterfaces(Consumer<InterfaceAddress> consumer) {
        try {
            Enumeration<NetworkInterface> netowrkInterfaces=NetworkInterface.getNetworkInterfaces();
            for(NetworkInterface networkInterface:Collections.list(netowrkInterfaces))
                filterNetworkInterface(networkInterface,consumer);
        } catch(SocketException e) {
            p("getNetworkInterfaces() caught: "+e);
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    static Set<String> hosts() {
        Set<String> hosts=new TreeSet<>();
        hosts.add("127.0.0.1");
        hosts.add("localhost");
        InetAddress inetAddress;
        try {
            inetAddress=InetAddress.getLocalHost();
            hosts.add(inetAddress.getHostAddress());
        } catch(UnknownHostException e) {
            p("get local host throws: "+e);
        }
        Set<InterfaceAddress> interfaceAddresses=findMyInterfaceAddressesOnRouter(null);
        for(InterfaceAddress interfaceAddress:interfaceAddresses) {
            String host=interfaceAddress.getAddress().getHostAddress();
            hosts.add(host);
        }
        return hosts;
    }
    // finds addresses on the router
    static Set<InterfaceAddress> findMyInterfaceAddressesOnRouter(final String router) {
        final Set<InterfaceAddress> networkInterfaces=new LinkedHashSet<>();
        IO.filterNetworkInterfaces(new Consumer<InterfaceAddress>() {
            @Override public void accept(InterfaceAddress interfaceAddress) {
                int length=interfaceAddress.getNetworkPrefixLength();
                switch(length) {
                    case 8:
                    case 16:
                    case 24:
                        //p("length and ip4 interface address: "+length+", "+interfaceAddress);
                        if(router==null||isOnRouter(router,interfaceAddress)) {
                            //p(interfaceAddress+" is on router: "+router);
                            networkInterfaces.add(interfaceAddress);
                        }
                        break;
                    case 10:
                    case 128:
                        //p("length and ip6 interface address: "+length+", "+interfaceAddress);
                        break;
                    default:
                        //p("length and strange interface address: "+length+", "+interfaceAddress);
                        break;
                }
            }
        });
        return networkInterfaces;
    }
    static boolean isOnRouter(String router,int n,InetAddress interfaceInetAddress) throws UnknownHostException {
        InetAddress routersAddress=InetAddress.getByName(router);
        byte[] bytes=routersAddress.getAddress();
        BigInteger routersAddressAsInteger=new BigInteger(bytes);
        bytes=interfaceInetAddress.getAddress();
        BigInteger interfacesAddressAsInteger=new BigInteger(bytes);
        if(bytes.length==4) {
            BigInteger mask=BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE).shiftLeft(32-n);
            if(routersAddressAsInteger.and(mask).equals(interfacesAddressAsInteger.and(mask))) {
                return true;
            } else;//p("does not match router.");
        } else;//p("ip6 address.");
        return false;
    }
    public static boolean isOnRouter(String router,InterfaceAddress interfaceAddress) {
        try {
            int n=interfaceAddress.getNetworkPrefixLength();
            InetAddress interfaceInetAddress=interfaceAddress.getAddress();
            return isOnRouter(router,n,interfaceInetAddress);
        } catch(UnknownHostException e) {
            p("isOnRouter caught: "+e);
        }
        return false;
    }
    public static SocketHandler socketHandler(String host,Integer service) {
        try {
            SocketHandler socketHandler=new SocketHandler(host,service);
            // socketHandler.setFormatter(new LoggingHandler());
            socketHandler.setLevel(Level.ALL);
            return socketHandler;
        } catch(IOException e) {
            //p("caught: '"+e+"' constructing socket handler on: "+host+":"+service);
        }
        return null;
    }
    public static void addFileHandler(Logger logger,File logFileDirectory,String prefix) {
        if(!logFileDirectory.exists()) {
            if(logFileDirectory.mkdir()) p("created: "+logFileDirectory);
            else p("can not create: "+logFileDirectory);
        }
        try {
            String pattern=prefix+".%u.%g.log";
            File logFile=new File(logFileDirectory,pattern);
            //p("log file get path: "+logFile.getPath());
            Handler handler=new FileHandler(logFile.getPath(),50_000_000,10,false);
            handler.setLevel(Level.ALL);
            logger.addHandler(handler);
            logger.warning("added file handler: "+handler);
        } catch(Exception e) {
            logger.warning("add file handler caught: "+e);
        }
    }
    public static void main(String args[]) {
        Logger logger=Logger.getLogger("xyzzy");
        addFileHandler(logger,new File(logFileDirectory),"IO");
        try {
            Enumeration<NetworkInterface> netowrkInterfaces=NetworkInterface.getNetworkInterfaces();
            for(NetworkInterface networkInterface:Collections.list(netowrkInterfaces))
                p("interface: "+networkInterface.getName()+" "+networkInterface.isUp()+" "+networkInterface.isLoopback()+" "+networkInterface.isPointToPoint()
                        +networkInterface.getInterfaceAddresses());
        } catch(SocketException e) {
            p("getNetworkInterfaces() caught: "+e);
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    public static String logFileDirectory="logFileDirectory";
    static Integer testService=12345;
}
