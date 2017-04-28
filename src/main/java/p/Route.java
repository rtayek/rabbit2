package p;
//http://www.techsupportforum.com/forums/f31/solved-dual-network-issues-681839.html
// in a dos box running as administrator:
//route -f
//route delete 0.0.0.0
//route -p add 0.0.0.0 mask 0.0.0.0 192.168.1.1 metric 10
// when we plug in a nic do: route delete 0.0.0.0 mask 0.0.0.0 192.168.2.1
import static p.IO.*;
import java.io.*;
import java.util.*;
public class Route {
    static class R {
        R(String desination,String mask,String gateway,String interface_,String metric) {
            this.destination=desination;
            this.mask=mask;
            this.gateway=gateway;
            this.interface_=interface_;
            this.metric=metric;
        }
        final String destination,mask,gateway,interface_,metric;
        @Override public String toString() {
            return "route: "+destination+" "+mask+" "+gateway+" "+interface_+" "+metric;
        }
    }
    void delete(R r) {
        p("deleting default gateway to: "+r);
        String[] strings=new String[] {"route","delete",zeroes,"mask",zeroes,r.gateway};
        p("command: "+Arrays.asList(strings));
        int rc=Exec.exec(strings);
        p("rc: "+rc);
    }
    List<R> relevant() {
        List<R> relevant=new ArrayList<>();
        for(R r:rs)
            if(r.destination.startsWith(prefix)||r.gateway.startsWith(prefix)) if(r.interface_!=null) {
                relevant.add(r);
            } else {
                relevant.add(r);
            }
        return relevant;
    }
    private void print() {
        p(interfaces.size()+" interfaces:");
        p(routes.size()+" routes:");
        for(R r:rs)
            if(r.interface_!=null) {
                if(r.destination.startsWith(prefix)||r.gateway.startsWith(prefix)) {
                    if(r.destination.equals(zeroes)&&r.mask.equals(zeroes)) if(r.gateway.equals(myRouter)) p("default gateway to my router: "+r);
                    else p("default gateway: "+r);
                    else p(r.toString());
                }
            } else {
                if(r.destination.startsWith(prefix)||r.gateway.startsWith(prefix)) {
                    if(r.gateway.equals(myRouter)) p("persistent route to my router: "+r);
                    else p("persistent : "+r);
                }
            }
    }
    void delete() {
        for(R r:rs)
            if(r.interface_!=null) if(r.destination.startsWith(prefix)||r.gateway.startsWith(prefix)) if(r.destination.equals(zeroes)&&r.mask.equals(zeroes)) if(!r.gateway.equals(myRouter)) delete(r);
    }
    void run() {
        Exec exec=new Exec(new String[] {"route","print"});
        exec.run();
        if(exec.rc==0) {
            StringReader stringReader=new StringReader(exec.output);
            BufferedReader bufferedReader=new BufferedReader(stringReader);
            String line=null;
            try {
                while((line=bufferedReader.readLine())!=null) {
                    if(line.startsWith(delimiter)) {
                        seenDelimiter=true;
                        seenInterfaceList=false;
                        seenNetworkDestination=false;
                    }
                    if(seenInterfaceList&&!seenDelimiter) {
                        interfaces.add(line);
                    }
                    if(seenNetworkDestination&&!seenDelimiter) {
                        routes.add(line);
                    } else if(seenNetworkAddress&&!seenDelimiter) {
                        routes.add(line);
                    }
                    if(line.startsWith(interfaceList)) {
                        seenInterfaceList=true;
                        seenDelimiter=false;
                    } else if(line.startsWith(networkDestination)) {
                        seenNetworkDestination=true;
                        seenDelimiter=false;
                    } else if(line.startsWith(networkAddress)) {
                        seenNetworkAddress=true;
                        seenDelimiter=false;
                    }
                }
            } catch(IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            for(String string:routes) {
                String[] x=string.split(" +");
                if(x.length==6) rs.add(new R(x[1],x[2],x[3],x[4],x[5]));
                else if(x.length==5) rs.add(new R(x[1],x[2],x[3],null,x[4]));
            }
        }
        // add in default route for my wired lan connection
        //p(exec.output);
    }
    public static void main(String[] args) {
        Route route=new Route();
        route.run();
        route.print();
        route.delete();
        List<R> relevant=route.relevant();
        for(R r:relevant)
            p(r.toString());
    }
    boolean seenInterfaceList,seenDelimiter,seenNetworkDestination,seenNetworkAddress;
    final String prefix="192.168";
    final String myRouter="192.168.1.1";
    Set<String> interfaces=new LinkedHashSet<>();
    Set<String> routes=new LinkedHashSet<>();
    Set<R> rs=new LinkedHashSet<R>();
    static final String interfaceList="Interface List";
    static final String networkDestination="Network Destination";
    static final String networkAddress="  Network Address";
    static final String delimiter="===================";
    static final String zeroes="0.0.0.0";
}
