import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.puppycrawl.tools.checkstyle.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.AutomaticBean;
import com.puppycrawl.tools.checkstyle.api.BeforeExecutionFileFilter;
import com.puppycrawl.tools.checkstyle.api.BeforeExecutionFileFilterSet;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.api.Context;
import com.puppycrawl.tools.checkstyle.api.ExternalResourceHolder;
import com.puppycrawl.tools.checkstyle.api.FileSetCheck;
import com.puppycrawl.tools.checkstyle.api.FileText;
import com.puppycrawl.tools.checkstyle.api.Filter;
import com.puppycrawl.tools.checkstyle.api.FilterSet;
import com.puppycrawl.tools.checkstyle.api.LocalizedMessage;
import com.puppycrawl.tools.checkstyle.api.MessageDispatcher;
import com.puppycrawl.tools.checkstyle.api.RootModule;
import com.puppycrawl.tools.checkstyle.api.SeverityLevel;
import com.puppycrawl.tools.checkstyle.api.SeverityLevelCounter;
import com.puppycrawl.tools.checkstyle.utils.CommonUtil;

/**
 * This class provides the functionality to check a set of files.
 */
public final class StringChecker extends AutomaticBean implements MessageDispatcher, RootModule {

    /** Message to use when an exception occurs and should be printed as a violation. */
    public static final String EXCEPTION_MSG = "general.exception";

    /** Logger for Checker. */
    private final Log log;

    /** Maintains error count. */
    private final SeverityLevelCounter counter = new SeverityLevelCounter(
            SeverityLevel.ERROR);

    /** Vector of listeners. */
    private final List<AuditListener> listeners = new ArrayList<>();

    /** Vector of fileset checks. */
    private final List<FileSetCheck> fileSetChecks = new ArrayList<>();

    /** The audit event before execution file filters. */
    private final BeforeExecutionFileFilterSet beforeExecutionFileFilters =
            new BeforeExecutionFileFilterSet();

    /** The audit event filters. */
    private final FilterSet filters = new FilterSet();

    /** Class loader to resolve classes with. **/
    private ClassLoader classLoader = Thread.currentThread()
            .getContextClassLoader();

    /** The basedir to strip off in file names. */
    private String basedir;

    /** Locale country to report messages . **/
    private String localeCountry = Locale.getDefault().getCountry();
    /** Locale language to report messages . **/
    private String localeLanguage = Locale.getDefault().getLanguage();

    /** The factory for instantiating submodules. */
    private ModuleFactory moduleFactory;

    /** The classloader used for loading Checkstyle module classes. */
    private ClassLoader moduleClassLoader;

    /** The context of all child components. */
    private Context childContext;

    /** The file extensions that are accepted. */
    private String[] fileExtensions = CommonUtil.EMPTY_STRING_ARRAY;

    /**
     * The severity level of any violations found by submodules.
     * The value of this property is passed to submodules via
     * contextualize().
     *
     * <p>Note: Since the Checker is merely a container for modules
     * it does not make sense to implement logging functionality
     * here. Consequently Checker does not extend AbstractViolationReporter,
     * leading to a bit of duplicated code for severity level setting.
     */
    private SeverityLevel severity = SeverityLevel.ERROR;

    /** Name of a charset. */
    private String charset = System.getProperty("file.encoding", StandardCharsets.UTF_8.name());

    /** Controls whether exceptions should halt execution or not. */
    private boolean haltOnException = true;

    /**
     * Creates a new {@code Checker} instance.
     * The instance needs to be contextualized and configured.
     */
    public StringChecker() {
        addListener(counter);
        log = LogFactory.getLog(Checker.class);
    }

    /**
     * Sets cache file.
     * @param fileName the cache file.
     */
    public void setCacheFile(final String fileName) {
        final Configuration configuration = getConfiguration();
    }

    /**
     * Removes before execution file filter.
     * @param filter before execution file filter to remove.
     */
    public void removeBeforeExecutionFileFilter(final BeforeExecutionFileFilter filter) {
        beforeExecutionFileFilters.removeBeforeExecutionFileFilter(filter);
    }

    /**
     * Removes filter.
     * @param filter filter to remove.
     */
    public void removeFilter(final Filter filter) {
        filters.removeFilter(filter);
    }

    /**
     * Destroy the Checker.
     */
    public void destroy() {
        listeners.clear();
        fileSetChecks.clear();
        beforeExecutionFileFilters.clear();
        filters.clear();
    }

    /**
     * Removes a given listener.
     * @param listener a listener to remove
     */
    public void removeListener(final AuditListener listener) {
        listeners.remove(listener);
    }

