package com.lazerycode.jmeter;

import com.lazerycode.jmeter.configuration.*;
import com.lazerycode.jmeter.properties.PropertyHandler;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.joda.time.format.DateTimeFormat;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.lazerycode.jmeter.UtilityFunctions.isSet;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.copyInputStreamToFile;

/**
 * JMeter Maven plugin.
 * This is a base class for the JMeter mojos.
 *
 * @author Tim McCune
 */
@SuppressWarnings({"UnusedDeclaration", "FieldCanBeLocal", "JavaDoc"}) // Mojos get their fields set via reflection
public abstract class JMeterAbstractMojo extends AbstractMojo {

	/**
	 * Sets the list of include patterns to use in directory scan for JMX files.
	 * Relative to testFilesDirectory.
	 */
	@Parameter
	protected List<String> testFilesIncluded;

	/**
	 * Sets the list of exclude patterns to use in directory scan for Test files.
	 * Relative to testFilesDirectory.
	 */
	@Parameter
	protected List<String> testFilesExcluded;

	/**
	 * Path under which JMX files are stored.
	 */
	@Parameter(defaultValue = "${basedir}/src/test/jmeter")
	protected File testFilesDirectory;

	/**
	 * Timestamp the test results.
	 */
	@Parameter(defaultValue = "true")
	protected boolean testResultsTimestamp;

	/**
	 * Append the results timestamp to the filename
	 * (It will be prepended by default if testResultsTimestamp is set to true)
	 */
	@Parameter(defaultValue = "false")
	protected boolean appendResultsTimestamp;

	/**
	 * Set the format of the timestamp that is appended to the results filename.
	 * (This assumes that testResultsTimestamp is set to 'true')
	 * For formatting see http://joda-time.sourceforge.net/apidocs/org/joda/time/format/DateTimeFormat.html
	 */
	@Parameter()
	protected String resultsFileNameDateFormat;

	/**
	 * Set the format of the results generated by JMeter
	 * Valid values are: xml, csv (XML set by default).
	 */
	@Parameter(defaultValue = "xml")
	protected String resultsFileFormat;

	/**
	 * Set the directory that JMeter results are saved to.
	 */
	@Parameter
	protected String resultsDirectory;

	/**
	 * Absolute path to JMeter custom (test dependent) properties file.
	 */
	@Parameter
	protected Map<String, String> propertiesJMeter = new HashMap<String, String>();

	/**
	 * JMeter Properties that are merged with precedence into default JMeter file in saveservice.properties
	 */
	@Parameter
	protected Map<String, String> propertiesSaveService = new HashMap<String, String>();

	/**
	 * JMeter Properties that are merged with precedence into default JMeter file in upgrade.properties
	 */
	@Parameter
	protected Map<String, String> propertiesUpgrade = new HashMap<String, String>();

	/**
	 * JMeter Properties that are merged with precedence into default JMeter file in user.properties
	 * user.properties takes precedence over jmeter.properties
	 */
	@Parameter
	protected Map<String, String> propertiesUser = new HashMap<String, String>();

	/**
	 * JMeter Global Properties that override those given in jmeterProps. <br>
	 * This sets local and remote properties (JMeter's definition of global properties is actually remote properties)
	 * and overrides any local/remote properties already set
	 */
	@Parameter
	protected Map<String, String> propertiesGlobal = new HashMap<String, String>();

	/**
	 * (Java) System properties set for the test run.
	 * Properties are merged with precedence into default JMeter file system.properties
	 */
	@Parameter
	protected Map<String, String> propertiesSystem = new HashMap<String, String>();

	/**
	 * Absolute path to JMeter custom (test dependent) properties file.
	 */
	@Parameter
	protected File customPropertiesFile;

	/**
	 * Replace the default JMeter properties with any custom properties files supplied.
	 * (If set to false any custom properties files will be merged with the default JMeter properties files, custom properties will overwrite default ones)
	 */
	@Parameter(defaultValue = "true")
	protected boolean propertiesReplacedByCustomFiles;

