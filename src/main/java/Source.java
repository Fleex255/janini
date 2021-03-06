import com.google.gson.Gson;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PackageObjectFactory;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.ThreadModeSettings;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.api.LocalizedMessage;
import org.apache.commons.cli.CommandLine;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ReflectPermission;
import java.security.Permissions;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Base class for all execution strategies.
 */
@SuppressWarnings({"checkstyle:visibilitymodifier", "checkstyle:constantname"})
public abstract class Source implements Callable<Void> {
    /**
     * Whether to run checkstyle.
     */
    public boolean runCheckstyle = true;

    /**
     * Whether to require checkstyle pass before compiling.
     */
    public boolean requireCheckstyle = true;

    /**
     * Compiler used.
     */
    protected String compiler = "";

    /**
     * How the source was run.
     */
    public String as = "";

    /**
     * Time that the source upload was received.
     */
    protected OffsetDateTime received;

    /**
     * Time that the result was returned to the client.
     */
    protected OffsetDateTime returned;

    /**
     * Time checkstyle started.
     */
    protected OffsetDateTime checkstyleStarted;

    /**
     * Time checkstyle finished.
     */
    protected OffsetDateTime checkstyleFinished;

    /**
     * Time checkstyle took in seconds.
     */
    protected double checkstyleLength;

    /**
     * Whether checkstyle succeeded.
     */
    protected boolean checkstyleSucceeded = false;

    /**
     * Time compilation started.
     */
    protected OffsetDateTime compileStarted;

    /**
     * Time compilation finished.
     */
    protected OffsetDateTime compileFinished;

    /**
     * Time compilation took in seconds.
     */
    protected double compileLength;

    /**
     * Whether compilation succeeded.
     */
    protected boolean compiled = false;

    /**
     * Error message generated by compilation if it failed.
     */
    protected String compilationErrorMessage;

    /**
     * Stack trace generated by compilation if it failed.
     */
    protected String compilationErrorStackTrace;

    /**
     * Time execution started.
     */
    protected OffsetDateTime executionStarted;

    /**
     * Time execution finished.
     */
    protected OffsetDateTime executionFinished;

    /**
     * Time execution took in seconds.
     */
    protected double executionLength;

    /**
     * Whether execution succeeded.
     */
    protected boolean executed = false;

    /**
     * Whether execution crashed.
     */
    protected boolean crashed = false;

    /**
     * Whether execution timed out.
     */
    protected boolean timedOut = false;

    /**
     * Error message generated by compilation if it failed.
     */
    protected String executionErrorMessage;

    /**
     * Stack trace generated by compilation if it failed.
     */
    protected String executionErrorStackTrace;

    /**
     * Default execution timeout in milliseconds.
     */
    public static final int DEFAULT_TIMEOUT = 100;

    /**
     * Execution timeout in milliseconds. Default is 100.
     */
    protected int timeoutLength = DEFAULT_TIMEOUT;

    /**
     * Output from execution if everything succeeded.
     */
    protected String output;

    /**
     * Current tool version.
     */
    protected static final String version = "1.0.0";

    /**
     * Default indentation level.
     */
    public static final transient int DEFAULT_INDENTATION_LEVEL = 4;

    /**
     * Amount to indent for checkstyle or when templating code.
     */
    public int indentLevel = DEFAULT_INDENTATION_LEVEL;

    /**
     * Gson object for serialization and deserialization.
     */
    private static transient Gson gson = new Gson();

    /**
     * Default permissions for code execution.
     */
    protected transient Permissions permissions = new Permissions();

    /**
     * Lock to serialize System.out and System.err.
     */
    private static final transient ReentrantLock OUTPUT_LOCK = new ReentrantLock();

    /**
     * Default checkstyle configuration.
     */
    private static transient Configuration defaultCheckstyleConfiguration = null;

    /**
     * checkstyle root module.
     */
    private transient StringChecker checker = null;