    /**
     * Sets base directory.
     * @param setBasedir the base directory to strip off in file names
     */
    public void setBasedir(final String setBasedir) {
        this.basedir = setBasedir;
    }

    /**
     * Process some files.
     *
     * @param files files to process
     * @return number of errors
     * @throws CheckstyleException thrown if an error occurs
     */
    public int process(final List<File> files) throws CheckstyleException {
        // Prepare to start
        fireAuditStarted();
        for (final FileSetCheck fsc : fileSetChecks) {
            fsc.beginProcessing(charset);
        }

        final List<File> targetFiles = files.stream()
                .filter(file -> CommonUtil.matchesFileExtension(file, fileExtensions))
                .collect(Collectors.toList());
        processFiles(targetFiles);

        // Finish up
        // It may also log!!!
        fileSetChecks.forEach(FileSetCheck::finishProcessing);

        // It may also log!!!
        fileSetChecks.forEach(FileSetCheck::destroy);

        final int errorCount = counter.getCount();
        fireAuditFinished();
        return errorCount;
    }

    /**
     * Returns a set of external configuration resource locations which are used by all file set
     * checks and filters.
     * @return a set of external configuration resource locations which are used by all file set
     *         checks and filters.
     */
    private Set<String> getExternalResourceLocations() {
        final Set<String> externalResources = new HashSet<>();
        fileSetChecks.stream().filter(check -> check instanceof ExternalResourceHolder)
            .forEach(check -> {
                final Set<String> locations =
                    ((ExternalResourceHolder) check).getExternalResourceLocations();
                externalResources.addAll(locations);
            });
        filters.getFilters().stream().filter(filter -> filter instanceof ExternalResourceHolder)
            .forEach(filter -> {
                final Set<String> locations =
                    ((ExternalResourceHolder) filter).getExternalResourceLocations();
                externalResources.addAll(locations);
            });
        return externalResources;
    }

    /** Notify all listeners about the audit start. */
    private void fireAuditStarted() {
        final AuditEvent event = new AuditEvent(this);
        for (final AuditListener listener : listeners) {
            listener.auditStarted(event);
        }
    }

    /** Notify all listeners about the audit end. */
    private void fireAuditFinished() {
        final AuditEvent event = new AuditEvent(this);
        for (final AuditListener listener : listeners) {
            listener.auditFinished(event);
        }
    }

    /**
     * Processes a list of files with all FileSetChecks.
     * @param files a list of files to process.
     * @throws CheckstyleException if error condition within Checkstyle occurs.
     * @noinspection ProhibitedExceptionThrown
     */
    private void processFiles(final List<File> files) throws CheckstyleException {
        for (final File file : files) {
            try {
                final String fileName = file.getAbsolutePath();
                final long timestamp = file.lastModified();
                fireFileStarted(fileName);
                final SortedSet<LocalizedMessage> fileMessages = processFile(file);
                fireErrors(fileName, fileMessages);
                fireFileFinished(fileName);
            } catch (Exception ex) {
                // We need to catch all exceptions to put a reason failure (file name) in exception
                throw new CheckstyleException("Exception was thrown while processing "
                        + file.getPath(), ex);
            } catch (Error error) {
                // We need to catch all errors to put a reason failure (file name) in error
                throw new Error("Error was thrown while processing " + file.getPath(), error);
            }
        }
    }