	/**
	 * Value class that wraps all proxy configurations.
	 */
	@Parameter
	protected ProxyConfiguration proxyConfig;

	/**
	 * Value class that wraps all remote configurations.
	 */
	@Parameter
	protected RemoteConfiguration remoteConfig;

	/**
	 * Value class that wraps all remote configurations.
	 */
	@Parameter
	protected Set<JMeterPlugins> jmeterPlugins;

	/**
	 * Value class that wraps all JMeter Process JVM settings.
	 */
	@Parameter
	protected JMeterProcessJVMSettings jMeterProcessJVMSettings;

	/**
	 * Set a root log level to override all log levels used by JMeter
	 * Valid log levels are: FATAL_ERROR, ERROR, WARN, INFO, DEBUG (They are not case sensitive);
	 * If you try to set an invalid log level it will be ignored
	 */
	@Parameter
	protected String overrideRootLogLevel;

	/**
	 * Name of advanced logging configuration file that is in the <testFilesDirectory>
	 * Defaults to "logkit.xml"
	 */
	@Parameter(defaultValue = "logkit.xml")
	protected String logConfigFilename;

	/**
	 * Sets whether FailureScanner should ignore failures in JMeter result file.
	 * Failures are for example failed requests
	 */
	@Parameter(defaultValue = "false")
	protected boolean ignoreResultFailures;

	/**
	 * Suppress JMeter output
	 */
	@Parameter(defaultValue = "true")
	protected boolean suppressJMeterOutput;

	/**
	 * Get a list of artifacts used by this plugin
	 */
	@Parameter(defaultValue = "${plugin.artifacts}", required = true, readonly = true)
	protected List<Artifact> pluginArtifacts;

	/**
	 * The information extracted from the Mojo being currently executed
	 */
	@Parameter(defaultValue = "${mojoExecution}", required = true, readonly = true)
	protected MojoExecution mojoExecution;

	/**
	 * Skip the JMeter tests
	 */
	@Parameter(defaultValue = "false")
	protected boolean skipTests;

	/**
	 * Build failed if source directory is not found.
	 */
	@Parameter(defaultValue = "true")
	protected boolean sourceDirFailed;

	//------------------------------------------------------------------------------------------------------------------

	/**
	 * Place where the JMeter files will be generated.
	 */
	@Parameter(defaultValue = "${project.build.directory}/jmeter")
	protected transient File workDir;

	/**
	 * Other directories will be created by this plugin and used by JMeter
	 */
	protected File binDir;
	protected File libDir;
	protected File libExtDir;
	protected File logsDir;
	protected File resultsDir;

	/**
	 * All property files are stored in this artifact, comes with JMeter library
	 */
	protected final String jmeterConfigArtifact = "ApacheJMeter_config";
	protected JMeterArgumentsArray testArgs;
	protected PropertyHandler pluginProperties;
	protected boolean resultsOutputIsCSVFormat = false;

	//==================================================================================================================

	/**
	 * Generate the directory tree utilised by JMeter.
	 */
	@SuppressWarnings("ResultOfMethodCallIgnored")
	protected void generateJMeterDirectoryTree() {
		logsDir = new File(workDir, "logs");
		logsDir.mkdirs();
		binDir = new File(workDir, "bin");
		binDir.mkdirs();
		if (null != resultsDirectory) {
			resultsDir = new File(resultsDirectory.replaceAll("\\|/", File.separator));
		} else {
			resultsDir = new File(workDir, "results");
		}
		resultsDir.mkdirs();
		libDir = new File(workDir, "lib");
		libExtDir = new File(libDir, "ext");
		libExtDir.mkdirs();

		//JMeter expects a <workdir>/lib/junit directory and complains if it can't find it.
		new File(libDir, "junit").mkdirs();
	}