    /**
     * Initialize based on command line options.
     *
     * @param settings options passed on the command line
     * @throws CheckstyleException thrown if the checkstyle configuration is invalid
     */
    public static void initialize(final CommandLine settings) throws CheckstyleException {
        String checkstyleConfigurationPath = "./defaults/checkstyle.xml";
        if (settings != null && settings.hasOption("c")) {
            checkstyleConfigurationPath = settings.getOptionValue("c");
        }

        defaultCheckstyleConfiguration = ConfigurationLoader.loadConfiguration(
                checkstyleConfigurationPath,
                new PropertiesExpander(System.getProperties()),
                ConfigurationLoader.IgnoredModulesOptions.OMIT,
                new ThreadModeSettings(1, 1)
        );
    }

    /**
     * Create a new Source object.
     * @throws CheckstyleException if checker creation fails.
     */
    Source() {
        // Required for out-of-order classes
        permissions.add(new RuntimePermission("getProtectionDomain"));
        // Required for streams
        permissions.add(new RuntimePermission("accessDeclaredMembers"));
        permissions.add(new ReflectPermission("suppressAccessChecks"));
        // Required for generics
        permissions.add(new RuntimePermission("getClassLoader"));

        // Set up the checkstyle checker
        try {
            final ClassLoader moduleClassLoader = StringChecker.class.getClassLoader();
            checker = (StringChecker) new PackageObjectFactory(
                    StringChecker.class.getPackage().getName(), moduleClassLoader
            ).createModule(defaultCheckstyleConfiguration.getName());
            checker.setModuleClassLoader(moduleClassLoader);
        } catch (CheckstyleException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Implemented by each executor to return a map of filename to contents for their sources.
     *
     * @return a map of source filename to contents
     */
    protected abstract Map<String, String> sources();

    /**
     * Create a new source object from a received JSON string.
     *
     * @param json  JSON string to initialize the new source object
     * @param klass subclass of Source to deserialize into
     * @return new source object initialized from the JSON string
     */
    protected static Source received(final String json, final Class<? extends Source> klass) {
        Source source = gson.fromJson(json, klass);
        source.received = OffsetDateTime.now();
        return source;
    }

    /**
     * Mark an execution as completed and generate JSON to return to the client.
     *
     * @return JSON string representing the result of this execution
     */
    public String completed() {
        returned = OffsetDateTime.now();
        return gson.toJson(this);
    }

    /**
     * Helper function to convert a stack track to a String.
     *
     * @param e Throwable to extract a stack trace from and convert it to a String
     * @return String containing the stack trace
     */
    protected static String stackTraceToString(final Throwable e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);
        return stringWriter.toString();
    }

    /**
     * Hackily change the indentation level of the checkstyle configuration.
     * @param configuration the checkstyle configuration object to modify.
     * @return a copy of the configuration with the current indentation setting.
     */
    private Configuration reconfigureIndentLevel(final Configuration configuration) {
        // Duplicate the configuration
        ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        Configuration confCopy;
        try {
            ObjectOutputStream serializer = new ObjectOutputStream(byteOutput);
            serializer.writeObject(configuration);
            serializer.flush();
            ByteArrayInputStream byteInput = new ByteArrayInputStream(byteOutput.toByteArray());
            ObjectInputStream deserializer = new ObjectInputStream(byteInput);
            confCopy = (Configuration) deserializer.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        // Change the copy's indentation level
        Configuration treeWalker = Stream.of(confCopy.getChildren())
                .filter(f -> f.getName().equals("TreeWalker")).findAny().orElse(null);
        if (treeWalker == null) {
            return configuration;
        }
        Configuration indentation = Stream.of(treeWalker.getChildren())
                .filter(f -> f.getName().equals("Indentation")).findAny().orElse(null);
        if (indentation == null) {
            return configuration;
        }
        try {
            Field attributesField = indentation.getClass().getDeclaredField("attributeMap");
            attributesField.setAccessible(true);
            HashMap<String, String> attributesMap = (HashMap<String, String>) attributesField.get(indentation);
            attributesMap.put("basicOffset", String.valueOf(indentLevel));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return confCopy;
    }

    /**
     * Run checkstyle on sources.
     *
     * @return this object for chaining
     */
    public final Source checkstyle() {
        if (!runCheckstyle) {
            return this;
        }
        int messageCount = 0;
        try {
            checkstyleStarted = OffsetDateTime.now();
            checker.configure(reconfigureIndentLevel(defaultCheckstyleConfiguration));
            for (Map.Entry<String, String> source : sources().entrySet()) {
                SortedSet<LocalizedMessage> sourceMessages = checker.processString(source.getValue(), source.getKey());
                messageCount += sourceMessages.size();
            }
            checkstyleSucceeded = messageCount == 0;
        } catch (CheckstyleException e) {
            checkstyleSucceeded = false;
        } finally {
            checkstyleFinished = OffsetDateTime.now();
            checkstyleLength = diffTimestamps(checkstyleStarted, checkstyleFinished);
        }
        return this;
    }

    /**
     * Called on each executor to actually do the compile.
     *
     * @throws Exception thrown if the compile fails
     */
    protected abstract void doCompile() throws Exception;

    /**
     * Compile the source to bytecode.
     * <p>
     * Throws an exception if compilation fails, with the type depending on how the code was being compiled.
     *
     * @return this object for chaining
     */
    public final Source compile() {
        if (runCheckstyle && requireCheckstyle && !checkstyleSucceeded) {
            return this;
        }
        try {
            compileStarted = OffsetDateTime.now();
            doCompile();
            compiled = true;
        } catch (Exception e) {
            compilationErrorMessage = e.toString();
            compilationErrorStackTrace = stackTraceToString(e);
        } finally {
            compileFinished = OffsetDateTime.now();
            compileLength = diffTimestamps(compileStarted, compileFinished);
        }
        return this;
    }

    /**
     * Called on each executor to actually run the code.
     *
     * @throws Exception thrown if execution fails
     */
    protected abstract void doExecute() throws Exception;

    @Override
    public final Void call() {
        try {
            try {
                executionStarted = OffsetDateTime.now();
                doExecute();
                executed = true;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        } catch (ThreadDeath ignored) {
        } catch (Throwable e) {
            crashed = true;
            executionErrorMessage = e.toString();
            executionErrorStackTrace = stackTraceToString(e);
        }
        return null;
    }

    /**
     * Execute some compiled Java code.
     *
     * @return this object for chaining
     */
    @SuppressWarnings("deprecation")
    public final Source execute() {
        if (!compiled) {
            return this;
        }
        OUTPUT_LOCK.lock();
        try {
            FutureTask<Void> futureTask = new FutureTask<>(this);
            Thread executionThread = new Thread(futureTask);


            ByteArrayOutputStream combinedOutputStream = new ByteArrayOutputStream();
            PrintStream combinedStream = new PrintStream(combinedOutputStream);
            PrintStream old = System.out;
            PrintStream err = System.err;
            System.setOut(combinedStream);
            System.setErr(combinedStream);

            try {
                executionThread.start();
                futureTask.get(timeoutLength, TimeUnit.MILLISECONDS);
                timedOut = false;
            } catch (TimeoutException e) {
                futureTask.cancel(true);
                executionThread.stop();
                timedOut = true;
            } catch (Throwable e) {
                timedOut = false;
            } finally {
                executionFinished = OffsetDateTime.now();
                executionLength = diffTimestamps(executionStarted, executionFinished);

                System.out.flush();
                System.err.flush();
                System.setOut(old);
                System.setErr(err);
                if (executed) {
                    output = combinedOutputStream.toString();
                }
            }
            return this;
        } finally {
            OUTPUT_LOCK.unlock();
        }
    }

    /**
     * Compile and execute sources. Convenience method for compile + execute.
     *
     * @return this object for chaining
     */
    public Source run() {
        return this.checkstyle().compile().execute();
    }

    /**
     * Convert milliseconds to seconds.
     */
    private static final double MILLISECONDS_TO_SECONDS = 1000.0;

    /**
     * Compute the difference of two timestamps in seconds.
     *
     * @param start the starting timestamp
     * @param end   the ending timestamp
     * @return the difference between the two timestamps in seconds
     */
    private static double diffTimestamps(final OffsetDateTime start, final OffsetDateTime end) {
        return (end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli())
                / MILLISECONDS_TO_SECONDS;
    }

    @Override
    public final String toString() {
        return gson.toJson(this);
    }
}
