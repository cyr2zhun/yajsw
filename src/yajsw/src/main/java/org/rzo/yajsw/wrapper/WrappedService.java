/*******************************************************************************
 * Copyright  2015 rzorzorzo@users.sf.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.rzo.yajsw.wrapper;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Logger;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ConfigurationUtils;
import org.rzo.yajsw.Constants;
import org.rzo.yajsw.boot.WrapperServiceBooter;
import org.rzo.yajsw.cache.Cache;
import org.rzo.yajsw.config.YajswConfigurationImpl;
import org.rzo.yajsw.os.JavaHome;
import org.rzo.yajsw.os.OperatingSystem;
import org.rzo.yajsw.os.Service;
import org.rzo.yajsw.os.ms.win.w32.WindowsXPService;
import org.rzo.yajsw.os.posix.PosixService;
import org.rzo.yajsw.util.Utils;

import com.sun.jna.PlatformEx;

import io.netty.util.internal.logging.SimpleLoggerFactory;

public class WrappedService
{

	/** The _config. */
	YajswConfigurationImpl _config;

	/** The _debug. */
	int _debug = 3;

	/** The Constant PATHSEP. */
	static final String PATHSEP = System.getProperty("path.separator");

	/** The _log. */
	Logger _log = Logger.getLogger(this.getClass().getName());

	/** The _local configuration. */
	protected Configuration _localConfiguration = new BaseConfiguration();

	/** The _use system properties. */
	boolean _useSystemProperties = true;

	Service _osService;

	Cache _cache = null;

	volatile boolean _init = false;

	private List<String> _confFilesList;

	private List _cliProperties;

	/*
	 * (non-Javadoc)
	 * 
	 * @see jnacontrib.win32.Win32Service#init()
	 */
	public void init()
	{
		if (_init)
			return;
		Map utils = new HashMap();
		utils.put("util", new Utils(this));
		if (_confFilesList != null && !_confFilesList.isEmpty()
				&& _localConfiguration != null
				&& !_localConfiguration.containsKey("wrapper.config"))
		{
			_localConfiguration.setProperty("wrapper.config",
					_confFilesList.get(0));
		}

		_config = new YajswConfigurationImpl(_localConfiguration,
				_useSystemProperties, utils);
		if (_confFilesList != null && !_confFilesList.isEmpty())
		{
			_config.setProperty("wrapperx.config", _confFilesList);
		}

		int umask = Utils.parseOctal(_config.getString("wrapper.umask", null));
		if (umask != -1)
			OperatingSystem.instance().processManagerInstance().umask(umask);

		if (!_config.isLocalFile())
			if (_cache == null)
			{
				_cache = new Cache();
				_cache.load(_config);
			}

		_debug = _config.getBoolean("wrapper.debug", false) ? _config.getInt(
				"wrapper.debug.level", 3) : 0;
		_osService = OperatingSystem.instance().serviceManagerInstance()
				.createService();
		_osService.setLogger(_log);
		_osService.setName(_config.getString("wrapper.ntservice.name"));
		_osService.setCliProperties(getCliProperties());

		String displayName = _config.getString("wrapper.ntservice.displayname");
		String description = _config.getString("wrapper.ntservice.description");

		Set dependeciesSet = new HashSet();
		for (Iterator it = _config.getKeys("wrapper.ntservice.dependency"); it
				.hasNext();)
		{
			String value = _config.getString((String) it.next());
			if (value != null && value.length() > 0)
				dependeciesSet.add(value);
		}
		String[] dependencies = new String[dependeciesSet.size()];
		int i = 0;
		for (Iterator it = dependeciesSet.iterator(); it.hasNext(); i++)
			dependencies[i] = (String) it.next();

		dependeciesSet = new HashSet();
		for (Iterator it = _config.getKeys("wrapper.ntservice.stop_dependency"); it
				.hasNext();)
		{
			String value = _config.getString((String) it.next());
			if (value != null && value.length() > 0)
				dependeciesSet.add(value);
		}
		String[] stopDependencies = new String[dependeciesSet.size()];
		i = 0;
		for (Iterator it = dependeciesSet.iterator(); it.hasNext(); i++)
			stopDependencies[i] = (String) it.next();

		String account = _config.getString("wrapper.ntservice.account");
		String password = _config.getString("wrapper.ntservice.password");
		String startType = _config.getString("wrapper.ntservice.starttype",
				Constants.DEFAULT_SERVICE_START_TYPE);

		String[] command = getCommand();
		_osService.setAccount(account);
		_osService.setCommand(command);
		_osService.setDependencies(dependencies);
		_osService.setStopDependencies(dependencies);
		_osService.setDescription(description);
		_osService.setDisplayName(displayName);
		_osService.setPassword(password);
		_osService.setConfig(_config);
		_osService.setStartType(startType);
		_osService.setFailureActions(OperatingSystem.instance()
				.getServiceFailureActions(_config));
		_osService.init();

		_init = true;

	}

	/**
	 * Install.
	 * 
	 * @return true, if successful
	 */
	public boolean install()
	{
		int i = 0;
		return _osService.install();
	}

	public boolean uninstall()
	{
		if (_osService == null)
			return false;
		stop();
		return _osService.uninstall();
	}

	/**
	 * Adds the classpath from manifest.
	 * 
	 * @param classpath
	 *            the classpath
	 * @param f
	 *            the f
	 */
	protected void addClasspathFromManifest(ArrayList classpath, File f)
	{
		try
		{

			Manifest manifest = new JarFile(f).getManifest();
			Attributes attr = manifest.getMainAttributes();

			String cl = attr.getValue("Class-Path");
			if (cl == null)
				return;
			String[] clArr = cl.split(" ");
			for (int i = 0; i < clArr.length; i++)
			{
				String file = clArr[i];
				File myFile = new File(f.getParent(), file).getCanonicalFile();
				// System.out.println("adding classpath from manifest "+file);
				if (myFile.exists() && !classpath.contains(myFile))
					classpath.add(myFile);
				else if (!myFile.exists())
					System.out.println("file not found " + myFile);
			}
		}
		catch (MalformedURLException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Wrapper options.
	 * 
	 * @return the string
	 */
	protected ArrayList wrapperOptions()
	{
		int additionalCount = 1;
		boolean xrsFound = false;
		ArrayList result = new ArrayList();

		// first add lookup vars eg ${lookup}
		if (_config.getBoolean("wrapper.save_interpolated", true))
		{

			for (Entry<String, String> e : _config.getEnvLookupSet().entrySet())
			{
				if (!e.getKey().contains("password"))
					result.add(Utils.getDOption(e.getKey(), e.getValue()));
			}
			for (Iterator it = _config.getLookupSet().iterator(); it.hasNext();)
			{
				String key = (String) it.next();
				if (key.contains("password"))
					continue;
				if (!"wrapper.working.dir".equals(key))
					result.add(Utils.getDOption(key, _config.getString(key)));
			}
		}

		for (Iterator it = _config.getSystemConfiguration().getKeys("wrapper"); it
				.hasNext();)
		{
			String key = (String) it.next();
			if (key.equals("wrapper.config"))
				result.add(Utils.getDOption(key, _config.getCachedPath()));
			else
				result.add(Utils.getDOption(key, _config.getString(key)));
			if (key.startsWith("wrapper.additional."))
			{
				additionalCount++;
				if (_config.getString(key).equals("-Xrs"))
					xrsFound = true;
			}
		}

		// if we have a list of configurations
		if (_confFilesList != null && _confFilesList.size() > 1)
		{
			String confList = "";
			// for each configuration
			for (int i = 0; i < _confFilesList.size(); i++)
			{
				// load it
				Configuration localConfiguration = ConfigurationUtils
						.cloneConfiguration(_localConfiguration);
				String conf = _confFilesList.get(i);
				localConfiguration.setProperty("wrapper.config", conf);
				Map utils = new HashMap();
				utils.put("util", new Utils(this));
				YajswConfigurationImpl config = new YajswConfigurationImpl(
						localConfiguration, _useSystemProperties, utils);
				Cache cache = null;
				// check if we need to download files from remote location
				if (!config.isLocalFile())
				{
					cache = new Cache();
					cache.load(config);
				}
				// add configuration file name to list
				confList += config.getCachedPath();
				if (i < (_confFilesList.size() - 1))
					confList += ",";
			}
			// TODO lookup is currently done only for first configuration
			// add list to service parameters
			result.add(Utils.getDOption("wrapperx.config", confList));
		}

		if (!xrsFound)
		{
			result.add("-Dwrapper.additional." + (additionalCount) + "x=-Xrs");
		}
		if (_config.getString("wrapper.stop.conf") != null)
			try
			{
				result.add(Utils.getDOption("wrapper.stop.conf", new File(
						_config.getString("wrapper.stop.conf"))
						.getCanonicalPath()));
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		if (_config.getString("wrapper.groovy") != null)
			try
			{
				result.add(Utils.getDOption("wrapper.groovy",
						new File(_config.getString("wrapper.groovy"))
								.getCanonicalPath()));
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		String tmpDir = _config.getString("wrapper.tmp.path", null);
		if (tmpDir == null)
			tmpDir = System.getProperty("java.io.tmpdir");
		File tmpFile = new File(tmpDir);
		result.add(Utils.getDOption("jna_tmpdir", tmpFile.getAbsolutePath()));

		return result;
	}

	// test main
	/**
	 * The main method.
	 * 
	 * @param args
	 *            the arguments
	 */
	public Configuration getLocalConfiguration()
	{

		return _localConfiguration;
	}

	public void setLocalConfiguration(Configuration config)
	{
		_localConfiguration = config;
	}

	public boolean isInstalled()
	{
		if (_osService != null)
			return _osService.isInstalled(state());
		return false;
	}

	public boolean isRunning()
	{
		if (_osService != null)
			return _osService.isRunning(state());
		return false;
	}

	public boolean isStarting()
	{
		if (_osService != null)
			return _osService.isStarting(state());
		return false;
	}

	public void setUseSystemProperties(boolean useSystem)
	{
		_useSystemProperties = useSystem;
	}

	public boolean start()
	{
		// System.out.println("OsService.start() "+_osService);
		if (_osService != null)
			return _osService.start();
		return false;
	}

	public boolean stop()
	{
		if (_osService != null)
			return _osService.stop();
		return false;
	}

	/**
	 * Gets the java command.
	 * 
	 * @return the java command
	 */
	String[] getCommand()
	{
		// interpolate all configuration properties, so that we may evaluate all
		// required environment variables
		for (Iterator it = _config.getKeys(); it.hasNext();)
		{
			String key = (String) it.next();
			if (key.startsWith("wrapper."))
				try
				{
					_config.getList(key);
				}
				catch (Exception ex)
				{

				}
		}
		JavaHome javaHome = OperatingSystem.instance().getJavaHome(_config);
		javaHome.setLogger(
				SimpleLoggerFactory.getInstance(this.getClass().getName()),
				_debug);
		String java = javaHome.findJava(
				_config.getString("wrapper.ntservice.java.command",
						_config.getString("wrapper.java.command")),
				_config.getString("wrapper.ntservice.java.customProcName"));
		if (java == null)
		{
			_log.warning("no java exe found. check configuration file. -> using default \"java\"");
			java = "java";
		}
		ArrayList jvmOptions = jvmOptions();
		ArrayList wrapperOptions = wrapperOptions();
		String mainClass = getServiceMainClass();
		String workingDir = _config.getString("wrapper.working.dir");
		if (workingDir != null)
		{
			File wd = new File(workingDir);
			if (!wd.exists() || !wd.isDirectory())
				_log.warning("working directory " + workingDir + " not found");
		}
		String[] result = new String[jvmOptions.size() + wrapperOptions.size()
				+ 2];
		result[0] = java;
		result[result.length - 1] = mainClass;
		int i = 1;
		for (Iterator it = jvmOptions.listIterator(); it.hasNext(); i++)
			result[i] = (String) it.next();
		for (Iterator it = wrapperOptions.listIterator(); it.hasNext(); i++)
			result[i] = (String) it.next();
		return result;
	}

	private String getServiceMainClass()
	{
		return WrapperServiceBooter.class.getName();
	}

	/**
	 * Jvm options.
	 * 
	 * @return the string
	 */
	private ArrayList jvmOptions()
	{
		ArrayList result = new ArrayList();
		result.add("-classpath");
		ArrayList classpath = new ArrayList();
		String[] files = _config.getString("java.class.path").split(PATHSEP);
		for (int i = 0; i < files.length; i++)
		{
			File f = null;
			try
			{
				f = new File(files[i]).getCanonicalFile();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (f != null && f.exists()
					&& !classpath.contains(f.getAbsolutePath()))
			{
				// System.out.println("adding classpath :"+f+":");
				classpath.add(f);
				// if (f.getName().endsWith(".jar"))
				// addClasspathFromManifest(classpath, f);
			}
		}
		StringBuffer sb = new StringBuffer();
		for (Iterator it = classpath.iterator(); it.hasNext();)
		{
			String canonicalFileName = null;
			try
			{
				canonicalFileName = ((File) it.next()).getCanonicalPath();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			sb.append(canonicalFileName);
			if (it.hasNext())
				sb.append(PATHSEP);
		}
		String cp = sb.toString();
		if (cp.contains(" "))
			cp = "\"" + cp + "\"";
		result.add(cp);
		result.add("-Xrs");
		result.add("-Dwrapper.service=true");
		String wDir = _config.getString("wrapper.working.dir");
		if (wDir != null)
		{
			File f = new File(wDir);
			if (f.exists() && f.isDirectory())
				try
				{
					result.add(Utils.getDOption("wrapper.working.dir",
							f.getCanonicalPath()));
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
		}
		if (_config.getBoolean("wrapper.ntservice.debug", false))
		{
			result.add("-Xdebug");
			result.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=1044");
		}
		for (Iterator it = _config.getKeys("wrapper.ntservice.additional"); it
				.hasNext();)
		{
			String key = (String) it.next();
			String value = _config.getString(key);
			result.add(value);
		}
		if (verifyNeedIPv4IsPreferred()
				&& !result.contains("-Djava.net.preferIPv4Stack=true"))
		{
			result.add("-Djava.net.preferIPv4Stack=true");
			if (_log != null)
				_log.info("Windows & JVM7: Setting -Djava.net.preferIPv4Stack=true (see java bug 7179799 )");

		}
		return result;
	}

	private boolean verifyNeedIPv4IsPreferred()
	{
		boolean isJDK7 = System.getProperty("java.version").startsWith("1.7");
		boolean isWindows = PlatformEx.isWinVista();
		return isWindows && isJDK7;
	}

	public String getServiceName()
	{
		return _config.getString("wrapper.ntservice.name");
	}

	public static void main(String[] args)
	{
		System.setProperty("wrapper.java.app.mainclass", "test.HelloWorld");
		System.setProperty("wrapper.ntservice.name",
				"JavaServiceWrapper_1207751158998");
		System.setProperty("wrapper.filter.trigger.1", "999");
		System.setProperty("wrapper.java.additional.1", "-Xrs");
		System.setProperty("wrapper.on_exit.default", "RESTART");
		WrappedService w = new WrappedService();

		System.out.println("init");
		w.init();
		System.out.println("install");
		w.install();
		for (int i = 0; i < 10; i++)
		{
			System.out.println("start");
			w.start();
			try
			{
				Thread.sleep(10000);
			}
			catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("stop");
			w.stop();
		}
		System.out.println("uninstall");
		w.uninstall();

	}

	public int state()
	{
		if (_osService != null)
			return _osService.state();
		return Service.STATE_UNKNOWN;
	}

	public boolean isInstalled(int state)
	{
		if (_osService == null)
			return false;
		return _osService.isInstalled(state);
	}

	public boolean isRunning(int state)
	{
		if (_osService == null)
			return false;
		return _osService.isRunning(state);
	}

	public boolean isInteractive(int state)
	{
		if (_osService == null)
			return false;
		return _osService.isInteractive(state);
	}

	public boolean isAutomatic(int state)
	{
		if (_osService == null)
			return false;
		return _osService.isAutomatic(state);
	}

	public boolean isManual(int state)
	{
		if (_osService == null)
			return false;
		return _osService.isManual(state);
	}

	public boolean isDisabled(int state)
	{
		if (_osService == null)
			return false;
		return _osService.isDisabled(state);
	}

	public boolean isPaused(int state)
	{
		if (_osService == null)
			return false;
		return _osService.isPaused(state);
	}

	public boolean isStateUnknown(int state)
	{
		if (_osService == null)
			return false;
		return _osService.isStateUnknown(state);
	}

	public void stopProcess()
	{
		if (_osService instanceof PosixService)
		{
			((PosixService) _osService).stopProcess();
		}
		else
			stop();
	}

	public void startProcess()
	{
		if (_osService instanceof PosixService)
		{
			((PosixService) _osService).startProcess();
		}
		else
			start();
	}

	public String getConfigLocalPath()
	{
		return _config.getCachedPath(false);
	}

	public void setConfFilesList(List<String> confFiles)
	{
		_confFilesList = confFiles;
	}

	public boolean requiresElevate()
	{
		if (_osService != null && _osService instanceof WindowsXPService)
			return ((WindowsXPService) _osService).requestElevation();
		return false;
	}

	public void setCliProperties(List properties) {
		System.out.println("setCliProperties "+properties);
		_cliProperties = properties;
	}
	
	public List getCliProperties()
	{
		return _cliProperties;
	}

}
