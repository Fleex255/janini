import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.ScriptEvaluator;

import java.lang.reflect.InvocationTargetException;

/**
 * Run a snippet of code using Janino.
 */
@SuppressWarnings("checkstyle:visibilitymodifier")
public class JaninoSnippet extends Source {

    /** Source code to compile. */
    public String source;

    /** Set the compiler that is used. */
    public final String compiler = "Janino";

    /** ScriptEvaluator used to compile and run our Java snippet. */
    private transient ScriptEvaluator scriptEvaluator = new ScriptEvaluator();

    /**
     * Create a new JaninoSnippet and set default fields.
     */
    JaninoSnippet() {
        super();
        scriptEvaluator.setPermissions(permissions);
    }

    /**
     * Create a new JaninoSnippet execution object from a received JSON string.
     *
     * @param json JSON string to initialize the new JaninoSnippet object
     * @return new JaninoSnippet object initialized from the JSON string
     */
    public static JaninoSnippet received(final String json) {
        return (JaninoSnippet) received(json, JaninoSnippet.class);
    }

    /**
     * Compile a snippet of Java source code using Janino.
     *
     * Throws an exception if compilation fails.
     *
     * @throws CompileException if compilation fails
     */
    public void doCompile() throws CompileException {
        scriptEvaluator.cook(source);
    }

    /**
     * Execute our snippet of Java source code.
     *
     * Throws an exception of execution fails.
     *
     * @throws InvocationTargetException if execution fails
     */
    public void doExecute() throws InvocationTargetException {
        scriptEvaluator.evaluate(new Object[0]);
    }

    /**
     * Convenience method for testing.
     *
     * @param setSource set the source of the JaninoSnippet object
     * @return this object for chaining
     * @throws Exception if compilation fails
     */
    public Source run(final String setSource) throws Exception {
        source = setSource;
        return super.run();
    }
}