	protected void propertyConfiguration() throws MojoExecutionException {
		pluginProperties = new PropertyHandler(testFilesDirectory, binDir, getArtifactNamed(jmeterConfigArtifact), propertiesReplacedByCustomFiles, sourceDirFailed);
		pluginProperties.setJMeterProperties(propertiesJMeter);
		pluginProperties.setJMeterGlobalProperties(propertiesGlobal);
		pluginProperties.setJMeterSaveServiceProperties(propertiesSaveService);
		pluginProperties.setJMeterUpgradeProperties(propertiesUpgrade);
		pluginProperties.setJmeterUserProperties(propertiesUser);
		pluginProperties.setJMeterSystemProperties(propertiesSystem);
		pluginProperties.configureJMeterPropertiesFiles();
		pluginProperties.setDefaultPluginProperties(binDir.getAbsolutePath());
	}

	/**
	 * Create the JMeter directory tree and copy all compile time JMeter dependencies into it.
	 * Generic compile time artifacts are copied into the libDir
	 * ApacheJMeter_* artifacts are copied into the libExtDir
	 * Runtime dependencies set by the user are also copied into the libExtDir
	 *
	 * @throws MojoExecutionException
	 */
	protected void populateJMeterDirectoryTree() throws MojoExecutionException {
		for (Artifact artifact : pluginArtifacts) {
			try {
				if (Artifact.SCOPE_COMPILE.equals(artifact.getScope()) || Artifact.SCOPE_RUNTIME.equals(artifact.getScope())) {
					if (artifact.getArtifactId().equals(jmeterConfigArtifact)) {
						extractConfigSettings(artifact);
					} else if (artifact.getArtifactId().equals("ApacheJMeter")) {
						copyFile(artifact.getFile(), new File(binDir + File.separator + artifact.getArtifactId() + ".jar"));
					} else if (artifact.getArtifactId().startsWith("ApacheJMeter_")) {
						copyFile(artifact.getFile(), new File(libExtDir + File.separator + artifact.getFile().getName()));
					} else if (isArtifactAJMeterDependency(artifact)) {
						copyFile(artifact.getFile(), new File(libDir + File.separator + artifact.getFile().getName()));
					} else if (isArtifactAnExplicitDependency(artifact)) {
						if (isArtifactMarkedAsAJMeterPlugin(artifact)) {
							copyFile(artifact.getFile(), new File(libExtDir + File.separator + artifact.getFile().getName()));
						} else {
							copyFile(artifact.getFile(), new File(libDir + File.separator + artifact.getFile().getName()));
						}
					}
				}
			} catch (IOException e) {
				throw new MojoExecutionException("Unable to populate the JMeter directory tree: " + e);
			}
		}
	}

	/**
	 * Extract the configuration settings (not properties files) form the configuration artifact and load them into the /bin directory
	 *
	 * @param artifact Configuration artifact
	 * @throws IOException
	 */
	private void extractConfigSettings(Artifact artifact) throws IOException {
		JarFile configSettings = new JarFile(artifact.getFile());
		Enumeration<JarEntry> entries = configSettings.entries();
		while (entries.hasMoreElements()) {
			JarEntry jarFileEntry = entries.nextElement();
			// Only interested in files in the /bin directory that are not properties files
			if (!jarFileEntry.isDirectory() && jarFileEntry.getName().startsWith("bin") && !jarFileEntry.getName().endsWith(".properties")) {
				File fileToCreate = new File(workDir.getCanonicalPath() + File.separator + jarFileEntry.getName());
				if (jarFileEntry.getName().endsWith(logConfigFilename) && fileToCreate.exists()) {
					break;
				}
				copyInputStreamToFile(configSettings.getInputStream(jarFileEntry), fileToCreate);
			}
		}
		configSettings.close();
	}

