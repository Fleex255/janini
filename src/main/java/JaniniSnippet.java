import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.ScriptEvaluator;

import java.lang.reflect.InvocationTargetException;

/**
 * Run a snippet of code using Janini.
 */
@SuppressWarnings("checkstyle:visibilitymodifier")
public class JaniniSnippet extends Source {

    /** Source code to compile. */
    public String source;

    /** Set the compiler that is used. */
    public String compiler = "Janino";

    /** ScriptEvaluator used to compile and run our Java snippet. */
    private transient ScriptEvaluator scriptEvaluator = new ScriptEvaluator();

    /**
     * Create a new JaniniSnippet and set default fields.
     */
    JaniniSnippet() {
        super();
        scriptEvaluator.setPermissions(permissions);
    }

    /**
     * Create a new JaniniSnippet execution object from a received JSON string.
     *
     * @param json JSON string to initialize the new JaniniSnippet object
     * @return new JaniniSnippet object initialized from the JSON string
     */
    public static JaniniSnippet received(final String json) {
        return (JaniniSnippet) received(json, JaniniSnippet.class);
    }

    /**
     * Compile a snippet of Java source code using Janini.
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
     * @param setSource set the source of the JaniniSnippet object
     * @return this object for chaining
     * @throws Exception if compilation fails
     */
    public Source run(final String setSource) throws Exception {
        source = setSource;
        return super.run();
    }
}
