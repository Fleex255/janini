import org.codehaus.commons.compiler.CompileException
import org.testng.Assert
import org.testng.annotations.Test

/**
 * Test the WebServer class.
 */
class TestJaninoClasses {
    /**
     * Test simple class.
     */
    @Test
    fun testMath() {
        val classes = JaninoClasses().run("""
public class Question {
    public static void main(final String[] unused) {
        int a = 3;
        int b = 4;
        System.out.print(a + b);
    }
}
""")
        Assert.assertEquals(classes.output, "7")
        Assert.assertEquals(classes.compiler, "Janino")
    }

    /**
     * Test exit class.
     */
    @Test
    fun testExit() {
        val classes = JaninoClasses().run("""
public class Question {
    public static void main(final String[] unused) {
        System.exit(-1);
    }
}
""")
        Assert.assertTrue(classes.compiled)
        Assert.assertEquals(classes.compiler, "Janino")
        Assert.assertFalse(classes.executed)
    }

    /**
     * Test wrong class name.
     */
    @Test
    fun testWrongClassName() {
        val classes = JaninoClasses()
        try {
            classes.run("""
public class Blah {
    public static void main(final String[] unused) {
        System.out.print("Broken");
    }
}
""")
        } catch (e : ClassNotFoundException) { }

        Assert.assertFalse(classes.compiled)
    }

    /**
     * Test wrong method name.
     */
    @Test
    fun testWrongMethodName() {
        val classes = JaninoClasses()
        try {
            classes.run("""
public class Question {
    public static void broken(final String[] unused) {
        System.out.print("Broken");
    }
}
""")
        } catch (e : NoSuchMethodException) { }

        Assert.assertFalse(classes.compiled)
    }

    /**
     * Test wrong method signature.
     */
    @Test
    fun testWrongMethodSignature() {
        val classes = JaninoClasses()
        try {
            classes.run("""
public class Question {
    public static void main() {
        System.out.print("Broken");
    }
}
""")
        } catch (e : NoSuchMethodException) { }

        Assert.assertFalse(classes.compiled)
    }

    /**
     * Test multiple classes in same source.
     */
    @Test
    fun testMultipleClassesInSingleSource() {
        val classes = JaninoClasses().run("""
public class Other {
    public String toString() {
        return "Working";
    }
}
public class Question {
    public static void main(final String[] unused) {
        Other other = new Other();
        System.out.print(other.toString());
    }
}
""")

        Assert.assertEquals(classes.output, "Working")
    }

    @Test
    fun testGenericsAreBrokenUsingJanino() {
        val classes = JaninoClasses("Janino")
        try {
            classes.run("""
import java.util.ArrayList;

public class Question {
    public static void main(final String[] unused) {
        ArrayList<String> list = new ArrayList<String>();
        list.add("Geoffrey");
        String broken = list.get(0);
        System.out.print(broken);
    }
}
""")
        } catch (e: CompileException) { }

        Assert.assertFalse(classes.compiled)
    }

    @Test
    fun testImports() {
        val classes = JaninoClasses().run("""
import java.util.ArrayList;

public class Question {
    public static void main(final String[] unused) {
        System.out.print("Worked");
    }
}
""")
        Assert.assertEquals(classes.output, "Worked")
        Assert.assertEquals(classes.compiler, "Janino")
    }

    @Test
    fun testGenericsWorkUsingJDK() {
        val classes = JaninoClasses("JDK").run("""
import java.util.ArrayList;

public class Question {
    public static void main(final String[] unused) {
        ArrayList<String> list = new ArrayList<String>();
        list.add("Geoffrey");
        String working = list.get(0);
        System.out.print(working);
    }
}
""")

        Assert.assertEquals(classes.output, "Geoffrey")
        Assert.assertEquals(classes.compiler, "JDK")
    }

    @Test
    fun testGenericsTriggerTheJDK() {
        val classes = JaninoClasses().run("""
import java.util.ArrayList;

public class Question {
    public static void main(final String[] unused) {
        ArrayList<String> list = new ArrayList<String>();
        list.add("Geoffrey");
        String working = list.get(0);
        System.out.print(working);
    }
}
""")

        Assert.assertEquals(classes.output, "Geoffrey")
        Assert.assertEquals(classes.compiler, "JDK")
    }
}
