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

    /** ScriptEvaluator used to compile and run our Java snippet. */
    private ScriptEvaluator scriptEvaluator = new ScriptEvaluator();

    /**
     * Create a new Janini snippet execution object from a received JSON string.
     *
     * @param json JSON string to initialize the new Janini snippet object
     * @return new Janini snippet object initialized from the JSON string
     */
    public static JaniniSnippet received(final String json) {
        JaniniSnippet janiniSnippet = (JaniniSnippet) received(json, JaniniSnippet.class);
        janiniSnippet.compiler = "Janino";
        janiniSnippet.scriptEvaluator.setPermissions(janiniSnippet.permissions);
        return janiniSnippet;
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
}
