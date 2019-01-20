import org.codehaus.commons.compiler.CompileException;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Run a snippet of code using Janino.
 */
@SuppressWarnings("checkstyle:visibilitymodifier")
public class Snippet extends Source {

    /**
     * Fake filename of snippet.
     */
    public String filename = "Snippet.java";

    /**
     * Source code to compile.
     */
    public String source;

    /**
     * Default indentation level.
     */
    public static final transient int DEFAULT_INDENTATION_LEVEL = 4;

    /**
     * Amount to indent when templating code.
     */
    public int indentLevel = DEFAULT_INDENTATION_LEVEL;

    /**
     * Janino script evaluator.
     */
    private transient org.codehaus.janino.ScriptEvaluator janinoScriptEvaluator;

    /**
     * JDK script evaluator.
     */
    private transient org.codehaus.commons.compiler.jdk.ScriptEvaluator jdkScriptEvaluator;

    /**
     * Map of lines in template to lines in original source.
     */
    private transient Map<Integer, Integer> templateLineMapping;

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
     * Convenience method to pad left strings with whitespace.
     *
     * @param count amount of space to insert
     * @return a empty String of length count
     */
    private String padLeft(final int count) {
        StringBuilder padding = new StringBuilder();
        for (int unused = 0; unused < count; unused++) {
            padding.append(" ");
        }
        return padding.toString();
    }

    /**
     * Get a map of sources for this snippet.
     *
     * @return a map of sources for this snippet.
     */
    protected Map<String, String> sources() {
        Map<String, String> snippetSources = new HashMap<>();
        templateLineMapping = new HashMap<>();

        int templateLine = 0;
        int sourceLine = 0;
        StringBuilder templatedSourceBuilder = new StringBuilder();

        templatedSourceBuilder.append("public class Snippet {\n");
        templateLineMapping.put(templateLine, null);
        templateLine++;
        templatedSourceBuilder.append(padLeft(indentLevel)).append("public static void snippet() {\n");
        templateLineMapping.put(templateLine, null);
        templateLine++;

        for (String line : source.trim().split("\n")) {
            if (line.startsWith("import ")) {
                sourceLine++;
                continue;
            }
            if (line.startsWith("static ")) {
                line = line.replace("static ", "");
            }
            templatedSourceBuilder.append(padLeft(indentLevel * 2)).append(line.replaceAll("\\s+$", "")).append("\n");
            templateLineMapping.put(templateLine, sourceLine);
            templateLine++;
            sourceLine++;
        }

        templatedSourceBuilder.append(padLeft(indentLevel)).append("}\n");
        templateLineMapping.put(templateLine, null);
        templateLine++;
        templatedSourceBuilder.append("}\n\n");
        templateLineMapping.put(templateLine, null);
        templateLine++;

        String templatedSource = templatedSourceBuilder.toString();
        snippetSources.put(filename, templatedSource);
        return snippetSources;
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
                    break;
                } catch (CompileException ignored) { }
                compileWithJDK();
                break;
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
     */
    public Source run(final String setSource) {
        source = setSource;
        return super.run();
    }

    /**
     * Set the indentation level. Convenience method for testing.
     *
     * @param setIndentLevel amount to indent when formatting the code
     * @return this object for chaining
     */
    public Source setIndentLevel(final int setIndentLevel) {
        indentLevel = setIndentLevel;
        return this;
    }
}
