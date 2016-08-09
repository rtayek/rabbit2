package p;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.util.*;
import java.util.logging.Logger;
import static p.Main.IO.*;
public class Main implements Runnable {
    public Main(String router,Group group,Model model) {
        // group and model are required to construct.
        // router is required to construct.
        // ip address is not required to construct
        // ip address is required to broadcast or receive
        // so maybe make these setable
        // and guard everything with a test for non null?
        this.router=router;
        this.group=group;
        this.model=model;
        // model.addObserver(new Audio.AudioObserver(model));
    }
    interface Consumer<T> {
        void accept(T t);
    }
    static class Single<T> { // was used by message factory instead of static -
                                 // helps with testing if we need to have a
                             // message sequence number
        public Single(T t) {
            this.t=t;
        }
        public T t;
    }
    public static class IO {
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
                if(thread!=null&&!excluded.contains(thread.getName())) p(toS(thread));
            // p("exit print threads");
        }
        public static void printThreads() {
            printThreads(Collections.emptyList());
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
        static boolean isOnRouter(String router,InterfaceAddress interfaceAddress) {
            try {
                InetAddress routersAddress=InetAddress.getByName(router);
                byte[] bytes=routersAddress.getAddress();
                BigInteger routersAddressAsInteger=new BigInteger(bytes);
                int n=interfaceAddress.getNetworkPrefixLength();
                bytes=interfaceAddress.getAddress().getAddress();
                BigInteger interfacesAddressAsInteger=new BigInteger(bytes);
                if(bytes.length==4) {
                    BigInteger mask=BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE).shiftLeft(32-n);
                    if(routersAddressAsInteger.and(mask).equals(interfacesAddressAsInteger.and(mask))) {
                        return true;
                    } else;//p("does not match router.");
                } else;//p("ip6 address.");
            } catch(UnknownHostException e) {}
            return false;
        }
        static void filterNetworkInterface(NetworkInterface networkInterface,InterfaceAddress interfaceAddress,Consumer<InterfaceAddress> consumer) throws SocketException {
            //p("Name: "+networkInterface.getName());
            //p("networkInterface: "+networkInterface);
            if(!networkInterface.isLoopback()) {
                //p("interfaceAddress: "+toS(interfaceAddress));
                consumer.accept(interfaceAddress);
            } else;//p("loopback.");
        }
        static void filterNetworkInterface(NetworkInterface networkInterface,Consumer<InterfaceAddress> consumer) throws SocketException {
            Enumeration<InetAddress> inetAddresses=networkInterface.getInetAddresses();
            List<InterfaceAddress> interfaceAddresses=networkInterface.getInterfaceAddresses();
            if(interfaceAddresses.size()>0) {
                //p("display name: "+networkInterface.getDisplayName());
                for(InterfaceAddress interfaceAddress:interfaceAddresses)
                    filterNetworkInterface(networkInterface,interfaceAddress,consumer);
            } else;//p(networkInterface.getDisplayName()+" has no addresses.");
        }
        public static void filterNetworkInterfaces(Consumer<InterfaceAddress> consumer) throws SocketException {
            Enumeration<NetworkInterface> netowrkInterfaces=NetworkInterface.getNetworkInterfaces();
            for(NetworkInterface networkInterface:Collections.list(netowrkInterfaces))
                filterNetworkInterface(networkInterface,consumer);
        }
        static class Connection extends Thread {
            // maybe only should implement runnable?
            Connection(Socket socket,Consumer<String> consumer,boolean outGoing) {
                this.socket=socket;
                this.consumer=consumer;
                setName("connection #"+serialNumber+(outGoing?" from: ":" to: ")+socket);
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
            String read() {
                String string;
                try {
                    string=in.readLine();
                    return string;
                } catch(IOException e) {
                    if(isClosing) p("closing");
                    else e.printStackTrace();
                }
                return null;
            }
            boolean send(String string) {
                try {
                    // p("sending: '"+string+'\'');
                    out.write(string+'\n');
                    out.flush();
                    return true;
                } catch(IOException e) {
                    if(isClosing) p("closing");
                    else e.printStackTrace();
                    return false;
                }
            }
            @Override public void run() {
                Thread.currentThread().setName("listening on: "+socket);
                // p(toS(this));
                // maybe put this is a try block to catch anything wierd?
                while(!done) {
                    String string=read();
                    received++;
                    if(string!=null) {
                        if(consumer!=null) consumer.accept(string);
                    } else done=true;
                }
            }
            public void close() {
                synchronized(isClosing) {
                    isClosing=true;
                    try {
                        // p("closing socket.");
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
                            e.printStackTrace();
                        }
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            final Consumer<String> consumer;
            private final Socket socket;
            final BufferedReader in;
            final Writer out;
            boolean done;
            volatile Boolean isClosing=false;
            int received;
            final int serialNumber=++serialNumbers;
            static int serialNumbers=0;
        }
        static class Acceptor extends Thread {
            Acceptor(ServerSocket serverSocket,Consumer<Socket> consumer) {
                this.serverSocket=serverSocket;
                this.consumer=consumer;
                setName("acceptor #"+serialNumber+" on: "+serverSocket);
            }
            @Override public void run() {
                // p(toS(this));
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
            public final ServerSocket serverSocket;
            final Consumer<Socket> consumer;
            boolean done;
            volatile Boolean isClosing=false;
            final int serialNumber=++serialNumbers;
            static int serialNumbers=0;
        }
    }
    public static class Group { // no need to clone unless we start storing
                                    // history or something in here.
        public Group(int first,int last,boolean sameInetAddress) {
            this.first=first;
            this.last=last;
            other=0;
            other2=0;
            this.sameInetAddress=sameInetAddress;
        }
        boolean isInetAddressInRange(InetAddress inetAddress) {
            int lowOrderOctet=lowOrderOctet(inetAddress);
            return first<=lowOrderOctet&&lowOrderOctet<=last;
        }
        int service(InetAddress ipAddress) {
            int lowOrderOctet=lowOrderOctet(ipAddress);
            int service=serviceBase+lowOrderOctet;
            return service;
        }
        Set<InetSocketAddress> socketAddresses(InetAddress myInetAddress) {
            Set<InetSocketAddress> socketAddresses=new LinkedHashSet<>();
            // InetAddress ipAddress=inetSocketAddress.getAddress();
            if(sameInetAddress) for(int i=first;i<=last;i++)
                socketAddresses.add(new InetSocketAddress(myInetAddress,serviceBase+i));
            else {
                byte[] bytes=myInetAddress.getAddress();
                for(int i=first;i<=last;i++) {
                    bytes[3]=(byte)i;
                    try {
                        InetAddress inetAddress=InetAddress.getByAddress(bytes);
                        socketAddresses.add(new InetSocketAddress(inetAddress,serviceBase+i));
                    } catch(UnknownHostException e) {
                        e.printStackTrace();
                    }
                }
            }
            return socketAddresses;
        }
        private boolean send(String string,InetSocketAddress inetSocketAddress) {
            Socket socket;
            try {
                socket=new Socket(inetSocketAddress.getAddress(),inetSocketAddress.getPort());
            } catch(IOException e) {
                e.printStackTrace();
                return false;
            }
            Connection connection=new Connection(socket,null,true);
            boolean ok=connection.send(string);
            connection.close();
            return ok;
        }
        private int broadcast(String string,InetAddress myInetAddress) {
            int connectionOrSendFailures=0;
            for(InetSocketAddress socketAddress:socketAddresses(myInetAddress)) {
                boolean ok=send(string,socketAddress);
                if(!ok) connectionOrSendFailures++;
            }
            if(connectionOrSendFailures>0) p("broadcast has: "+connectionOrSendFailures+" failure(s)");
            return connectionOrSendFailures;
        }
        final Integer first,last,other,other2;
        public final int serviceBase=10_000;
        final boolean sameInetAddress;
    }
    public class Tablet {
        private Tablet() {}
        boolean receive(String string) {
            if(string.length()!=model.buttons) {
                p("bad message: "+string);
                return false;
            }
            for(int i=0;i<string.length();i++) {
                char c=string.charAt(i);
                if(c=='T'||c=='F') {
                    boolean newState=c=='T'?true:false;
                    model.setState(i+1,newState);
                } else p("bad string: "+string);
            }
            return true;
        }
        public synchronized boolean isListening() {
            return isListening;
        }
        public synchronized boolean startListening(SocketAddress socketAddress) {
            try {
                ServerSocket serverSocket=new ServerSocket();
                serverSocket.bind(socketAddress);
                if(serverSocket.isBound()) {
                    acceptor=new Acceptor(serverSocket,socket-> {
                        // p("accepted new connection:"+socket);
                        Connection connection=new Connection(socket,string-> {
                            receive(string);
                        },false);
                        connection.start();
                    });
                    acceptor.start();
                    isListening=true;
                    return true;
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
            return false;
        }
        public synchronized void stopListening() {
            acceptor.close();
            isListening=false;
            // how about the connections?
            // close them also?
        }
        public void click(int id,InetAddress myInetAddress) {
            // p("click: "+id+" in: "+this);
            try {
                if(1<=id&&id<=model.buttons) {
                    synchronized(model) {
                        if(model.resetButtonId!=null&&id==model.resetButtonId) model.reset();
                        else model.setState(id,!model.state(id));
                        if(myInetAddress!=null) {
                            String message=model.toCharacters();
                            int connectionOrSendFailures=group.broadcast(message,myInetAddress);
                        }
                    }
                } else l.warning(id+" is not a model button!");
            } catch(Exception e) {
                l.severe("click caught: "+e);
                e.printStackTrace();
            }
        }
        Acceptor acceptor;
        private boolean isListening;
    }
    public Tablet instance() {
        if(instance==null) instance=new Tablet();
        return instance;
    }
    public static boolean isAndroid() {
        return System.getProperty("http.agent")!=null;
    }
    public boolean isRouterOk() {
        return router!=null&&Exec.canWePing(router,1_000);
    }
    InetAddress findMyInetAddress() {
        Set<InterfaceAddress> networkInterfaces;
        try {
            networkInterfaces=findMyInetAddresses();
            //p("on router's network: "+networkInterfaces);
            if(networkInterfaces.size()>0) {
                InetAddress inetAddress=networkInterfaces.iterator().next().getAddress();
                if(group.isInetAddressInRange(inetAddress)) {
                    //p("using: "+inetAddress);
                    return inetAddress;
                } ;//else p(inetAddress+" is not in range!");
            } else p("no inetAddresses!");
        } catch(SocketException e) {
            p("caught: "+e);
        }
        return null;
    }
    static void sleep(int n) {
        try {
            Thread.sleep(5_000);
        } catch(InterruptedException e) {
            p("caught: "+e);
        }
    }
    @Override public void run() {
        p("router: "+router);
        while(true) {
            if(router==null||myInetAddress==null||!isRouterOk()) {
                if(instance().isListening) {
                    p("something is not working, stopping listening.");
                    instance().stopListening();
                }
            }
            while(myInetAddress==null) {
                p("we do not know our ip address!");
                myInetAddress=findMyInetAddress();
                sleep(5_000);
            }
            p("ip address: "+myInetAddress);
            while(!isRouterOk()) {
                p("router in not up!");
                sleep(5_000);
            }
            p("router is up.");
            if(!instance().isListening) {
                int service=group.serviceBase+group.first;
                InetSocketAddress inetSocketAddress=new InetSocketAddress(myInetAddress,service);
                boolean ok=instance().startListening(inetSocketAddress);
                if(ok) p("listening.");
            }
            sleep(5_000);
        }
    }
    static int lowOrderOctet(InetAddress ipAddress) {
        int lowOrderOctet=Byte.toUnsignedInt(ipAddress.getAddress()[3]);
        return lowOrderOctet;
    }
    public static void main(String[] args) throws Exception {
        int first=100,n=32;
        Group group=new Group(first,first+n-1,false);
        new Main(defaultRouter,group,Model.mark1).run();
    }
    Set<InterfaceAddress> findMyInetAddresses() throws SocketException {
        final Set<InterfaceAddress> networkInterfaces=new LinkedHashSet<>();
        IO.filterNetworkInterfaces(new Consumer<InterfaceAddress>() {
            @Override public void accept(InterfaceAddress interfaceAddress) {
                if(isOnRouter(router,interfaceAddress)) {
                    //p(interfaceAddress+" is on router: "+router);
                    networkInterfaces.add(interfaceAddress);
                }
            }
        });
        return networkInterfaces;
    }
    public final String router;
    volatile InetAddress myInetAddress;
    final Single<Integer> single=new Single<>(0); // maybe it's just an int now?
    public final Model model;
    private Tablet instance;
    final Group group;
    public final Logger l=Logger.getLogger("xyzzy");
    public static final String defaultRouter="192.168.0.1";
}
