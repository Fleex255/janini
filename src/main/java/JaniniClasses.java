import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.SimpleCompiler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

/**
 * Run a class method using Janini.
 */
@SuppressWarnings("checkstyle:visibilitymodifier")
public class JaniniClasses extends Source {
    /** Source code to compile. */
    public String source;

    /** Default class name to load. */
    public String className = "Question";

    /** Default method name to run. */
    public String methodName = "main";

    /** Set the compiler that is used. */
    public String compiler = "Janino";

    /** Compiler used to compile our sources. */
    private transient SimpleCompiler simpleCompiler = new SimpleCompiler();

    /** Method to run. */
    private transient Method method;

    /**
     * Create a new JaniniClasses and set default fields.
     */
    JaniniClasses() {
        super();
        simpleCompiler.setPermissions(permissions);
    }

    /**
     * Create a new JaniniClasses execution object from a received JSON string.
     *
     * @param json JSON string to initialize the new JaniniClasses object
     * @return new JaniniClasses object initialized from the JSON string
     */
    public static JaniniClasses received(final String json) {
        return (JaniniClasses) received(json, JaniniClasses.class);
    }

    /**
     * Compile Java classes using Janino.
     *
     * Throws an exception if compilation fails.
     *
     * @throws CompileException if compilation fails
     * @throws IOException if there is a problem creating the input stream
     * @throws ClassNotFoundException if the class specified is not found
     * @throws NoSuchMethodException if the method specified is not found or is not static
     */
    public void doCompile() throws CompileException, IOException, ClassNotFoundException, NoSuchMethodException {
        InputStream stream = new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
        simpleCompiler.cook(stream);
        ClassLoader classLoader = simpleCompiler.getClassLoader();
        Class<?> klass = classLoader.loadClass(className);
        method = klass.getMethod(methodName, String[].class);
        if (!(Modifier.isStatic(method.getModifiers()))) {
            throw new NoSuchMethodException(methodName + " must be static");
        }
    }

    /**
     * Execute our Java classes code.
     *
     * Throws an exception if execution fails.
     *
     * @throws InvocationTargetException if the target cannot be invoked
     * @throws IllegalAccessException if the code attempts to violate the sandbox
     */
    public void doExecute() throws InvocationTargetException, IllegalAccessException {
        method.invoke(null, (Object) new String[] {});
    }

    /**
     * Convenience method for testing.
     *
     * @param setSource set the source of the JaniniClasses object
     * @return this object for chaining
     * @throws Exception if compilation fails
     */
    public Source run(final String setSource) throws Exception {
        source = setSource;
        return super.run();
    }
}
