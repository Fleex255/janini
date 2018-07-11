import org.codehaus.commons.compiler.CompileException
import org.testng.Assert
import org.testng.annotations.Test

/**
 * Test the WebServer class.
 */
class TestJaniniClasses {
    /**
     * Test simple class.
     */
    @Test
    fun testMath() {
        val snippet = JaniniClasses().run("""
public class Question {
    public static void main(String[] unused) {
        int a = 3;
        int b = 4;
        System.out.print(a + b);
    }
}
""")
        Assert.assertEquals(snippet.output, "7")
    }

    /**
     * Test exit class.
     */
    @Test
    fun testExit() {
        val snippet = JaniniClasses().run("""
public class Question {
    public static void main(String[] unused) {
        System.exit(-1);
    }
}
""")
        Assert.assertFalse(snippet.executed);
    }

    /**
     * Test wrong class name.
     */
    @Test
    fun testWrongClassName() {
        val snippet = JaniniClasses()
        try {
            snippet.run("""
public class Blah {
    public static void main(String[] unused) {
        System.out.println("Broken");
    }
}
""")
        } catch (e : ClassNotFoundException) { }
        Assert.assertFalse(snippet.compiled);
    }

    /**
     * Test wrong method name.
     */
    @Test
    fun testWrongMethodName() {
        val snippet = JaniniClasses()
        try {
            snippet.run("""
public class Question {
    public static void broken(String[] unused) {
        System.out.println("Broken");
    }
}
""")
        } catch (e : NoSuchMethodException) { }
        Assert.assertFalse(snippet.compiled);
    }

    /**
     * Test wrong method signature.
     */
    @Test
    fun testWrongMethodSignature() {
        val snippet = JaniniClasses()
        try {
            snippet.run("""
public class Question {
    public static void main() {
        System.out.println("Broken");
    }
}
""")
        } catch (e : NoSuchMethodException) { }
        Assert.assertFalse(snippet.compiled);
    }
}
