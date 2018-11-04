import org.codehaus.commons.compiler.CompileException;
import java.lang.reflect.InvocationTargetException;

/**
 * Run a snippet of code using Janino.
 */
@SuppressWarnings("checkstyle:visibilitymodifier")
public class Snippet extends Source {

    /**
     * Source code to compile.
     */
    public String source;

    /**
     * Janino script evaluator.
     */
    private transient org.codehaus.janino.ScriptEvaluator janinoScriptEvaluator;

    /**
     * JDK script evaluator.
     */
    private transient org.codehaus.commons.compiler.jdk.ScriptEvaluator jdkScriptEvaluator;

    /**
     * Create a new Snippet and set default fields.
     */
    Snippet() {
        super();
    }

    /**
     * Create a new Snippet with a given compiler.
     *
     * @param setCompiler the compiler to use
     */
    Snippet(final String setCompiler) {
        super();
        compiler = setCompiler;
    }

    /**
     * Create a new Snippet execution object from a received JSON string.
     *
     * @param json JSON string to initialize the new Snippet object
     * @return new Snippet object initialized from the JSON string
     */
    public static Snippet received(final String json) {
        return (Snippet) received(json, Snippet.class);
    }

    /**
     * Try compiling with Janino.
     *
     * @throws CompileException thrown if compilation fails
     */
    private void compileWithJanino() throws CompileException {
        janinoScriptEvaluator = new org.codehaus.janino.ScriptEvaluator();
        janinoScriptEvaluator.setPermissions(permissions);
        janinoScriptEvaluator.cook(source);
        compiler = "Janino";
    }

    /**
     * Try compiling with the JDK.
     *
     * @throws CompileException thrown if compilation fails
     */
    private void compileWithJDK() throws CompileException {
        jdkScriptEvaluator = new org.codehaus.commons.compiler.jdk.ScriptEvaluator();
        jdkScriptEvaluator.setPermissions(permissions);
        jdkScriptEvaluator.cook(source);
        compiler = "JDK";
    }

    /**
     * Compile a snippet of Java source code using Janino.
     * <p>
     * Throws an exception if compilation fails.
     *
     * @throws CompileException if compilation fails
     */
    public void doCompile() throws CompileException {
        switch (compiler) {
            case "Janino":
                compileWithJanino();
                break;
            case "JDK":
                compileWithJDK();
                break;
            default:
                try {
                    compileWithJanino();
                } catch (CompileException unused) {
                    compileWithJDK();
                }
        }
    }

    /**
     * Execute our snippet of Java source code.
     * <p>
     * Throws an exception of execution fails.
     *
     * @throws InvocationTargetException if execution fails
     */
    public void doExecute() throws InvocationTargetException {
        if (compiler.equals("Janino")) {
            janinoScriptEvaluator.evaluate(new Object[0]);
        } else if (compiler.equals("JDK")) {
            jdkScriptEvaluator.evaluate(new Object[0]);
        }
    }

    /**
     * Convenience method for testing.
     *
     * @param setSource set the source of the Snippet object
     * @return this object for chaining
     * @throws Exception if compilation fails
     */
    public Source run(final String setSource) throws Exception {
        source = setSource;
        return super.run();
    }
}
