import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.security.Permissions;
import java.time.OffsetDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.ScriptEvaluator;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import static spark.Spark.post;
import static spark.Spark.port;
import static spark.Spark.staticFiles;

/**
 * A small web server that runs arbitrary Java code.
 */
public class WebServer {

    /** Small thread pool. */
    private static ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Default timeout for code execution. */
    private static final int DEFAULT_TIMEOUT = 1000;

    /**
     * The port that our server listens on.
     */
    private static final int DEFAULT_SERVER_PORT = 3223;

    /** Runnable subclass for execution. */
    static class RunCode implements Callable<JsonObject> {

        /** Content to process. */
        private JsonObject uploadContent;

        /**
         * Instantiates a new run code.
         *
         * @param setUploadContent the set upload content
         */
        RunCode(final JsonObject setUploadContent) {
            uploadContent = setUploadContent;
        }

        @Override
        public JsonObject call() {
            ScriptEvaluator scriptEvaluator = new ScriptEvaluator();
            scriptEvaluator.setPermissions(new Permissions());
            try {
                uploadContent.add("compileStart", OffsetDateTime.now().toString());
                scriptEvaluator.cook(uploadContent.get("source").asString());
                uploadContent.add("compileFinish", OffsetDateTime.now().toString());
                uploadContent.add("compiled", true);

                uploadContent.add("runStart", OffsetDateTime.now().toString());
                scriptEvaluator.evaluate(null);
                uploadContent.add("runFinish", OffsetDateTime.now().toString());
                uploadContent.add("ran", true);
            } catch (CompileException e) {
                uploadContent.add("compiled", false);
            } catch (InvocationTargetException e) {
                uploadContent.add("ran", false);
            } finally {
                uploadContent.add("executionFinish", OffsetDateTime.now().toString());
            }
            return uploadContent;
        }
    }

    /**
     * Run some Java code.
     *
     * @param uploadContent a JSON object describing what to do
     * @return the same JSON object with some additional information about what happened
     */
    public static synchronized JsonObject run(final JsonObject uploadContent) {

        RunCode runCode = new RunCode(uploadContent);
        uploadContent.add("submitted", OffsetDateTime.now().toString());
        Future<JsonObject> future = executor.submit(runCode);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream byteArrayErrorStream = new ByteArrayOutputStream();
        PrintStream outStream = new PrintStream(byteArrayOutputStream);
        PrintStream errStream = new PrintStream(byteArrayErrorStream);
        PrintStream old = System.out;
        PrintStream err = System.err;
        System.setOut(outStream);
        System.setErr(errStream);
        JsonObject returnContent = null;
        try {
            returnContent = future.get(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
            uploadContent.add("timeout", false);
        } catch (Exception exception) {
            future.cancel(true);
            uploadContent.add("ran", true);
            uploadContent.add("timeout", true);
        } finally {
            uploadContent.add("completed", OffsetDateTime.now().toString());
            System.out.flush();
            System.err.flush();
            System.setOut(old);
            System.setErr(err);
            uploadContent.add("out", byteArrayOutputStream.toString());
            uploadContent.add("err", byteArrayErrorStream.toString());
        }
        return returnContent;
    }

    /**
     * Start the code execution web server.
     *
     * @param unused unused input arguments
     */
    public static void main(final String[] unused) {
        port(DEFAULT_SERVER_PORT);
        staticFiles.location("/webroot");
        post("/run", (request, response) -> {
            JsonObject requestContent = Json.parse(request.body()).asObject();
            requestContent.add("received", OffsetDateTime.now().toString());
            JsonObject responseContent = run(requestContent);
            requestContent.add("returned", OffsetDateTime.now().toString());
            response.body(responseContent.toString());
            response.type("application/json; charset=utf-8");
            return response;
        });
    }
}
