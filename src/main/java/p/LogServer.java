package p;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.SocketHandler;
import static p.IO.*;
public class LogServer implements Runnable {
    public static class Pair<First,Second> {
        public Pair(First first,Second second) {
            this.first=first;
            this.second=second;
        }
        @Override public String toString() {
            return "["+first+","+second+"]";
        }
        public First first;
        public Second second;
    }    // need some way to end a log file when tablet stops
    // need some way to use a date or prefix.
    // combine this class with logging handler?
    private class LogFile {
        private LogFile(Socket socket,String prefix,int sequenceNumber) {
            this.socket=socket;
            this.prefix=prefix;
            this.sequenceNumber=sequenceNumber;
        }
        private int first() { // figure out something reasonable to do here.
            int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
            for(int i=1;i<99;i++)
                if(file(i).exists()) {
                    max=Math.max(max,i);
                    min=Math.min(min,i);
                }
            if(min>=50) return 1;
            else if(max<50) return 50;
            return 100;
        }
        private File file(int n) {
            return new File("log",name(n));
        }
        File file() {
            return new File("log",name(sequenceNumber));
        }
        private String name(int n) {
            InetAddress inetAddress=socket.getInetAddress();
            String address=inetAddress.getHostAddress();
            long t=System.currentTimeMillis()-newMillenium;
            t/=1000;
            String name=prefix!=null&&!prefix.equals("")?(prefix+"."):"";
            name+=""+t+".";
            name+=address+"."+socket.getLocalPort()+".";
            name+=serverSocket.getInetAddress().getHostAddress()+".";
            name+=n;
            name+=".xml";
            return name;
        }
        Socket socket;
        String prefix;
        int sequenceNumber=1;
    }
    public static class Factory { // is this just for testing? (i think so)
        public Factory(Writer writer) {
            this.writer=writer;
        }
        public Copier create(Socket socket,boolean verbose) {
            return new Copier(socket,writer,false);
        }
        final Writer writer;
    }
    public static class Copier extends Thread {
        public Copier(Socket socket,Writer out,boolean verbose) {
            super(""+socket.getRemoteSocketAddress());
            this.socket=socket;
            this.out=out;
            this.verbose=verbose;
        }
        public void flush() throws IOException {
            out.flush();
        }
        public void close() {
            try {
                if(verbose) p("closing: "+this);
                out.flush();
                out.close();
                socket.shutdownInput();
                socket.shutdownOutput();
                socket.close();
            } catch(IOException e) {
                // p("caught: "+e);
            }
        }
        @Override public void run() {
            try {
                //out.write("<!-- first line -->"); // illegal xml!
                out.flush();
                InputStream is=socket.getInputStream();
                BufferedReader br=new BufferedReader(new InputStreamReader(is,"US-ASCII"));
                String line=null;
                p("try first read:");
                boolean once=false;
                while((line=br.readLine())!=null) {
                    if(!once) {
                        p("got first read: "+line);
                        once=true;
                    }
                    if(line.contains("some weird thing")) rollover();
                    out.write(line+"\n");
                    out.flush();
                    if(verbose) p("copier wrote: "+line);
                    if(file!=null&&file.exists()&&file.length()>maxSize) if(line.equals("</record>")) {
                        rollover();
                        //out.write("<!-- rolled over -->");  // illegal xml!
                    }
                }
                p("end of file");
                //out.write("<!-- end of file -->");
            } catch(IOException e) {
                try {
                    if(isShuttingdown) {
                        p(this+" shutting down, caught: "+e);
                        //out.write("<!-- expected caught: '"+e+"' -->");
                    } else {
                        p(this+"not shutting down, caught: '"+e+"'");
                        //out.write("<!-- unexpected caught: '"+e+"' -->");
                        //e.printStackTrace();
                    }
                } catch(Exception e1) { // not needed if no writes!
                    p(this+" shutdown: write caught: "+e1);
                    e1.printStackTrace();
                }
            } finally {
                close();
            }
        }
        private void rollover() throws IOException {
            // close file and start new one
            p("closing log file");
            out.close();
            logFile.sequenceNumber++; // starts over at 1!
            File newFile=logFile.file();
            out=new FileWriter(newFile);
            file=newFile;
            p("rollover to: "+newFile);
        }
        final Socket socket;
        public final boolean verbose;
        Writer out;
        public File file; // may be null for testing
        LogFile logFile; // may be null for testing
        public volatile boolean isShuttingdown;
    }
    public LogServer(String host,int service,String prefix) throws IOException {
        this(host,service,null,prefix);
    }
    public LogServer(String host,int service,Factory factory,String prefix) {
        this.host=host;
        this.service=service;
        this.prefix=prefix!=null?prefix:"";
        InetAddress inetAddress;
        try {
            inetAddress=InetAddress.getByName(host);
            serverSocket=new ServerSocket(service,10/* what should this be?*/,inetAddress);
        } catch(IOException e) {
            p("can not create log server: "+e);
            p("make sure host: "+host+":"+service+" is up.");
            throw new RuntimeException(e);
        }
        this.factory=factory;
    }
    @Override public void run() {
        p("LogServer running on: "+serverSocket);
        while(true)
            if(!serverSocket.isClosed()&&!isShuttingDown) {
                Socket socket=null;
                try {
                    socket=serverSocket.accept();
                    p("accepted connection from: "+socket);
                    Copier copier=null;
                    if(factory!=null) copier=factory.create(socket,verbose);
                    else {
                        LogFile logFile=new LogFile(socket,prefix,1);
                        File file=logFile.file();
                        p("log file: "+file);
                        Writer out=new FileWriter(file);
                        copier=new Copier(socket,out,verbose);
                        copier.file=file;
                        copier.logFile=logFile;
                        synchronized(copiers) {
                            copiers.add(copier);
                        }
                    }
                    copier.start();
                    p("started copier: "+copier);
                } catch(IOException e) {
                    if(!isShuttingDown) {
                        p("log acceptor caught: '"+e+"'");
                        e.printStackTrace();
                    }
                } catch(Exception e) {
                    p("log acceptor caught: '"+e+"'");
                    e.printStackTrace();
                }
            } else break;
    }
    public void stop() throws IOException {
        synchronized(copiers) {
            for(Iterator<Copier> i=copiers.iterator();i.hasNext();) {
                Copier copier=i.next();
                if(copier.isAlive()) { // why test for alive?
                    copier.close();
                    i.remove();
                    // how to kill off thread?
                } else p(copier+" is not alive!");
            }
        }
        isShuttingDown=true;
        serverSocket.close();
    }
    public static void print() {}
    public static void main(String args[]) {
        for(Pair<String,Integer> pair:logServerHosts.keySet())
            if(pair.second.equals(defaultLogServerService)) try {
                LogServer logServer=new LogServer(pair.first,pair.second,null);
                new Thread(logServer).start();
                logServers.add(logServer);
            } catch(Exception e) {
                p("caught: '"+e+"'");
            }
        if(logServers.size()==0) {
            p("no log servers were created!");
            p("check the interfaces to see if they are up: "+logServerHosts.keySet());
        }
    }
    public static final int defaultLogServerService=5000;
    public static final String laptopToday="192.168.0.100";
    public static final Map<Pair<String,Integer>,SocketHandler> logServerHosts=new LinkedHashMap<>();
    static {
        for(Integer service:new Integer[] {defaultLogServerService,/*chainsawLogServerService,lilithLogServerService,*/}) {
            //logServerHosts.put(new Pair<String,Integer>(raysPc,service),null);
            //logServerHosts.put(new Pair<String,Integer>("192.168.0.138"/*raysPcOnTabletNetworkToday*/,service),null);
            logServerHosts.put(new Pair<String,Integer>(laptopToday,service),null);
        }
    }
    public static final Set<LogServer> logServers=new LinkedHashSet<>();
    public final String host;
    public final int service;
    public final String prefix;
    private boolean isShuttingDown;
    private final ServerSocket serverSocket;
    private final Factory factory;
    public boolean verbose;
    public final List<Copier> copiers=new ArrayList<>();
    public static final int maxSize=1_000_000;
    static final long newMillenium=978_307_200_000l;
}
