package p;
import java.io.*;
import java.net.*;
import java.util.*;
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
    } // need some way to end a log file when tablet stops
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
            return new File(logServerlogDirectory,name(n));
        }
        File file() {
            return file(sequenceNumber);
        }
        private String name(int n) {
            InetAddress inetAddress=socket.getInetAddress();
            byte[] bytes=inetAddress.getAddress();
            long t=System.currentTimeMillis()-newMillenium;
            t/=1000;
            String name=prefix!=null&&!prefix.equals("")?(prefix+"."):"";
            name+=""+t+".";
            name+=bytes[2]+"."+bytes[3]+".";
            bytes=serverSocket.getInetAddress().getAddress();
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
            super("copier "+socket.getRemoteSocketAddress());
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
        if(!logServerlogDirectory.exists()) {
            if(logServerlogDirectory.mkdir()) p("created: "+logServerlogDirectory);
            else p("can not create: "+logServerlogDirectory);
        }
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
        Set<LogServer> logServers=new LinkedHashSet<>();
        // how to figure out what nic(s) to run this on.
        // iterating over the nics is fine, but the tablets will need to know the ipaddress on startup
        // or the address needs to be entered into the properties file (maybe with ignore=false
        // and the app needs to be restarted.
        // or we could require a static ip address ... 
        // let's not do that (require a static ip address). 
        
        for(int i=0;i<=5;i++) {
        String router="192.168."+i+".1";
            Set<InterfaceAddress> interfaceAddresses=IO.findMyInterfaceAddressesOnRouter(router);
            if(interfaceAddresses.size()>0) {
                InetAddress inetAddress=interfaceAddresses.iterator().next().getAddress();
                LogServer logServer=new LogServer(inetAddress.getHostAddress(),defaultLogServerService,null,null);
                new Thread(logServer,"logServer").start();
                logServers.add(logServer);
            } else p("no interface addresses on: "+router);
        }
        if(logServers.size()==0) {
            p("no log servers were created!");
            p("check the interfaces to see if they are up.");
        }
    }
    public static final File logServerlogDirectory=new File("logServerlogDirectory");
    public static final int defaultLogServerService=5000;
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
