import org.codehaus.commons.compiler.CompileException
import org.testng.Assert
import org.testng.annotations.Test

/**
 * Test the WebServer class.
 */
class TestSnippet {
    /**
     * Test simple script.
     */
    @Test
    fun testMath() {
        val snippet = Snippet().run("""
int a = 3;
int b = 4;
System.out.print(a + b);
""")
        Assert.assertEquals(snippet.output, "7")
    }

    @Test
    fun testExit() {
        val snippet = Snippet().run("""
System.exit(-1);
""")
        Assert.assertFalse(snippet.executed)
        Assert.assertTrue(snippet.crashed)
    }

    @Test
    fun testNullDeference() {
        val snippet = Snippet().run("""
Object reference = null;
System.out.println(reference.toString());
""")
        Assert.assertFalse(snippet.executed)
        Assert.assertTrue(snippet.crashed)
    }

    @Test
    fun testTimeout() {
        val snippet = Snippet().run("""
int i = 0;
while (true) {
    i++;
}
""")
        Assert.assertFalse(snippet.executed)
        Assert.assertTrue(snippet.timedOut)
        Assert.assertTrue(snippet.timeoutLength / 1000.0 <= snippet.executionLength)
        Assert.assertTrue(snippet.executionLength <= (snippet.timeoutLength / 1000.0 * 1.2))
    }

    @Test
    fun testPrivateVisibility() {
        val snippet = Snippet().run("""
class Test {
  private int value;
}
Test t = new Test();
t.value = 5;
System.out.print(t.value);
""")
        Assert.assertEquals(snippet.output, "5")
    }

    @Test
    fun testGenericsAreBrokenUsingJanino() {
        val snippet = Snippet("Janino")
        try {
            snippet.run("""
import java.util.ArrayList;
ArrayList<String> list = new ArrayList<String>();
list.add("Geoffrey");
String broken = list.get(0);
System.out.println(broken);
        """)
        } catch (e: CompileException) { }

        Assert.assertFalse(snippet.compiled)
    }

    @Test
    fun testGenericsWorkUsingJDK() {
        val snippet = Snippet("JDK")
        try {
            snippet.run("""
import java.util.ArrayList;
ArrayList<String> list = new ArrayList<String>();
list.add("Geoffrey");
String working = list.get(0);
System.out.print(working);
        """)
        } catch (e: CompileException) { }

        Assert.assertEquals(snippet.output, "Geoffrey");
        Assert.assertEquals(snippet.compiler, "JDK")
    }

    @Test
    fun testGenericsTriggerTheJDK() {
        val snippet = Snippet()
        try {
            snippet.run("""
import java.util.ArrayList;
ArrayList<String> list = new ArrayList<String>();
list.add("Geoffrey");
String working = list.get(0);
System.out.print(working);
        """)
        } catch (e: CompileException) { }

        Assert.assertEquals(snippet.output, "Geoffrey");
        Assert.assertEquals(snippet.compiler, "JDK")
    }

    @Test
    fun testGenericsWorkWithCasts() {
        val snippet = Snippet().run("""
import java.util.ArrayList;
ArrayList<String> list = new ArrayList<String>();
list.add("Geoffrey");
String working = (String) list.get(0);
System.out.print(working);
        """)
        Assert.assertTrue(snippet.executed);
        Assert.assertEquals(snippet.output, "Geoffrey")
    }
}