    /**
     * Processes a file with all FileSetChecks.
     * @param file a file to process.
     * @return a sorted set of messages to be logged.
     * @throws CheckstyleException if error condition within Checkstyle occurs.
     * @noinspection ProhibitedExceptionThrown
     */
    private SortedSet<LocalizedMessage> processFile(final File file) throws CheckstyleException {
        final SortedSet<LocalizedMessage> fileMessages = new TreeSet<>();
        try {
            final FileText theText = new FileText(file.getAbsoluteFile(), charset);
            for (final FileSetCheck fsc : fileSetChecks) {
                fileMessages.addAll(fsc.process(file, theText));
            }
        } catch (final IOException ioe) {
            log.debug("IOException occurred.", ioe);
            fileMessages.add(new LocalizedMessage(1,
                    Definitions.CHECKSTYLE_BUNDLE, EXCEPTION_MSG,
                    new String[] {ioe.getMessage()}, null, getClass(), null));
        } catch (Exception ex) {
            if (haltOnException) {
                throw ex;
            }

            log.debug("Exception occurred.", ex);

            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw, true);

            ex.printStackTrace(pw);

            fileMessages.add(new LocalizedMessage(1,
                    Definitions.CHECKSTYLE_BUNDLE, EXCEPTION_MSG,
                    new String[] {sw.getBuffer().toString()},
                    null, getClass(), null));
        }
        return fileMessages;
    }

    /**
     * Processes a String with all FileSetChecks.
     * @param contents a String to process.
     * @param filename the name of the file whose contents are provided
     * @return a sorted set of messages to be logged.
     * @noinspection ProhibitedExceptionThrown
     */
    public SortedSet<LocalizedMessage> processString(final String contents, final String filename) {
        final SortedSet<LocalizedMessage> fileMessages = new TreeSet<>();
        final File pretendFile = new File(filename);
        try {
            final FileText theText = new FileText(pretendFile, Arrays.asList(contents.split("\n")));
            for (final FileSetCheck fsc : fileSetChecks) {
                fileMessages.addAll(fsc.process(pretendFile, theText));
            }
        } catch (Exception ex) {
            log.debug("Exception occurred.", ex);

            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw, true);

            ex.printStackTrace(pw);

            fileMessages.add(new LocalizedMessage(1,
                    Definitions.CHECKSTYLE_BUNDLE, EXCEPTION_MSG,
                    new String[] {sw.getBuffer().toString()},
                    null, getClass(), null));
        }
        return fileMessages;
    }

    /**
     * Check if all before execution file filters accept starting the file.
     *
     * @param fileName
     *            the file to be audited
     * @return {@code true} if the file is accepted.
     */
    private boolean acceptFileStarted(final String fileName) {
        final String stripped = CommonUtil.relativizeAndNormalizePath(basedir, fileName);
        return beforeExecutionFileFilters.accept(stripped);
    }

    /**
     * Notify all listeners about the beginning of a file audit.
     *
     * @param fileName
     *            the file to be audited
     */
    @Override
    public void fireFileStarted(final String fileName) {
        final String stripped = CommonUtil.relativizeAndNormalizePath(basedir, fileName);
        final AuditEvent event = new AuditEvent(this, stripped);
        for (final AuditListener listener : listeners) {
            listener.fileStarted(event);
        }
    }

    /**
     * Notify all listeners about the errors in a file.
     *
     * @param fileName the audited file
     * @param errors the audit errors from the file
     */
    @Override
    public void fireErrors(final String fileName, final SortedSet<LocalizedMessage> errors) {
        final String stripped = CommonUtil.relativizeAndNormalizePath(basedir, fileName);
        boolean hasNonFilteredViolations = false;
        for (final LocalizedMessage element : errors) {
            final AuditEvent event = new AuditEvent(this, stripped, element);
            if (filters.accept(event)) {
                for (final AuditListener listener : listeners) {
                    listener.addError(event);
                }
            }
        }
    }

    /**
     * Notify all listeners about the end of a file audit.
     *
     * @param fileName
     *            the audited file
     */
    @Override
    public void fireFileFinished(final String fileName) {
        final String stripped = CommonUtil.relativizeAndNormalizePath(basedir, fileName);
        final AuditEvent event = new AuditEvent(this, stripped);
        for (final AuditListener listener : listeners) {
            listener.fileFinished(event);
        }
    }

    @Override
    protected void finishLocalSetup() throws CheckstyleException {
        final Locale locale = new Locale(localeLanguage, localeCountry);
        LocalizedMessage.setLocale(locale);

        if (moduleFactory == null) {
            if (moduleClassLoader == null) {
                throw new CheckstyleException(
                        "if no custom moduleFactory is set, "
                                + "moduleClassLoader must be specified");
            }

            final Set<String> packageNames = PackageNamesLoader
                    .getPackageNames(moduleClassLoader);
            moduleFactory = new PackageObjectFactory(packageNames,
                    moduleClassLoader);
        }

        final DefaultContext context = new DefaultContext();
        context.add("charset", charset);
        context.add("classLoader", classLoader);
        context.add("moduleFactory", moduleFactory);
        context.add("severity", severity.getName());
        context.add("basedir", basedir);
        childContext = context;
    }

    /**
     * {@inheritDoc} Creates child module.
     * @noinspection ChainOfInstanceofChecks
     */
    @Override
    protected void setupChild(final Configuration childConf)
            throws CheckstyleException {
        final String name = childConf.getName();
        final Object child;

        try {
            child = moduleFactory.createModule(name);

            if (child instanceof AutomaticBean) {
                final AutomaticBean bean = (AutomaticBean) child;
                bean.contextualize(childContext);
                bean.configure(childConf);
            }
        } catch (final CheckstyleException ex) {
            throw new CheckstyleException("cannot initialize module " + name
                    + " - " + ex.getMessage(), ex);
        }
        if (child instanceof FileSetCheck) {
            final FileSetCheck fsc = (FileSetCheck) child;
            fsc.init();
            addFileSetCheck(fsc);
        } else if (child instanceof BeforeExecutionFileFilter) {
            final BeforeExecutionFileFilter filter = (BeforeExecutionFileFilter) child;
            addBeforeExecutionFileFilter(filter);
        } else if (child instanceof Filter) {
            final Filter filter = (Filter) child;
            addFilter(filter);
        } else if (child instanceof AuditListener) {
            final AuditListener listener = (AuditListener) child;
            addListener(listener);
        } else {
            throw new CheckstyleException(name
                    + " is not allowed as a child in Checker");
        }
    }

    /**
     * Adds a FileSetCheck to the list of FileSetChecks
     * that is executed in process().
     * @param fileSetCheck the additional FileSetCheck
     */
    public void addFileSetCheck(final FileSetCheck fileSetCheck) {
        fileSetCheck.setMessageDispatcher(this);
        fileSetChecks.add(fileSetCheck);
    }

    /**
     * Adds a before execution file filter to the end of the event chain.
     * @param filter the additional filter
     */
    public void addBeforeExecutionFileFilter(final BeforeExecutionFileFilter filter) {
        beforeExecutionFileFilters.addBeforeExecutionFileFilter(filter);
    }

    /**
     * Adds a filter to the end of the audit event filter chain.
     * @param filter the additional filter
     */
    public void addFilter(final Filter filter) {
        filters.addFilter(filter);
    }

    @Override
    public void addListener(final AuditListener listener) {
        listeners.add(listener);
    }

    /**
     * Sets the file extensions that identify the files that pass the
     * filter of this FileSetCheck.
     * @param extensions the set of file extensions. A missing
     *     initial '.' character of an extension is automatically added.
     */
    public void setFileExtensions(final String... extensions) {
        if (extensions == null) {
            fileExtensions = null;
        } else {
            fileExtensions = new String[extensions.length];
            for (int i = 0; i < extensions.length; i++) {
                final String extension = extensions[i];
                if (CommonUtil.startsWithChar(extension, '.')) {
                    fileExtensions[i] = extension;
                } else {
                    fileExtensions[i] = "." + extension;
                }
            }
        }
    }

    /**
     * Sets the factory for creating submodules.
     *
     * @param setModuleFactory the factory for creating FileSetChecks
     */
    public void setModuleFactory(final ModuleFactory setModuleFactory) {
        this.moduleFactory = setModuleFactory;
    }

    /**
     * Sets locale country.
     * @param setLocaleCountry the country to report messages
     */
    public void setLocaleCountry(final String setLocaleCountry) {
        this.localeCountry = setLocaleCountry;
    }

    /**
     * Sets locale language.
     * @param setLocaleLanguage the language to report messages
     */
    public void setLocaleLanguage(final String setLocaleLanguage) {
        this.localeLanguage = setLocaleLanguage;
    }

    /**
     * Sets the severity level.  The string should be one of the names
     * defined in the {@code SeverityLevel} class.
     *
     * @param setSeverity  The new severity level
     * @see SeverityLevel
     */
    public void setSeverity(final String setSeverity) {
        this.severity = SeverityLevel.getInstance(setSeverity);
    }

    /**
     * Sets the classloader that is used to contextualize fileset checks.
     * Some Check implementations will use that classloader to improve the
     * quality of their reports, e.g. to load a class and then analyze it via
     * reflection.
     * @param setClassLoader the new classloader
     */
    public void setClassLoader(final ClassLoader setClassLoader) {
        this.classLoader = setClassLoader;
    }

    @Override
    public void setModuleClassLoader(final ClassLoader setModuleClassLoader) {
        this.moduleClassLoader = setModuleClassLoader;
    }

    /**
     * Sets a named charset.
     * @param setCharset the name of a charset
     * @throws UnsupportedEncodingException if charset is unsupported.
     */
    public void setCharset(final String setCharset)
            throws UnsupportedEncodingException {
        if (!Charset.isSupported(setCharset)) {
            final String message = "unsupported charset: '" + setCharset + "'";
            throw new UnsupportedEncodingException(message);
        }
        this.charset = setCharset;
    }

    /**
     * Sets the field haltOnException.
     * @param setHaltOnException the new value.
     */
    public void setHaltOnException(final boolean setHaltOnException) {
        this.haltOnException = setHaltOnException;
    }

    /**
     * Clears the cache.
     */
    public void clearCache() {
    }

}
