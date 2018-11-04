import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import org.apache.commons.cli.*;
import spark.Filter;

import static spark.Spark.*;

/**
 * A small web server that runs arbitrary Java code.
 */
public class WebServer {
    /**
     * The port that our server listens on.
     */
    private static final int DEFAULT_SERVER_PORT = 8888;

    static {
        System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
        System.setProperty("org.eclipse.jetty.LEVEL", "OFF");
    }

    /**
     * Run submitted code.
     * <p>
     * Exposed here for use by the testing suite.
     *
     * @param requestBody request content as a String
     * @return response as a String
     */
    public static String run(final String requestBody) {
        JsonObject requestContent = Json.parse(requestBody).asObject();
        String runAs;
        if (requestContent.get("as") != null) {
            runAs = requestContent.get("as").asString();
        } else {
            runAs = "Snippet";
        }
        Source source = null;
        switch (runAs) {
            case "Snippet":
                source = Source.received(requestBody, Snippet.class);
                break;
            case "SimpleCompiler":
                source = Source.received(requestBody, SimpleCompiler.class);
                break;
            default:
                break;
        }
        if (source == null) {
            return requestBody;
        }
        return source.run().completed();
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
        options.addOption("l", "local", false, "Enable local development mode by disabling CORS.");
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
            try {
                response.type("application/json; charset=utf-8");
                return run(request.body());
            } catch (Exception e) {
                if (settings.hasOption("v")) {
                    System.err.println(e.toString());
                }
                return "";
            }
        });

        if (settings.hasOption("l")) {
            after((Filter) (request, response) -> {
                response.header("Access-Control-Allow-Origin", "*");
                response.header("Access-Control-Allow-Methods", "POST,GET");
            });
        }
    }
}
