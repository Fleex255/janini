import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import org.apache.commons.cli.*;
import org.codehaus.janino.ScriptEvaluator;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.security.Permissions;
import java.time.OffsetDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static spark.Spark.*;

/**
 * A small web server that runs arbitrary Java code.
 */
public class WebServer {

    /**
     * Default timeout for code execution.
     */
    private static final int DEFAULT_TIMEOUT = 100;

    /**
     * The port that our server listens on.
     */
    private static final int DEFAULT_SERVER_PORT = 8888;

    /**
     * Runnable subclass for execution.
     */
    static class RunCode implements Callable<JsonObject> {

        /**
         * Our script evaluator.
         */
        private ScriptEvaluator scriptEvaluator;

        /**
         * Instantiates a new run code.
         *
         * @param setScriptEvaluator the script evaluator to use
         */
        RunCode(final ScriptEvaluator setScriptEvaluator) {
            scriptEvaluator = setScriptEvaluator;
        }

        @Override
        public JsonObject call() {
            JsonObject uploadContent = new JsonObject();
            try {
                scriptEvaluator.evaluate(new Object[0]);
                uploadContent.add("completed", true);
            } catch (InvocationTargetException e) {
                uploadContent.add("completed", false);
                uploadContent.add("runtimeError", e.getMessage());
                StringWriter writer = new StringWriter();
                PrintWriter printWriter = new PrintWriter(writer);
                e.printStackTrace(printWriter);
                printWriter.flush();
                uploadContent.add("runtimeStackTrace", writer.toString());
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
    public static synchronized void run(final JsonObject uploadContent) {
        uploadContent.add("submitted", OffsetDateTime.now().toString());

        ScriptEvaluator scriptEvaluator = new ScriptEvaluator();
        Permissions permissions = new Permissions();
        permissions.add(new RuntimePermission("getProtectionDomain"));
        scriptEvaluator.setPermissions(permissions);

        try {
            uploadContent.add("compileStart", OffsetDateTime.now().toString());
            scriptEvaluator.cook(uploadContent.get("source").asString());
            uploadContent.add("compiled", true);
        } catch (Throwable e) {
            uploadContent.add("compiled", false);
            uploadContent.add("compileError", e.getMessage());
            return;
        } finally {
            uploadContent.add("compileFinish", OffsetDateTime.now().toString());
        }

        RunCode runCode = new RunCode(scriptEvaluator);
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
            JsonObject executionContent = futureTask.get(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
            uploadContent.add("timeout", false);
            uploadContent.merge(executionContent);
        } catch (Exception exception) {
            futureTask.cancel(true);
            executionThread.stop();
            uploadContent.set("completed", false);
            uploadContent.set("timeout", true);
        } finally {
            uploadContent.add("runFinish", OffsetDateTime.now().toString());
            System.out.flush();
            System.err.flush();
            System.setOut(old);
            System.setErr(err);
            if (uploadContent.get("completed").asBoolean()) {
                uploadContent.add("output", combinedOutputStream.toString());
            }
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
        String location = "/";
        if (settings.hasOption("i")) {
            staticFiles.location("/webroot");
            location = "/run";
        }
        post(location, (request, response) -> {
            JsonObject requestContent;
            try {
                requestContent = Json.parse(request.body()).asObject();
                requestContent.add("received", OffsetDateTime.now().toString());
                run(requestContent);
                requestContent.add("returned", OffsetDateTime.now().toString());
                requestContent.add("version", "0.2");
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
