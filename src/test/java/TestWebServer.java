import org.testng.annotations.Test;
import com.eclipsesource.json.JsonObject;

/**
 * Test the WebServer class.
 */
public class TestWebServer {

    /**
     * Test compile.
     */
    @Test
    public void testCompile() {
        JsonObject testObject = new JsonObject();
        testObject.add("source", "int a = 3; int b = 4; System.out.println(a + b);");
        WebServer.run(testObject);
        System.out.println(testObject.toString());
    }

}