	protected boolean isArtifactMarkedAsAJMeterPlugin(Artifact artifact) {
		if (null != jmeterPlugins) {
			for (JMeterPlugins identifiedPlugin : jmeterPlugins) {
				if (identifiedPlugin.toString().equals(artifact.getGroupId() + ":" + artifact.getArtifactId())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Check if the given artifact is needed by an explicit dependency (a dependency, that is explicitly defined in
	 * the pom of the project using the jmeter-maven-plugin).
	 *
	 * @param artifact Artifact to examine
	 * @return true if the given artifact is needed by a explicit dependency.
	 */
	protected boolean isArtifactAnExplicitDependency(Artifact artifact) {
		try {
			//Maven 3
			List<Dependency> pluginDependencies = mojoExecution.getPlugin().getDependencies();
			for (Dependency dependency : pluginDependencies) {
				for (String parent : artifact.getDependencyTrail()) {
					if (parent.contains(dependency.getGroupId() + ":" + dependency.getArtifactId()) && parent.contains(dependency.getVersion())) {
						return true;
					}
				}
			}
		} catch (NoSuchMethodError ignored) {
			//Maven 2
			Set<Artifact> pluginDependentArtifacts = mojoExecution.getMojoDescriptor().getPluginDescriptor().getIntroducedDependencyArtifacts();
			for (Artifact dependency : pluginDependentArtifacts) {
				for (String parent : artifact.getDependencyTrail()) {
					if (parent.contains(dependency.getGroupId() + ":" + dependency.getArtifactId()) && parent.contains(dependency.getBaseVersion())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Work out if an artifact is a JMeter dependency
	 *
	 * @param artifact Artifact to examine
	 * @return true if a Jmeter dependency, false if a plugin dependency.
	 */
	protected boolean isArtifactAJMeterDependency(Artifact artifact) {
		for (String dependency : artifact.getDependencyTrail()) {
			if (dependency.contains("org.apache.jmeter")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Search the list of plugin artifacts for an artifact with a specific name
	 *
	 * @param artifactName
	 * @return
	 * @throws MojoExecutionException
	 */
	protected Artifact getArtifactNamed(String artifactName) throws MojoExecutionException {
		for (Artifact artifact : pluginArtifacts) {
			if (artifact.getArtifactId().equals(artifactName)) {
				return artifact;
			}
		}
		throw new MojoExecutionException("Unable to find artifact '" + artifactName + "'!");
	}

	/**
	 * Generate the initial JMeter Arguments array that is used to create the command line that we pass to JMeter.
	 *
	 * @throws MojoExecutionException
	 */
	protected void initialiseJMeterArgumentsArray(boolean disableGUI) throws MojoExecutionException {
		testArgs = new JMeterArgumentsArray(disableGUI, workDir.getAbsolutePath());
		testArgs.setResultsDirectory(resultsDir.getAbsolutePath());
		testArgs.setResultFileOutputFormatIsCSV(resultsOutputIsCSVFormat);
		if (testResultsTimestamp) {
			testArgs.setResultsTimestamp(testResultsTimestamp);
			testArgs.appendTimestamp(appendResultsTimestamp);
			if (isSet(resultsFileNameDateFormat)) {
				try {
					testArgs.setResultsFileNameDateFormat(DateTimeFormat.forPattern(resultsFileNameDateFormat));
				} catch (Exception ex) {
					getLog().error("'" + resultsFileNameDateFormat + "' is an invalid DateTimeFormat.  Defaulting to Standard ISO_8601.");
				}
			}
		}
		testArgs.setProxyConfig(proxyConfig);
		testArgs.setACustomPropertiesFile(customPropertiesFile);
		testArgs.setLogRootOverride(overrideRootLogLevel);
		testArgs.setLogsDirectory(logsDir.getAbsolutePath());
	}

	protected void setJMeterResultFileFormat() {
		if (resultsFileFormat.toLowerCase().equals("csv")) {
			propertiesJMeter.put("jmeter.save.saveservice.output_format", "csv");
			resultsOutputIsCSVFormat = true;
		} else {
			propertiesJMeter.put("jmeter.save.saveservice.output_format", "xml");
			resultsOutputIsCSVFormat = false;
		}
	}

	protected void configureAdvancedLogging() throws MojoFailureException {
		File advancedLoggingSetting = new File(testFilesDirectory + File.separator + logConfigFilename);
		if (advancedLoggingSetting.exists()) {
			try {
				copyFile(advancedLoggingSetting, new File(binDir + File.separator + logConfigFilename));
			} catch (IOException ex) {
				throw new MojoFailureException(ex.getMessage());
			}
			propertiesJMeter.put("log_config", logConfigFilename);
		}
	}
}
