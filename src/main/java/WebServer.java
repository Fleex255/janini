import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import org.apache.commons.cli.*;
import org.codehaus.janino.ScriptEvaluator;
import org.codehaus.janino.SimpleCompiler;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.Permissions;
import java.time.OffsetDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static spark.Spark.*;

/**
 * A small web server that runs arbitrary Java code.
 */
public class WebServer {

    /**
     * Lock to serialize System.out and System.err.
     */
    private static final ReentrantLock OUTPUT_LOCK = new ReentrantLock();

    /**
     * Max timeout for code execution.
     */
    private static final int MAX_TIMEOUT = 1000;

    /**
     * Default timeout for code execution.
     */
    private static final int DEFAULT_TIMEOUT = 100;

    /**
     * The port that our server listens on.
     */
    private static final int DEFAULT_SERVER_PORT = 8888;

    /**
     * Add a stack track to the current execution object.
     *
     * @param addTo the JSON object to add the stack trace to.
     * @param e the exception that was thrown.
     * @param as the field on the JSON object to set.
     */
    private static void addStackTrace(final JsonObject addTo, final Throwable e, final String as) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);
        String s = stringWriter.toString();
        addTo.add(as, s);
    }

    /**
     * Runnable subclass for execution.
     */
    static class RunCode implements Callable<JsonObject> {

        /**
         * Our script evaluator.
         */
        private ScriptEvaluator scriptEvaluator;

        /**
         * Our runnable method for simple compiler execution.
         */
        private Method methodToRun;

        /**
         * Instantiates a new run code with a script to run.
         *
         * @param setScriptEvaluator the script evaluator to use
         */
        RunCode(final ScriptEvaluator setScriptEvaluator) {
            scriptEvaluator = setScriptEvaluator;
        }

        /**
         * Instantiate a new run code object with a class method to run.
         *
         * @param setMethodToRun the method to run
         */
        RunCode(final Method setMethodToRun) {
            methodToRun = setMethodToRun;
        }

        @Override
        public JsonObject call() {
            JsonObject uploadContent = new JsonObject();
            try {
                if (scriptEvaluator != null) {
                    scriptEvaluator.evaluate(new Object[0]);
                } else if (methodToRun != null) {
                    methodToRun.invoke(null, (Object) new String[] {});
                }
                uploadContent.add("completed", true);
            } catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
                uploadContent.add("completed", false);
                uploadContent.add("runtimeError", e.getMessage());
                addStackTrace(uploadContent, e, "runtimeStackTrace");
            }
            return uploadContent;
        }
    }

    /**
     * Run some Java code.
     *
     * @param uploadContent a JSON object describing what to do
     */
    @SuppressWarnings("deprecation")
    static void run(final JsonObject uploadContent) {
        uploadContent.add("submitted", OffsetDateTime.now().toString());

        RunCode runCode = null;
        String runAs;
        if (uploadContent.get("as") != null) {
            runAs = uploadContent.get("as").asString();
        } else {
            runAs = "script";
        }

        Permissions permissions = new Permissions();
        permissions.add(new RuntimePermission("getProtectionDomain"));

        if (runAs.equals("script")) {
            ScriptEvaluator scriptEvaluator = new ScriptEvaluator();
            scriptEvaluator.setPermissions(permissions);

            try {
                uploadContent.add("compileStart", OffsetDateTime.now().toString());
                scriptEvaluator.cook(uploadContent.get("source").asString());
                uploadContent.add("compiled", true);

                runCode = new RunCode(scriptEvaluator);
            } catch (Throwable e) {
                uploadContent.add("compiled", false);
                uploadContent.add("compileError", e.getMessage());
                addStackTrace(uploadContent, e, "compileStackTrace");
                return;
            } finally {
                uploadContent.add("compileFinish", OffsetDateTime.now().toString());
            }
        } else if (runAs.equals("compiler")) {
            InputStream stream = new ByteArrayInputStream(uploadContent.get("source")
                    .asString().getBytes(StandardCharsets.UTF_8));
            try {
                SimpleCompiler simpleCompiler = new SimpleCompiler();
                simpleCompiler.setPermissions(permissions);
                simpleCompiler.cook("", stream);
                ClassLoader classLoader = simpleCompiler.getClassLoader();

                uploadContent.add("compiled", true);
                Class<?> c;
                try {
                    c = classLoader.loadClass(uploadContent.get("class").asString());
                } catch (Throwable e) {
                    throw new Exception("Class " + uploadContent.get("class").asString() + " is not defined");
                }
                String mainMethod = "main";
                if (uploadContent.get("main") != null) {
                    mainMethod = uploadContent.get("main").asString();
                }
                Method methodToRun = c.getMethod(mainMethod, String[].class);
                if (!Modifier.isStatic(methodToRun.getModifiers())) {
                    throw new Exception(uploadContent.get("class").asString() + "." + mainMethod + " must be static");
                }
                runCode = new RunCode(methodToRun);
            } catch (Throwable e) {
                uploadContent.add("compiled", false);
                uploadContent.add("compileError", e.getMessage());
                addStackTrace(uploadContent, e, "compileStackTrace");
                return;
            } finally {
                uploadContent.add("compileFinish", OffsetDateTime.now().toString());
            }
        }
        if (runCode == null) {
            return;
        }

        OUTPUT_LOCK.lock();
        FutureTask<JsonObject> futureTask = new FutureTask<>(runCode);
        Thread executionThread = new Thread(futureTask);

        ByteArrayOutputStream combinedOutputStream = new ByteArrayOutputStream();
        PrintStream combinedStream = new PrintStream(combinedOutputStream);
        PrintStream old = System.out;
        PrintStream err = System.err;
        System.setOut(combinedStream);
        System.setErr(combinedStream);

        try {
            executionThread.start();
            uploadContent.add("runStart", OffsetDateTime.now().toString());
            int timeout = DEFAULT_TIMEOUT;
            if (uploadContent.get("timeout") != null) {
                timeout = uploadContent.get("timeout").asInt();
            }
            if (timeout > MAX_TIMEOUT) {
                timeout = MAX_TIMEOUT;
            }
            uploadContent.set("timeoutLength", timeout);
            JsonObject executionContent = futureTask.get(timeout, TimeUnit.MILLISECONDS);
            uploadContent.add("timeout", false);
            uploadContent.merge(executionContent);
        } catch (Throwable e) {
            futureTask.cancel(true);
            executionThread.stop();
            uploadContent.set("completed", false);
            uploadContent.set("timeout", true);
            addStackTrace(uploadContent, e, "runtimeStackTrace");
        } finally {
            uploadContent.add("runFinish", OffsetDateTime.now().toString());
            System.out.flush();
            System.err.flush();
            System.setOut(old);
            System.setErr(err);
            if (uploadContent.get("completed").asBoolean()) {
                uploadContent.add("output", combinedOutputStream.toString());
            }
            OUTPUT_LOCK.unlock();
        }
    }

    static {
        System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
        System.setProperty("org.eclipse.jetty.LEVEL", "OFF");
    }

    /**
     * Start the code execution web server.
     *
     * @param args command line arguments
     * @throws ParseException thrown if command line options cannot be parsed
     */
    public static void main(final String[] args) throws ParseException {

        Options options = new Options();
        options.addOption("p", "port", true, "Port to use. Default is 8888.");
        options.addOption("i", "interactive", false, "Enable interactive mode.");
        options.addOption("v", "verbose", false, "Enable verbose mode.");
        CommandLineParser parser = new BasicParser();
        CommandLine settings = parser.parse(options, args);

        if (settings.hasOption("p")) {
            port(Integer.parseInt(settings.getOptionValue("p")));
        } else {
            port(DEFAULT_SERVER_PORT);
        }

        if (settings.hasOption("i")) {
            staticFiles.location("/webroot");
        }

        post("/run", (request, response) -> {
            JsonObject requestContent;
            try {
                requestContent = Json.parse(request.body()).asObject();
                requestContent.add("received", OffsetDateTime.now().toString());
                run(requestContent);
                requestContent.add("returned", OffsetDateTime.now().toString());
                requestContent.add("version", "0.3.1");
                response.type("application/json; charset=utf-8");
                return requestContent.toString();
            } catch (Exception e) {
                if (settings.hasOption("v")) {
                    System.err.println(e.toString());
                }
                return "";
            }
        });
    }
}
