import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.security.Permissions;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.ScriptEvaluator;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * A class that runs a small web server that runs arbitrary Java code.
 */
public class WebServer {

    /** Small thread pool. */
    private static ExecutorService executor = Executors.newSingleThreadExecutor();

    /** Default timeout for code execution. */
    private static final int DEFAULT_TIMEOUT = 1000;

    /**
     * The port that our server listens on.
     */
    private static final int DEFAULT_SERVER_PORT = 8125;

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
                scriptEvaluator.cook(uploadContent.getString("source"));
                uploadContent.put("compiled", true);
                scriptEvaluator.evaluate(null);
                uploadContent.put("ran", true);
            } catch (CompileException e) {
                uploadContent.put("compiled", false);
            } catch (InvocationTargetException e) {
                uploadContent.put("ran", false);
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
    public static JsonObject run(final JsonObject uploadContent) {

        /*
         * Pull fields off of the request. We have a width, a height, and a flat array of image
         * bytes. The image bytes are encoded using Base64, but getBinary will take care of that for
         * us.
         */

        RunCode runCode = new RunCode(uploadContent);
        Future<JsonObject> future = executor.submit(runCode);
        executor.shutdown();

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
            uploadContent.put("timeout", false);
        } catch (Exception exception) {
            future.cancel(true);
            uploadContent.put("ran", true);
            uploadContent.put("timeout", true);
        } finally {
            System.out.flush();
            System.err.flush();
            System.setOut(old);
            System.setErr(err);
            uploadContent.put("out", byteArrayOutputStream.toString());
            uploadContent.put("err", byteArrayErrorStream.toString());
        }
        return returnContent;
    }

    /**
     * Start the code execution web server.
     *
     * @param unused unused input arguments
     */
    public static void main(final String[] unused) {

        Vertx vertx = Vertx.vertx();

        /*
         * Set up routes to our static assets: index.html, index.js, and index.css. We use a single
         * route here for all GET requests. In a more complex web server this would probably not be
         * appropriate, but in this simple case it works fine.
         */
        Router router = Router.router(vertx);
        router.route().method(HttpMethod.GET).handler(StaticHandler.create());

        /*
         * The BodyHandler ensures that we can retrieve JSON data from our request body. We send all
         * POST requests to the handler defined above. Again, in a more complex server you would
         * want to do something more sophisticated.
         */
        router.route().method(HttpMethod.POST).handler(BodyHandler.create());
        router.route(HttpMethod.POST, "/:run").handler(routingContext -> {
            JsonObject uploadContent = run(routingContext.getBodyAsJson());
            routingContext.response()
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(uploadContent.encode());
        });

        /*
         * Turn on compression, although upstream compression isn't currently implemented.
         */
        HttpServerOptions serverOptions = new HttpServerOptions();
        serverOptions.setCompressionSupported(true);
        serverOptions.setDecompressionSupported(true);

        HttpServer server = vertx.createHttpServer();

        /*
         * Ensure that the server is closed when we exit, to avoid port collisions.
         */
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                vertx.close();
            }
        });

        /*
         * Start the server.
         */
        System.out.println("Starting web server on localhost:" + DEFAULT_SERVER_PORT);
        System.out.println("If you get a message about a port in use, please shut\n"
                + "down other running instances of this web server.");
        server.requestHandler(router::accept).listen(DEFAULT_SERVER_PORT);
    }
}
