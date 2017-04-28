package p;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import static java.lang.Math.*;
import java.util.*;
import static p.IO.p;
public class Exec {
    public Exec(String command) {
        processBuilder=new ProcessBuilder(command);
    }
    public Exec(String[] strings) {
        List<String> command=Arrays.asList(strings);
        processBuilder=new ProcessBuilder(command);
    }
    public static String output(InputStream inputStream) throws IOException {
        StringBuilder sb=new StringBuilder();
        BufferedReader br=null;
        try {
            br=new BufferedReader(new InputStreamReader(inputStream));
            String line=null;
            while((line=br.readLine())!=null) {
                sb.append(line+System.getProperty("line.separator"));
            }
        } finally {
            br.close();
        }
        return sb.toString();
    }
    public Exec run() {
        Process process;
        try {
            process=processBuilder.start();
            rc=process.waitFor();
            output=output(process.getInputStream());
            error=output(process.getErrorStream());
        } catch(IOException e) {
            p("caught: "+e);
            e.printStackTrace();
        } catch(InterruptedException e) {
            p("caught: "+e);
            e.printStackTrace();
        } catch(Exception e) {
            p("caught: "+e);
            e.printStackTrace();
        }
        return this;
    }
    public void print() {
        print(rc,output,error);
    }
    public static void print(int rc,String output,String error) {
        p("return code: "+rc);
        p("output: '"+output+"'");
        p("err: '"+error+"'");
    }
    public static int exec(String[] strings) {
        Exec exec=new Exec(strings);
        exec.run();
        //exec.print();
        return exec.rc;
    }
    public static int exec(String command) {
        Runtime runtime=Runtime.getRuntime();
        try {
            Process proc=runtime.exec(command);
            proc.waitFor();
            return proc.exitValue();
        } catch(IOException e) {
            e.printStackTrace();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
        return -1;
    }
    public static int ping(String host) {
        return exec(new String[] {"ping",host});
    }
    public static boolean isAndroid() {
        return System.getProperty("http.agent")!=null; // fragile! 
    }
    public static boolean canWePing(String host,int timeout) {
        String timeoutString="";
        if(isAndroid()) {
            timeoutString+=max(1,timeout/1_000);
            return exec(new String[] {"ping","-c","1","-W",timeoutString,host})==0;
        } else {
            timeoutString+=timeout;
            Exec exec=new Exec(new String[] {"ping","-n","1","-w",""+timeoutString,host});
            exec.run();
            //exec.print();
            boolean ok=false;
            //if(exec.output.contains("Reply from "+host+":")) // fragile! yes, very!
            if(exec.output.contains("Lost = 0")) // fragile! yes, very!
                // fix this!
                ok=true;
            else p("not ok,output: "+exec.output);
            p("returning: "+ok);
            return ok;
        }
    }
    public static void main(String[] args) throws InterruptedException,IOException {
        p("------");
        Exec exec=new Exec(new String[] {"ping","localhost"});
        exec.run();
        exec.print();
        p("------");
        Exec.exec("ping localhost");
        p("------");
        Exec.ping("localhost");
        p("------");
        new Exec(new String[] {"ping","localhost"}).run().print();
    }
    final ProcessBuilder processBuilder;
    int rc;
    String output="",error="";
}
