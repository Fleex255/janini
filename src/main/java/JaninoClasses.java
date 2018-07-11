import org.codehaus.commons.compiler.CompileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

/**
 * Run a class method using Janino.
 */
@SuppressWarnings("checkstyle:visibilitymodifier")
public class JaninoClasses extends Source {

    /** Sources to compile. */
    public String source;

    /** Default class name to load. */
    public String className = "Question";

    /** Default method name to run. */
    public String methodName = "main";

    /** Method to run. */
    private transient Method method;

    /**
     * Create a new JaninoClasses and set default fields.
     */
    JaninoClasses() {
        super();
    }

    /**
     * Create a new JaninoClasses with a given compiler.
     *
     * @param setCompiler the compiler to use
     */
    JaninoClasses(final String setCompiler) {
        super();
        compiler = setCompiler;
    }

    /**
     * Create a new JaninoClasses execution object from a received JSON string.
     *
     * @param json JSON string to initialize the new JaninoClasses object
     * @return new JaninoClasses object initialized from the JSON string
     */
    public static JaninoClasses received(final String json) {
        return (JaninoClasses) received(json, JaninoClasses.class);
    }

    /**
     * Try compiling with Janino.
     *
     * @return ClassLoader if compilation was successful
     * @throws CompileException thrown if compilation fails
     * @throws IOException thrown if there was a problem converting the string to bytes
     */
    private ClassLoader compileWithJanino() throws CompileException, IOException {
        org.codehaus.janino.SimpleCompiler simpleCompiler =
                new org.codehaus.janino.SimpleCompiler();
        simpleCompiler.setPermissions(permissions);
        simpleCompiler.cook(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
        return simpleCompiler.getClassLoader();
    }

    /**
     * Try compiling with the JDK compiler.
     *
     * @return ClassLoader if compilation was successful
     * @throws CompileException thrown if compilation fails
     * @throws IOException thrown if there was a problem converting the string to bytes
     */
    private ClassLoader compileWithJDK() throws CompileException, IOException {
        org.codehaus.commons.compiler.jdk.SimpleCompiler simpleCompiler =
                new org.codehaus.commons.compiler.jdk.SimpleCompiler();
        simpleCompiler.setPermissions(permissions);
        simpleCompiler.cook(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
        return simpleCompiler.getClassLoader();
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
        ClassLoader classLoader;
        switch (compiler) {
            case "Janino":
                classLoader = compileWithJanino();
                break;
            case "JDK":
                classLoader = compileWithJDK();
                break;
            default:
                try {
                    compiler = "Janino";
                    classLoader = compileWithJanino();
                } catch (Exception janinoException) {
                    compiler = "JDK";
                    classLoader = compileWithJDK();
                }
                break;
        }
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
     * @param setSource set the source of the JaninoClasses object
     * @return this object for chaining
     * @throws Exception if compilation fails
     */
    public Source run(final String setSource) throws Exception {
        source = setSource;
        return super.run();
    }
}
