import org.testng.annotations.Test;

import io.vertx.core.json.JsonObject;

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
        testObject.put("source", "int a = 3; int b = 4; System.out.println(a + b);");
        WebServer.run(testObject);
        System.out.println(testObject.encodePrettily());
    }

}
