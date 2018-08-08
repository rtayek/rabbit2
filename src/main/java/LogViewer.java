import java.io.*;
import javax.xml.parsers.*;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import static p.IO.*;
public class LogViewer {
    void run() throws ParserConfigurationException, SAXException, IOException {
        File file = new File(p.LogServer.logServerlogDirectory,"main.0.0.log");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.parse(file);
        p(document.toString());
    }
    public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
        new LogViewer().run();
    }
}
