package p;
import java.io.*;
import java.net.*;
public class StandaloneTcp {
    public interface Consumer<T> { // instead of 1.8 definition
        void accept(T t);
    }
    class Server extends Thread {
        Server(ServerSocket serverSocket,Consumer<Socket> consumer) {
            super("Server");
            this.serverSocket=serverSocket;
            this.consumer=consumer;
        }
        @Override public void run() {
            p("server running on: "+serverSocket);
            while(true)
                try {
                    Socket socket=serverSocket.accept();
                    if(consumer!=null) consumer.accept(socket);
                } catch(IOException e) {
                    p(getName()+" caught: "+e);
                    break;
                }
        }
        final ServerSocket serverSocket;
        final Consumer<Socket> consumer;
    }
    class Client {}
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
    StandaloneTcp(String host,Integer service) throws IOException {
        ServerSocket serverSocket=new ServerSocket();
        SocketAddress socketAddress=new InetSocketAddress(host,service);
        serverSocket.bind(socketAddress);
        Consumer<Socket> socketConsumer=new Consumer<Socket>() {
            @Override public void accept(Socket socket) {
                p("accepted from: "+socket);
                InputStream inputStream=null;
                try {
                    inputStream=socket.getInputStream();
                    BufferedReader in=new BufferedReader(new InputStreamReader(inputStream));
                    String string=in.readLine();
                    p(string+" from: "+socket);
                    socket.close();
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        };
        new Server(serverSocket,socketConsumer).start();
    }
    static boolean send(String host,Integer service) {
        Socket socket;
        try {
            socket=new Socket(host,service);
            OutputStreamWriter out=new OutputStreamWriter(socket.getOutputStream());
            out.write("hello\n");
            out.flush();
            socket.close();
            return true;
        } catch(IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    public static void main(String[] args) {
        final String host="localhost";
        final Integer service=1237;
        try {
            StandaloneTcp tcp=new StandaloneTcp(host,service);
        } catch(Exception e) {
            p("main caught: "+e);
        }
        for(int i=0;i<3;i++) {
            boolean ok=send(host,service);
            if(!ok)
                p("send failed");
        }
    }
}
