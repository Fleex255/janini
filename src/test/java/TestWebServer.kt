import org.testng.Assert
import org.testng.annotations.Test
import com.eclipsesource.json.JsonObject

/**
 * Test the WebServer class.
 */
class TestWebServer {

    private fun isOK(testObject : JsonObject) {
        Assert.assertTrue(testObject.get("completed").asBoolean())
    }

    /**
     * Test crash script.
     */
    @Test
    fun testCrashScript() {
        val testObject = JsonObject()
        testObject.add("source", "System.exit(-1);")
        WebServer.run(testObject.toString())
        // If this fails it shuts down the test runner
    }

    /**
     * Test simple class.
     */
    @Test
    fun testSimpleClass() {
        val testObject = JsonObject()
        testObject.add("as", "compiler")
        testObject.add("class", "Question")
        val source = """
public class Question {
    public static void main(String[] unused) {
        int a = 3;
        int b = 4;
        System.out.println(a + b);
    }
}""".trim()
        testObject.add("source", source)
        WebServer.run(testObject.toString())
        Assert.assertEquals(Integer.parseInt(testObject.get("output").asString().trim()), 7)
    }

    /**
     * Test crash class.
     */
    @Test
    fun testCrashClass() {
        val testObject = JsonObject()
        testObject.add("as", "compiler")
        testObject.add("class", "Question")
        val source = """
public class Question {
    public static void main(String[] unused) {
        System.exit(-1);
    }
}""".trim()
        testObject.add("source", source)
        WebServer.run(testObject.toString())
        // If this fails it shuts down the test runner
    }

    /**
     * Test timeout class.
     */
    @Test
    fun testTimeoutClass() {
        val testObject = JsonObject()
        testObject.add("as", "compiler")
        testObject.add("class", "Question")
        val source = """
public class Question {
    public static void main(String[] unused) {
        while (true) { }
    }
}
""".trim()
        testObject.add("source", source)
        WebServer.run(testObject.toString())
        Assert.assertTrue(testObject.get("timeout").asBoolean())
    }

    /**
     * Test parallel execution.
     */
    @Test
    fun testParallelExecution() {

        listOf(0, 1, 2, 3, 4, 6, 7, 8).parallelStream()
                .forEach{ i ->
                    val source = """
public class Question {
    public static void main(String[] unused) {
        for (int i = 0; i < 1024; i++) {
            System.out.print("$i");
            for (int j = 0; j < 10000; j++) { }
        }
    }
}"""
                    val testObject = JsonObject()
                    testObject.add("as", "compiler")
                    testObject.add("class", "Question")
                    testObject.add("source", source)
                    WebServer.run(testObject.toString())
                    isOK(testObject)
                    Assert.assertEquals(testObject.get("output").asString().length, 1024)
                }

    }
}
