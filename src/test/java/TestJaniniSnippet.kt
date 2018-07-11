import org.testng.Assert
import org.testng.annotations.Test

/**
 * Test the WebServer class.
 */
class TestJaniniSnippet {
    /**
     * Test simple script.
     */
    @Test
    fun testMath() {
        val snippet = JaniniSnippet().compile("""
int a = 3;
int b = 4;
System.out.print(a + b);
""").execute()
        Assert.assertEquals(snippet.output, "7")
    }

    @Test
    fun testExit() {
        val snippet = JaniniSnippet().compile("""
System.exit(-1);
""").execute()
        Assert.assertFalse(snippet.executed)
        Assert.assertTrue(snippet.crashed)
    }

    @Test
    fun testNullDeference() {
        val snippet = JaniniSnippet().compile("""
Object reference = null;
System.out.println(reference.toString());
""").execute()
        Assert.assertFalse(snippet.executed)
        Assert.assertTrue(snippet.crashed)
    }

    @Test
    fun testTimeout() {
        val snippet = JaniniSnippet().compile("""
int i = 0;
while (true) {
    i++;
}
""").execute();
        Assert.assertFalse(snippet.executed)
        Assert.assertTrue(snippet.timedOut)
        Assert.assertTrue(snippet.timeoutLength / 1000.0 <= snippet.executionLength)
        Assert.assertTrue(snippet.executionLength <= (snippet.timeoutLength / 1000.0 * 1.1))
    }
}
