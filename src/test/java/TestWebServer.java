import com.eclipsesource.json.WriterConfig;
import org.testng.Assert;
import org.testng.annotations.Test;
import com.eclipsesource.json.JsonObject;

/**
 * Test the WebServer class.
 */
public class TestWebServer {

    /**
     * Test simple script.
     */
    @Test
    public void testSimpleScript() {
        JsonObject testObject = new JsonObject();
        testObject.add("source", "int a = 3; int b = 4; System.out.println(a + b);");
        WebServer.run(testObject);
        Assert.assertEquals(Integer.parseInt(testObject.get("output").asString().trim()), 7);
    }

    /**
     * Test crash script.
     */
    @Test
    public void testCrashScript() {
        JsonObject testObject = new JsonObject();
        testObject.add("source", "System.exit(-1);");
        WebServer.run(testObject);
        // If this fails it shuts down the test runner
    }

    /**
     * Test simple class.
     */
    @Test
    public void testSimpleClass() {
        JsonObject testObject = new JsonObject();
        testObject.add("as", "compiler");
        testObject.add("class", "Question");
        testObject.add("source", "public class Question {\npublic static void main(String[] unused) {\nint a = 3; int b = 4; System.out.println(a + b);}}");
        WebServer.run(testObject);
        Assert.assertEquals(Integer.parseInt(testObject.get("output").asString().trim()), 7);
    }

    /**
     * Test crash class.
     */
    @Test
    public void testCrashClass() {
        JsonObject testObject = new JsonObject();
        testObject.add("as", "compiler");
        testObject.add("class", "Question");
        testObject.add("source", "public class Question {\npublic static void main(String[] unused) {\nSystem.exit(-1);}}");
        WebServer.run(testObject);
        // If this fails it shuts down the test runner
    }
}
