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

package org.rzo.yajsw.app;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.logging.LogFactory;
import org.rzo.yajsw.Constants;
import org.rzo.yajsw.YajswVersion;
import org.rzo.yajsw.action.Action;
import org.rzo.yajsw.action.ActionFactory;
import org.rzo.yajsw.config.ConfigUtils;
import org.rzo.yajsw.config.YajswConfiguration;
import org.rzo.yajsw.config.YajswConfigurationImpl;
import org.rzo.yajsw.controller.Message;
import org.rzo.yajsw.controller.jvm.MessageDecoder;
import org.rzo.yajsw.controller.jvm.MessageEncoder;
import org.rzo.yajsw.io.CyclicBufferFileInputStream;
import org.rzo.yajsw.io.CyclicBufferFilePrintStream;
import org.rzo.yajsw.io.TeeInputStream;
import org.rzo.yajsw.io.TeeOutputStream;
import org.rzo.yajsw.nettyutils.SystemOutLoggingFilter;
import org.rzo.yajsw.os.OperatingSystem;
import org.rzo.yajsw.script.Script;
import org.rzo.yajsw.script.ScriptFactory;
import org.rzo.yajsw.util.Cycler;
import org.rzo.yajsw.util.DaemonThreadFactory;
import org.rzo.yajsw.util.Utils;
import org.rzo.yajsw.wrapper.AlphaNumericComparator;

import com.sun.management.HotSpotDiagnosticMXBean;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.SimpleLoggerFactory;

// TODO: Auto-generated Javadoc
/**
 * The Class WrapperManagerImpl.
 */
public class WrapperManagerImpl implements WrapperManager, Constants,
		WrapperManagerImplMBean
{
	
	/** The _port. */
	int _port = DEFAULT_PORT;

	/** The _debug. */
	int _debug = 3;
	boolean _debugComm = false;

	/** The log. */
	final InternalLogger log = SimpleLoggerFactory
			.getInstance("WrapperManager");

	/** The _started. */
	volatile boolean _started = false;

	/** The _key. */
	String _key;

	/** The _ping interval. */
	int _pingInterval = 5000;

	/** The connector. */
	Bootstrap connector;

	/** The _session. */
	volatile Channel _session;

	/** The _stopping. */
	volatile boolean _stopping = false;

	/** The _config. */
	Configuration _config;

	/** The instance. */
	static WrapperManagerImpl instance;

	/** The _exit code. */
	int _exitCode = 0;

	/** The main method. */
	Method mainMethod = null;
	String mainClassName = null;
	String module = null;
	List<String> modulePath = null;

	/** The main method args. */
	String[] mainMethodArgs = null;

	/** The exit on main terminate. */
	int exitOnMainTerminate = -1;
	private int exitOnException = 999;

	/** The _my pid. */
	volatile int _myPid = -1;

	boolean _externalStop = false;

	String _groovyScript = null;

	Cycler _pinger;

	OutputStream _outStream;
	OutputStream _errStream;

	volatile boolean _appearHanging = false;

	boolean _overrideStdErr = false;

	boolean _haltAppOnWrapper = false;

	Lock _lock = new ReentrantLock();
	Condition _connectEnd = _lock.newCondition();
	Executor executor = Executors.newCachedThreadPool(new DaemonThreadFactory(
			"yajsw-pool", Thread.MAX_PRIORITY));

	long _startupTimeout = 0;

	volatile String shutdownScript = null;

	Properties _properties;

	volatile boolean _dumpingHeap = false;

	volatile String _stopReason = null;

	float currentPercentHeap = -1;
	long minorGCDuration = -1;
	long fullGCDuration = -1;
	final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
	final long maxHeap = memoryBean.getHeapMemoryUsage().getMax();
	final Object _heapDataLock = new Object();
	boolean _sendHeapData = false;

	private long lastMinorCollectionCount;
	private long lastMinorCollectionTime;

	private long lastFullCollectionCount;
	private long lastFullCollectionTime;

	Long usedHeap = null;
	Long timeMinorGC = null;
	Long timeFullGC = null;
	Long lastUsedHeap = null;

	GarbageCollectorMXBean minorGCBean;
	GarbageCollectorMXBean fullGCBean;

	MessageFormat gcFormat = null;

	boolean _initGCBeans = false;

	volatile Runnable _shutdownListener;
	
	volatile long _pingTime = System.currentTimeMillis();

	BlockingQueue<String> _keystoreResult = new ArrayBlockingQueue(1);
	
	private boolean _wrapperPingCheckAck = false;
	
	Object moduleLayer = null;
	
	public void setModuleLayer(Object layer)
	{
		this.moduleLayer = layer;
	}
	
	public Object getModuleLayer()
	{
		return this.moduleLayer;
	}
	
	private String getSystemProperty(String key)
	{
		String result = System.getProperty(key);
		if (result != null && result.contains("\""))
			result.replaceAll("\"", "");
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.rzo.yajsw.WrapperManager#init(java.lang.String[],
	 * java.lang.ClassLoader)
	 */
	public void init(String[] args, ClassLoader wrapperClassLoader)
	{
		/*
		 * System.out.println(Scheduler.class.getClassLoader());
		 * System.out.println(Configuration.class.getClassLoader());
		 * System.out.flush(); try { Thread.sleep(10000); } catch
		 * (InterruptedException e1) { // TODO Auto-generated catch block
		 * e1.printStackTrace(); }
		 */
		System.out.println("YAJSW: " + YajswVersion.YAJSW_VERSION);
		System.out.println("OS   : " + YajswVersion.OS_VERSION);
		System.out.println("JVM  : " + YajswVersion.JAVA_VERSION);
		// set commons logging for vfs -> avoid using default java logger
		ClassLoader currentClassLoader = Thread.currentThread()
				.getContextClassLoader();
		Thread.currentThread().setContextClassLoader(wrapperClassLoader);
		// String commonsLog =
		// getSystemProperty("org.apache.commons.logging.Log");
		// System.setProperty("org.apache.commons.logging.Log",
		// "org.apache.commons.logging.impl.SimpleLog");
		LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log",
				"org.apache.commons.logging.impl.SimpleLog");
		instance = this;
		String outFile = getSystemProperty("wrapper.teeName");
		String outPath = getSystemProperty("wrapper.tmp.path");
		if (outPath != null)
			outPath = outPath.replaceAll("\"", "");
		String vStr = getSystemProperty("wrapper.console.visible");
		boolean visible = vStr != null && vStr.equals("true");
		if (outFile != null)
		{
			teeSystemStreams(outFile, outPath, visible);
		}

		String preScript = getSystemProperty("wrapper.app.pre.script");
		if (preScript != null & !"".equals(preScript))
			try
			{
				if (_debug > 2)
					System.out.println("wrapped process: executing pre script "
							+ preScript);
				Script script = ScriptFactory.createScript(preScript,
						"wrapper.app.pre.script", null, new String[0], log, 0,
						"UTF-8", false, _debug, 1);
				if (script != null)
					script.execute();
				else
					System.out
							.println("wrapped process: executing pre script error: could not open script");
			}
			catch (Throwable ex)
			{
				ex.printStackTrace();
			}

		YajswConfigurationImpl config = new YajswConfigurationImpl();
		// config.setDebug(false);
		config.init();
		boolean dbg = config.getBoolean("wrapper.debug", false);
		int debugLevel = config.getInt("wrapper.debug.level", 3);
		_debug = dbg ? debugLevel : 0;
		_debugComm = config.getBoolean("wrapper.debug.comm", false);
		logJavaInfo(args);

		try
		{
			_overrideStdErr = config.getBoolean("wrapper.java.dump.override",
					false);
		}
		catch (Exception ex)
		{
			System.out.println("Error getting wrapper.java.dump.override "
					+ ex.getMessage());
		}
		try
		{
			mainClassName = config
					.getString("wrapper.java.app.mainclass");
			String jarName = config.getString("wrapper.java.app.jar");
			Configuration mp = config.subset("wrapper.java.app.module-path");
			if (mp != null && !mp.isEmpty())
			{
				modulePath = new ArrayList<String>();
				Iterator<String> it = mp.getKeys();
				for (;it.hasNext();)
				{
					modulePath.add(mp.getString(it.next()));
				}
			}
			//module = config.getString("wrapper.java.app.module", null);
			String groovyScript = config.getString("wrapper.groovy");
			if (mainClassName == null && jarName == null
					&& groovyScript == null && module == null)
				mainClassName = config.getString("wrapper.app.parameter.1");
			if (_debug > 1)
				System.out.println("mainClass/jar/script: " + mainClassName
						+ "/" + jarName + "/" + groovyScript);
			if (jarName == null && mainClassName == null
					&& groovyScript == null && module == null)
			{
				System.out
						.println("missing main class name or jar file or groovy file or module. please check configuration");
				return;
			}
			if (jarName != null)
			{
				mainMethod = loadJar(jarName);
			}
			else if (mainClassName != null)
				try
				{
					Class cls = ClassLoader.getSystemClassLoader().loadClass(
							mainClassName);// Class.forName(mainClassName,
					// currentContext);
					mainMethod = cls.getMethod("main",
							new Class[] { String[].class });
				}
				catch (Exception e)
				{
					System.out.println("error finding main method in class: "
							+ mainClassName + " : " + e.getMessage());
					// log.throwing(WrapperMain.class.getName(), "main", e);
					e.printStackTrace();
					return;
				}
			else
				_groovyScript = groovyScript;

			String stopConfig = config.getString("wrapper.stop.conf");
			if (stopConfig != null)
			{
				File f = new File(stopConfig);
				_externalStop = true;
			}
			if (_debug > 1)
				System.out.println("external stop " + _externalStop);

			exitOnMainTerminate = config.getInt(
					"wrapper.exit_on_main_terminate",
					DEFAULT_EXIT_ON_MAIN_TERMINATE);

			exitOnException = config.getInt("wrapper.exit_on_main_exception",
					DEFAULT_EXIT_ON_MAIN_EXCEPTION);

			mainMethodArgs = getAppParam((Configuration) config);
			setConfiguration((Configuration) config);
			if (_config.getBoolean("wrapper.java.jmx", false))
				registerMBean(config);

			String control = _config.getString("wrapper.control",
					DEFAULT_CONTROL);
			if ("TIGHT".equals(control) || "APPLICATION".equals(control))
				_haltAppOnWrapper = true;

			setKey(_config.getString("wrapper.key"));
			// setDebug(true);
			setPort(_config.getInt("wrapper.port"));
			setPingInterval(_config.getInt("wrapper.ping.interval",
					Constants.DEFAULT_PING_INTERVAL));

			_startupTimeout = _config.getInt("wrapper.startup.timeout",
					DEFAULT_STARTUP_TIMEOUT) * 1000;

			_wrapperPingCheckAck = _config.getBoolean("wrapper.ping.check_ack", false);
			shutdownScript = _config.getString("wrapper.app.shutdown.script",
					null);
			if (shutdownScript != null && !"".equals(shutdownScript))
			{
				Runtime.getRuntime().addShutdownHook(new Thread()
				{
					public void run()
					{
						executeShutdownScript();
					}
				});

			}

			try
			{
				_sendHeapData = config.getBoolean(
						"wrapper.java.monitor.gc.restart", false)
						|| config
								.getBoolean("wrapper.java.monitor.heap", false);
			}
			catch (Exception ex)
			{
				System.out
						.println("error reading wrapper.java.monitor.*.restart");
			}

			monitorDeadLocks(config);
			monitorHeap(config);
			monitorGc(config);

			if (_debug > 1)
				System.out.println("terminated WrapperManager.init()");
			// if (commonsLog != null)
			// System.setProperty("org.apache.commons.logging.Log", commonsLog);
			LogFactory.getFactory().removeAttribute(
					"org.apache.commons.logging.Log");

			Utils.verifyIPv4IsPreferred(null);
		}
		catch (Throwable ex)
		{
			ex.printStackTrace();
		}
		if (currentClassLoader != null)
			Thread.currentThread().setContextClassLoader(currentClassLoader);

	}

	private void monitorDeadLocks(YajswConfigurationImpl config)
	{
		if (config.getBoolean("wrapper.java.monitor.deadlock", false))
		{
			final long cycle = config.getLong(
					"wrapper.java.monitor.deadlock.interval", 30) * 1000;
			final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
			if (_debug > 2)
				System.out.println("monitor deadlock: start");
			executor.execute(new Runnable()
			{

				public void run()
				{
					while (!_stopping)
					{
						long[] ids = bean.findDeadlockedThreads();
						if (ids != null && ids.length > 0)
						{
							System.err
									.println("wrapper.java.monitor.deadlock: DEADLOCK IN THREADS: ");
							threadDump(ids);
							// exit loop once we find a deadlock
							return;
						}
						try
						{
							Thread.sleep(cycle);
						}
						catch (InterruptedException e)
						{
							return;
						}
					}

				}

			});

		}
	}

	volatile boolean _heapNotified = false;

	private void monitorHeap(YajswConfigurationImpl config)
	{
		if (config.getBoolean("wrapper.java.monitor.heap", false))
		{
			final long cycle = config.getLong(
					"wrapper.java.monitor.heap.interval", 30) * 1000;
			final int thresholdPercent = config.getInt(
					"wrapper.java.monitor.heap.threshold.percent", 95);
			if (_debug > 2)
				System.out.println("monitor heap: start");
			executor.execute(new Runnable()
			{

				public void run()
				{
					while (!_stopping)
					{
						synchronized (_heapDataLock)
						{
							// currentPercentHeap = ((float)
							// memoryBean.getHeapMemoryUsage().getUsed() /
							// maxHeap) * 100;
							long usedMem = memoryBean.getHeapMemoryUsage()
									.getUsed();
							currentPercentHeap = (float) ((((double) usedMem) / maxHeap) * 100d);
							if (currentPercentHeap > thresholdPercent)
							{
								if (!_heapNotified)
								{
									// System.err.println("wrapper.java.monitor.heap: HEAP SIZE EXCEEDS THRESHOLD: "
									// +
									// memoryBean.getHeapMemoryUsage().getUsed()
									// + "/" + maxHeap);
									System.err
											.println("wrapper.java.monitor.heap: HEAP SIZE EXCEEDS THRESHOLD: "
													+ usedMem
													+ "/"
													+ maxHeap
													+ " => "
													+ currentPercentHeap + "%");
									_heapNotified = true;
								}
							}
							else if (_heapNotified)
							{
								/*
								 * System.err .println(
								 * "wrapper.java.monitor.heap: HEAP SIZE OK: " +
								 * memoryBean .getHeapMemoryUsage() .getUsed() +
								 * "/" + maxHeap);
								 */
								System.err
										.println("wrapper.java.monitor.heap: HEAP SIZE OK: "
												+ usedMem
												+ "/"
												+ maxHeap
												+ " => "
												+ currentPercentHeap
												+ "%");

								_heapNotified = false;

							}
						}
						try
						{
							Thread.sleep(cycle);
						}
						catch (InterruptedException e)
						{
							return;
						}
					}

				}

			});

		}
	}

	private void monitorGc(YajswConfigurationImpl config)
	{
		initGCBeans();
		String mFormat = config.getString("wrapper.java.monitor.gc", null);
		boolean gcRestart = config.getBoolean(
				"wrapper.java.monitor.gc.restart", false);
		if (mFormat != null || gcRestart)
			try
			{
				if (_debug > 2)
					System.out.println("monitor GC: " + mFormat);
				if (mFormat != null)
					gcFormat = new MessageFormat(mFormat);
				final long cycle = config.getLong(
						"wrapper.java.monitor.gc.interval", 1) * 1000;

				if (_debug > 2)
				{
					System.out.println("monitor gc: minorGCBean/fullGCBean: "
							+ minorGCBean.getName() + "/"
							+ fullGCBean.getName());

					System.out.println("monitor gc: start cycle " + cycle
							+ "ms");
				}
				executor.execute(new Runnable()
				{
					public void run()
					{
						if (minorGCBean == null)
						{
							System.err
									.println("monitor gc: could not find minorGCBean -> abort monitor");
							return;
						}
						if (fullGCBean == null)
						{
							System.err
									.println("monitor gc: could not find fullGCBean -> abort monitor");
							return;
						}
						try
						{
							while (!_stopping)
							{
								getGCData();
								// Sleep a little bit before the next poll
								Thread.sleep(cycle);
							}
						}
						catch (Exception ex)
						{
							// Do nothing except exit this thread
							ex.printStackTrace();
						}
						System.err.println("monitor gc: end");

					}
				});
			}
			catch (Exception ex)
			{
				System.err.println("monitor gc: exception: " + ex);
				ex.printStackTrace();
			}
	}

	private void initGCBeans()
	{
		if (_initGCBeans)
			return;
		GarbageCollectorMXBean minorGCBeanX = null;
		GarbageCollectorMXBean fullGCBeanX = null;

		try
		{
			List<GarbageCollectorMXBean> gcMBeans = ManagementFactory
					.getGarbageCollectorMXBeans();
			for (GarbageCollectorMXBean gcBean : gcMBeans)
			{
				if (gcBean.getName().toLowerCase().contains("copy"))
				{
					minorGCBeanX = gcBean;
				}
				else if ("ParNew".equals(gcBean.getName()))
				{
					minorGCBeanX = gcBean;
				}
				else if (gcBean.getName().toLowerCase().contains("scavenge"))
				{
					minorGCBeanX = gcBean;
				}
				else if (gcBean.getName().toLowerCase().contains("marksweep"))
				{
					fullGCBeanX = gcBean;
				}
				else if (gcBean.getName().toLowerCase().contains("young"))
				{
					minorGCBeanX = gcBean;
				}
				else if (gcBean.getName().toLowerCase().contains("old"))
				{
					fullGCBeanX = gcBean;
				}
				else
				{
					System.err
							.println("Unable to classify GarbageCollectorMXBean ["
									+ gcBean.getName() + "]");
				}
			}
		}
		catch (Throwable e)
		{
			System.out.println("error getting GC beans");
		}
		minorGCBean = minorGCBeanX;
		fullGCBean = fullGCBeanX;
		_initGCBeans = true;

	}

	private void getGCData()
	{
		initGCBeans();
		if (minorGCBean == null || fullGCBean == null)
			return;
		if (minorGCBean.getCollectionCount() != lastMinorCollectionCount)
		{
			long diffCount = minorGCBean.getCollectionCount()
					- lastMinorCollectionCount;
			long diffTime = minorGCBean.getCollectionTime()
					- lastMinorCollectionTime;
			if (diffCount != 0 && diffCount != 1)
				timeMinorGC = diffTime / diffCount;
			else
				timeMinorGC = diffTime;
			usedHeap = memoryBean.getHeapMemoryUsage().getUsed();

			lastMinorCollectionCount = minorGCBean.getCollectionCount();
			lastMinorCollectionTime = minorGCBean.getCollectionTime();
		}

		if (fullGCBean.getCollectionCount() != lastFullCollectionCount)
		{
			long diffCount = fullGCBean.getCollectionCount()
					- lastFullCollectionCount;
			long diffTime = fullGCBean.getCollectionTime()
					- lastFullCollectionTime;
			if (diffCount != 0 && diffCount != 1)
				timeFullGC = diffTime / diffCount;
			else
				timeFullGC = diffTime;

			lastFullCollectionCount = fullGCBean.getCollectionCount();
			lastFullCollectionTime = fullGCBean.getCollectionTime();
		}
		usedHeap = memoryBean.getHeapMemoryUsage().getUsed();
		if (usedHeap != null)
		{
			if (timeMinorGC == null)
				timeMinorGC = 0L;
			if (timeFullGC == null)
				timeFullGC = 0L;

			// remember data to be sent by ping
			synchronized (_heapDataLock)
			{
				currentPercentHeap = ((float) usedHeap / maxHeap) * 100;
				if (minorGCDuration == -1)
					minorGCDuration = 0;
				if (fullGCDuration == -1)
					fullGCDuration = 0;
				minorGCDuration += timeMinorGC;
				fullGCDuration += timeFullGC;
			}

			lastUsedHeap = usedHeap;

			if (gcFormat != null)
				System.err.println(gcFormat.format(new Object[] { usedHeap,
						timeMinorGC, timeFullGC }));
			usedHeap = null;
			timeMinorGC = null;
			timeFullGC = null;
		}

	}

	private void registerMBean(YajswConfiguration config)
	{
		MBeanServer server = null;
		ArrayList servers = MBeanServerFactory.findMBeanServer(null);
		try
		{
			if (servers != null && servers.size() > 0)
				server = (MBeanServer) servers.get(0);
			if (server != null)
			{
				String name = config.getString("wrapper.console.title");
				if (name == null)
					name = config.getString("wrapper.ntservice.name");
				if (name == null)
					name = "yajsw.noname";
				name = ObjectName.quote(name);
				ObjectName oName = new ObjectName("Wrapper", "name", name);
				server.registerMBean(this, oName);
				// System.out.println("found mbean server: " +
				// server.toString());
			}
			else
				System.out.println("ERROR: no mbean server found ");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.rzo.yajsw.WrapperManager#getMainMethod()
	 */
	public Method getMainMethod()
	{
		return mainMethod;
	}
	
	public String getMainClassName() 
	{
		return mainClassName;
	}
	
	public String getModule()
	{
		return module;
	}
	
	public List<String> getModulePath()
	{
		return modulePath;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.rzo.yajsw.WrapperManager#getMainMethodArgs()
	 */
	public Object[] getMainMethodArgs()
	{
		return mainMethodArgs;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.rzo.yajsw.WrapperManager#isExitOnMainTerminate()
	 */
	public int getExitOnMainTerminate()
	{
		if (_debug > 2)
			System.out.println("exit on main terminate " + exitOnMainTerminate);
		return exitOnMainTerminate;
	}

	public int getExitOnException()
	{
		if (_debug > 2)
			System.out.println("exit on main exception " + exitOnException);
		return exitOnException;
	}

	/**
	 * Load jar.
	 * 
	 * @param jarName
	 *            the jar name
	 * 
	 * @return the method
	 */
	private Method loadJar(String jarName)
	{
		URL url = null;
		try
		{
			url = new File(jarName).toURI().toURL();
		}
		catch (MalformedURLException e2)
		{
			e2.printStackTrace();
			return null;
		}
		Manifest manifest;
		try
		{
			manifest = new JarFile(new File(jarName)).getManifest();
		}
		catch (IOException e1)
		{
			e1.printStackTrace();
			return null;
		}
		Attributes attr = manifest.getMainAttributes();

		String cl = attr.getValue("Class-Path");
		ClassLoader loader = null;
		/*
		 * if (cl != null) { ArrayList classpath = new ArrayList(); String[]
		 * clArr = cl.split(" "); for (int i = 0; i < clArr.length; i++) {
		 * String file = clArr[i]; File myFile; try { myFile = new File(file);
		 * classpath.add(myFile); } catch (Exception e) { e.printStackTrace(); }
		 * }
		 * 
		 * URL[] urlsArr = new URL[classpath.size()]; int i = 0; for (Iterator
		 * it = classpath.iterator(); it.hasNext(); i++) try { urlsArr[i] =
		 * ((File) it.next()).toURI().toURL(); } catch (Exception e) {
		 * e.printStackTrace(); }
		 * 
		 * loader = new URLClassLoader(urlsArr,
		 * ClassLoader.getSystemClassLoader()); }
		 */if (loader == null)
			loader = ClassLoader.getSystemClassLoader();

		String mainClassName = attr.getValue("Main-Class");
		if (mainClassName == null)
			return null;
		Method mainMethod = null;
		try
		{
			Class cls = loader.loadClass(mainClassName);// cl.loadClass(mainClassName);
			mainMethod = cls.getMethod("main", new Class[] { String[].class });
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			System.err
					.println("ERROR: could not load main method from class/jar: "
							+ mainClassName + "/" + jarName);
		}

		return mainMethod;
	}

	private File createRWfile(String path, String fname)
	{
		File pFile = new File(path);
		try
		{
			if (!pFile.exists())
				pFile.mkdirs();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		File result = new File(path, fname);

		if (OperatingSystem.instance().isPosix())
		{
			String absPath = result.getAbsolutePath();
			//System.out.println("createRWfile " + absPath);
			try
			{
				if (!result.exists())
				{
					result.createNewFile();
				}
				result.deleteOnExit();
				Process p = Runtime.getRuntime().exec("chmod 777 " + absPath);
				int i=0;
				boolean ok = false;
				// wait timeout 20 secs
				while (!ok && i < 200)
				try
				{
					i++;
					Thread.yield();
					p.exitValue();
					ok = true;
				}
				catch(IllegalThreadStateException ex){ Thread.sleep(100);};
				//p.waitFor(); NOTE: we need waitFor(timeout) 
				p.destroy();
			}
			catch (Exception ex)
			{
				ex.printStackTrace(System.out);
			}
		}

		return result;
	}

	/**
	 * Tee system streams.
	 * 
	 * @param outFile
	 *            the out file
	 * @param path
	 *            the path
	 * @param visible
	 *            the visible
	 */
	private void teeSystemStreams(String outFile, String path, boolean visible)
	{
		File fOut = createRWfile(path, "out_" + outFile);
		// if (fOut.exists())
		// fOut.delete();
		fOut.deleteOnExit();
		File fErr = createRWfile(path, "err_" + outFile);
		// if (fErr.exists())
		// fErr.delete();
		fErr.deleteOnExit();
		File fIn = createRWfile(path, "in_" + outFile);
		fIn.deleteOnExit();

		try
		{
			PrintStream wrapperOut = (PrintStream) new CyclicBufferFilePrintStream(
					fOut);
			TeeOutputStream newOut = (TeeOutputStream) new TeeOutputStream();
			newOut.connect(wrapperOut);
			// pipe output to console only if it is visible
			if (visible)
				newOut.connect(System.out);
			_outStream = wrapperOut;
			System.setOut(new PrintStream(newOut));
		}
		catch (Throwable e)
		{
			e.printStackTrace(System.out);
		}

		try
		{

			PrintStream wrapperErr = (PrintStream) new CyclicBufferFilePrintStream(
					fErr);
			TeeOutputStream newErr = (TeeOutputStream) new TeeOutputStream();
			newErr.connect(wrapperErr);
			// pipe output to console only if it is visible
			if (visible)
				newErr.connect(System.err);
			_errStream = newErr;
			System.setErr(new PrintStream(newErr));
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}

		try
		{
			CyclicBufferFileInputStream wrapperIn = new CyclicBufferFileInputStream(
					fIn, "r");
			TeeInputStream newIn = (TeeInputStream) new TeeInputStream();
			newIn.connect(wrapperIn);
			newIn.connect(System.in);
			System.setIn(newIn);
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Log java info.
	 * 
	 * @param args
	 *            the args
	 */
	private void logJavaInfo(String[] args)
	{
		if (_debug > 1)
		{
			System.out.println("APP user name="
					+ getSystemProperty("user.name"));
			System.out.println("APP working dir="
					+ getSystemProperty("user.dir"));
			System.out.println("APP java version="
					+ getSystemProperty("java.version"));
			System.out.println("APP class path="
					+ getSystemProperty("java.class.path"));
			System.out.println("APP library path="
					+ getSystemProperty("java.library.path"));
		}
		String[] files = getSystemProperty("java.class.path").split(
				File.pathSeparator);
		for (int i = 0; i < files.length; i++)
		{
			File f = new File(files[i]);
			if (!f.exists())
				System.err.println("Classpath File not found: " + files[i]);
		}

		if (_debug > 1)
		{
			String argsStr = "Application args: ";
			if (args != null && args.length > 0)
				for (int i = 0; i < args.length; i++)
				{
					argsStr += args[i] + " ";
				}
			else
				argsStr += "no args";
			System.out.println(argsStr);
		}

	}

	/**
	 * Gets the app param.
	 * 
	 * @param config
	 *            the config
	 * 
	 * @return the app param
	 */
	private String[] getAppParam(Configuration config)
	{
		ArrayList result = new ArrayList();
		ArrayList keys = new ArrayList();
		for (Iterator it = config.getKeys("wrapper.app.parameter"); it
				.hasNext();)
		{
			keys.add(it.next());
		}
		Collections.sort(keys, new AlphaNumericComparator());
		for (Iterator it = keys.listIterator(); it.hasNext();)
		{
			String arg = config.getString((String) it.next());
			if (arg != null)
			{
				arg = arg.trim();
				if (arg.length() > 0)
					result.add(arg);
			}
		}
		String[] args = new String[result.size()];
		int i = 0;
		for (Iterator it = result.iterator(); it.hasNext(); i++)
		{
			args[i] = (String) it.next();
		}
		if (_debug > 1)
		{
			System.out.println("args: ");
			for (String arg : args)
				System.out.println(arg);
		}
		return args;
	}

	/**
	 * Sets the configuration.
	 * 
	 * @param config
	 *            the new configuration
	 */
	void setConfiguration(Configuration config)
	{
		_config = config;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.rzo.yajsw.WrapperManager#start()
	 */
	public void start()
	{
		try
		{
			// hack: avoid netty hangs on connect
			if (_config.getBoolean("wrapper.console.pipestreams", false))
			{
				Socket dummy = new Socket("127.0.0.1", _port);
				OutputStream out = dummy.getOutputStream();
				out.close();
				dummy.close();
			}
		}
		catch (Throwable e1)
		{
			if (_debug > 1)
				e1.printStackTrace();
		}

		connector = new Bootstrap();
		EventLoopGroup workerGroup = new OioEventLoopGroup(0, new DaemonThreadFactory(
				"netty", Thread.MAX_PRIORITY));
		// workerGroup.setIoRatio(99);
		connector.group(workerGroup);
		connector.channel(OioSocketChannel.class);

		connector.remoteAddress(new InetSocketAddress("127.0.0.1", _port));
		connector.option(ChannelOption.SO_REUSEADDR, true);
		connector.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10 * 1000);
		connector.option(ChannelOption.TCP_NODELAY, true);

		if (_debugComm)
			connector.handler(new ChannelInitializer<SocketChannel>()
			{

				@Override
				protected void initChannel(SocketChannel ch) throws Exception
				{
					ch.pipeline()
							.addLast(
									new io.netty.channel.ChannelHandler[] {
											new SystemOutLoggingFilter(
													"WrapperManager"),
											new DelimiterBasedFrameDecoder(
													8192, true, Delimiters
															.nulDelimiter()),
											new MessageEncoder(),
											new MessageDecoder(),
											new WrapperHandler() }

							);
				}

			});
		else
			connector.handler(new ChannelInitializer<SocketChannel>()
			{

				@Override
				protected void initChannel(SocketChannel ch) throws Exception
				{
					ch.pipeline().addLast(
							new io.netty.channel.ChannelHandler[] {
									new DelimiterBasedFrameDecoder(8192, true,
											Delimiters.nulDelimiter()),
									new MessageEncoder(), new MessageDecoder(),
									new WrapperHandler() }

					);
				}

			});

		// pinger is a cycler with high priority threads
		// sends ping messages within a ping interval
		_pinger = new Cycler(getPingInterval(), 0,
				Executors.newCachedThreadPool(new DaemonThreadFactory("pinger",
						Thread.MAX_PRIORITY)), new Runnable()
				{
					long start = System.currentTimeMillis();

					public void run()
					{
						ChannelFuture future = null;
						if (_session != null && _session.isActive()
								&& !_stopping && !_appearHanging)
						{
							// if ping has not been acknowledged by the wrapper
							if (_wrapperPingCheckAck && System.currentTimeMillis() - _pingTime > 3*getPingInterval())
							{
								System.out.println("missing ping ack for "+(System.currentTimeMillis() - _pingTime)+ " ms. Closing tcp/ip connection");
								_session.close();
							}
							else
							synchronized (_heapDataLock)
							{
								if (_sendHeapData)
								{
									if (minorGCDuration == -1)
										getGCData();
									future = _session
											.writeAndFlush(new Message(
													Constants.WRAPPER_MSG_PING,
													"" + currentPercentHeap
															+ ";"
															+ minorGCDuration
															+ ";"
															+ fullGCDuration
															+ ";"
															+ lastUsedHeap));
									currentPercentHeap = -1;
									minorGCDuration = -1;
									fullGCDuration = -1;
								}
								else
								{
									future = _session
											.writeAndFlush(new Message(
													Constants.WRAPPER_MSG_PING,
													""));
								}
							}
							try
							{
								if (future != null)
								future.await(10000);
							}
							catch (InterruptedException e)
							{
								e.printStackTrace();
							}
							start = System.currentTimeMillis();
						}
						else if ((_haltAppOnWrapper && (System
								.currentTimeMillis() - start) > _startupTimeout)
								&& !_stopping)
						{
							System.out
									.println("no connection to wrapper during "
											+ (_startupTimeout / 1000)
											+ " seconds -> System.exit(-1)");
							System.out
									.println("if this is due to server overload consider increasing yajsw configuration property wrapper.startup.timeout");
							System.exit(-1);
						}
					}
				});
		_pinger.start();

		// connect
		// handler should process messages in a separate thread

		reconnect();
		/*
		 * try { if (_config.getInt("wrapper.action.port") > 0) { _actionHandler
		 * = new ActionHandler(this, _config); _actionHandler.start(); } } catch
		 * (Exception ex) { log.info("could not start action handler " +
		 * ex.getMessage()); }
		 */

	}

	/**
	 * Reconnect.
	 */
	private void reconnect()
	{
		// try connecting, if we could not sleep then retry
		while (!_started)
		{
			if (_debug > 1)
				// log.fine("connecting to port " + _port);
				System.out.println("connecting to port " + _port);
			final ChannelFuture future1 = connector.connect();
			try
			{
				Thread.yield();
				// System.out.println("connecting wait future ");
				future1.await(10000);
				// System.out.println("after connecting wait future ");
				_started = future1.isSuccess();
			}
			catch (Exception e1)
			{
				// TODO Auto-generated catch block
				System.out.println("error connecting to wrapper: " + e1);
				e1.printStackTrace();
			}
			/*
			 * executor.execute(new Runnable() {
			 * 
			 * public void run() { future1.addListener(new
			 * ChannelFutureListener() { public void
			 * operationComplete(ChannelFuture future) throws Exception {
			 * _lock.lock(); System.out.println("future" + future.isSuccess());
			 * _started = future.isSuccess(); _connectEnd.signal();
			 * _lock.unlock();
			 * 
			 * } }); }
			 * 
			 * });
			 * 
			 * _lock.lock(); try { _connectEnd.await(); } catch
			 * (InterruptedException e1) { // TODO Auto-generated catch block
			 * e1.printStackTrace(); } _lock.unlock();
			 * System.out.println("started "+_started);
			 */

			if (_started)
			{
				if (_debug > 2)
					System.out
							.println("WrapperManager: channel connected, sending key");
				future1.channel().writeAndFlush(
						new Message(Constants.WRAPPER_MSG_KEY, _key));
			}
			else
				try
				{
					if (_debug > 0)
						// log.fine("connection failed -> sleep then retry");
						System.out
								.println("connection failed -> sleep then retry");
					_started = false;
					Thread.sleep(5000);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
		}
	}

	/**
	 * The Class WrapperHandler.
	 */
	class WrapperHandler extends ChannelInboundHandlerAdapter
	{

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception
		{
			if (_debug > 1)
				System.out.println("session closed");
			_started = false;
			if (_session != null)
				try
				{
					_session.close();
				}
				catch (Throwable ex)
				{
					ex.printStackTrace();
				}
			_session = null;
			if (!_stopping)
			{
				if (_debug > 0)
					System.out.println("try reconnect");
				executor.execute(new Runnable()
				{
					public void run()
					{
						try
						{
							Thread.sleep(5000);
						}
						catch (InterruptedException e)
						{
							e.printStackTrace();
						}
						reconnect();
					}
				});
			}
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object message)
		{
			Message msg = (Message) message;
			if (_stopping)
				return;
			Channel session = ctx.channel();
			if (msg.getCode() == Constants.WRAPPER_MSG_STOP)
				try
				{
					System.out.println("wrapper manager received "+msg);
					_stopping = true;

					if (session != null)
						session.close();
					// Thread.sleep(100);
					if (msg.getMessage() != null
							&& msg.getMessage().length() > 0)
						try
						{
							String[] txt = msg.getMessage().split(":");
							if (txt[0].length() > 0)
								_exitCode = Integer.parseInt(txt[0]);
							if (txt.length > 1 && txt[1].length() > 0)
								_stopReason = txt[1];
						}
						catch (Exception ex)
						{
							// DO NOTHING
						}
					executeShutdownScript();
					executeShutdownListener();
					if (!_externalStop)
					{
						System.exit(_exitCode);
					}
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
				}
			else if (msg.getCode() == Constants.WRAPPER_MSG_OKKEY)
			{
				_session = session;
				try
				{
					_myPid = Integer.parseInt(msg.getMessage());
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
				}
			}
			else if (msg.getCode() == Constants.WRAPPER_MSG_THREAD_DUMP)
			{
				threadDump();
			}
			else if (msg.getCode() == Constants.WRAPPER_MSG_GC)
			{
				gc();
			}
			else if (msg.getCode() == Constants.WRAPPER_MSG_DUMP_HEAP)
			{
				dumpHeap(msg.getMessage());
			}
			else if (msg.getCode() == Constants.WRAPPER_MSG_PING)
			{
				_pingTime = System.currentTimeMillis();
			}
			else if (msg.getCode() == Constants.WRAPPER_MSG_KEYSTORE_RESULT)
			{
				System.out.println("received keystore value");
				_keystoreResult.add(msg.getMessage());
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable e)
				throws Exception
		{
			/*
			 * if (_debug) // log.log(Level.FINE, "exceptionCaught",
			 * e.getCause()); e.getCause().printStackTrace();
			 */
			if (_debug > 1)
			{
				Throwable th = e;
				if (th == null)
					return;
				String msg = th.getMessage();
				if (msg == null)
					return;
				System.err.println(msg);
				if (!msg.toLowerCase().contains("connection refused"))
					th.printStackTrace(System.err);
			}

		}

	}

	/**
	 * Gets the port.
	 * 
	 * @return the port
	 */
	int getPort()
	{
		return _port;
	}

	/**
	 * Sets the port.
	 * 
	 * @param port
	 *            the new port
	 */
	public void setPort(int port)
	{
		_port = port;
	}

	/**
	 * Checks if is debug.
	 * 
	 * @return true, if is debug
	 */
	int getDebug()
	{
		return _debug;
	}

	/**
	 * Sets the debug.
	 * 
	 * @param debug
	 *            the new debug
	 */
	void setDebug(int debug)
	{
		_debug = debug;
	}

	/**
	 * Gets the key.
	 * 
	 * @return the key
	 */
	String getKey()
	{
		return _key;
	}

	/**
	 * Sets the key.
	 * 
	 * @param key
	 *            the new key
	 */
	public void setKey(String key)
	{
		_key = key;
	}

	/**
	 * Checks if is started.
	 * 
	 * @return true, if is started
	 */
	boolean isStarted()
	{
		return _started;
	}

	/**
	 * Gets the ping interval.
	 * 
	 * @return the ping interval
	 */
	int getPingInterval()
	{
		return _pingInterval;
	}

	/**
	 * Sets the ping interval.
	 * 
	 * @param pingInterval
	 *            the new ping interval
	 */
	void setPingInterval(int pingInterval)
	{
		_pingInterval = pingInterval * 1000;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.rzo.yajsw.WrapperManager#stop()
	 */
	public void stop()
	{
		if (_session != null)
		{
			while (_session != null && !_stopping)
			{
				_session.writeAndFlush(new Message(Constants.WRAPPER_MSG_STOP,
						null));
				try
				{
					Thread.sleep(500);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		while (true)
		{
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		}
		else
			System.exit(0);

	}

	/**
	 * Stop timer.
	 */
	public void stopTimer()
	{
		if (_session != null)
			while (_session != null && !_stopping)
			{
				_session.writeAndFlush(new Message(
						Constants.WRAPPER_MSG_STOP_TIMER, null));
				try
				{
					Thread.sleep(500);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
	}

	/**
	 * Restart.
	 */
	public void restart()
	{
		if (_session != null)
		{
			while (_session != null && !_stopping)
			{
				Future future = _session.writeAndFlush(new Message(
						Constants.WRAPPER_MSG_RESTART, null));
				try {
					future.await(1000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				try
				{
					Thread.sleep(500);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			while (true)
			{
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		else
			System.out
					.println("not connected to wrapper -> cannot send restart command");
	}

	/**
	 * Instance.
	 * 
	 * @return the wrapper manager impl
	 */
	public static WrapperManagerImpl instance()
	{
		return instance;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.rzo.yajsw.WrapperManager#getPid()
	 */
	public int getPid()
	{
		return _myPid;
	}

	public boolean isControlledByWrapper()
	{
		return _started;
	}

	public boolean isLaunchedAsService()
	{
		return _config.getBoolean("wrapper.service", false);
	}

	public String getGroovyScript()
	{
		return _groovyScript;
	}

	public void threadDump()
	{
		System.out.println("yajsw: thread dump requested");
		threadDump(null);
	}

	public void threadDump(long[] ids)
	{
		Message m = new Message(Constants.WRAPPER_MSG_THREAD_DUMP, null);
		Action a = ActionFactory.getAction(m);
		try
		{
			if (_overrideStdErr)
				a.execute(m, _session, new PrintStream(_errStream), ids);
			else
				a.execute(m, _session, System.err, ids);

		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void gc()
	{
		System.out.println("yajsw: gc requested");
		System.gc();
	}

	public void dumpHeap(final String fileName)
	{
		if (_dumpingHeap)
			return;
		System.out.println("yajsw: dumpHeap requested " + fileName);
		_dumpingHeap = true;
		executor.execute(new Runnable()
		{

			public void run()
			{
				try
				{
					File file;
					if (fileName == null || fileName.length() < 1)
						file = new File(".");
					else
						file = new File(fileName);
					File parent;
					if (file.isDirectory())
					{
						parent = file;
						file = new File(parent, "dump"
								+ "_"
								+ new SimpleDateFormat("yyyy_MM_dd-hh_mm")
										.format(new Date()) + ".hprof");
					}
					else
						parent = file.getParentFile();

					if (!parent.exists())
						parent.mkdirs();

					// com.sun.management.HotSpotDiagnosticMXBean mb =
					// sun.management.ManagementFactory.getDiagnosticMXBean();
					com.sun.management.HotSpotDiagnosticMXBean mb = ManagementFactory
							.newPlatformMXBeanProxy(
									ManagementFactory.getPlatformMBeanServer(),
									HOTSPOT_BEAN_NAME,
									HotSpotDiagnosticMXBean.class);
					File dumpFile = new File(parent, file.getName());
					mb.dumpHeap(dumpFile.getAbsolutePath(), true);
					System.out.println("yajsw: dumpHeap done "
							+ dumpFile.getAbsolutePath());
				}
				catch (Throwable ex)
				{
					ex.printStackTrace();
				}
				finally
				{
					_dumpingHeap = false;
				}
			}

		});

	}

	private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

	public static void main(String[] args) throws MalformedURLException
	{
		String name = "c:/test test/start.jar";
		System.out.println(new File(name).exists());
		System.out.println(new File(new File(name).toURI().getPath()).exists());
		WrapperManager wm = new WrapperManagerImpl();
		((WrapperManagerImpl) wm).loadJar("c:/test test/start.jar");
		synchronized (wm)
		{
			wm.threadDump();
		}
	}

	public boolean isAppearHanging()
	{
		return _appearHanging;
	}

	public void setAppearHanging(boolean appearHanging)
	{
		_appearHanging = appearHanging;
	}

	public void reportServiceStartup()
	{
		boolean reported = false;
		while (!reported && !_stopping)
		{
			if (_session == null || !_session.isActive())
				try
				{
					Thread.sleep(500);
				}
				catch (Exception ex)
				{

				}
			else
			{
				_session.writeAndFlush(new Message(
						Constants.WRAPPER_MSG_SERVICE_STARTUP, null));
				reported = true;
			}
		}
	}

	private void executeShutdownScript()
	{
		if (shutdownScript != null & !"".equals(shutdownScript))
		{
			if (_debug > 1)
				System.out.println("executing shutdown script "
						+ shutdownScript);
			Script script = ScriptFactory.createScript(shutdownScript,
					"wrapper.app.shutdown.script", null, new String[0], log, 0,
					_config.getString("wrapper.script.encoding"),
					_config.getBoolean("wrapper.script.reload", false), _debug,
					1);
			// make sure it is invoked only once
			// stop may be invoke multiple times
			shutdownScript = null;
			if (script != null)
			{
				script.execute();
				if (_debug > 2)
					System.out.println("terminated shutdown script ");
			}
			else
				System.out.println("Error " + shutdownScript
						+ "not initialized");
		}

	}

	private void executeShutdownListener()
	{
		if (_shutdownListener == null)
			return;
		new Thread(_shutdownListener).start();
	}

	public void executeScript(String scriptFileName,
			ClassLoader wrapperClassLoader)
	{
		System.out.println("initializing script " + scriptFileName);
		ClassLoader currentClassLoader = Thread.currentThread()
				.getContextClassLoader();
		Thread.currentThread().setContextClassLoader(wrapperClassLoader);
		try
		{
			Script script = ScriptFactory.createScript(scriptFileName, "",
					null, (String[]) getMainMethodArgs(), null, 0,
					_config.getString("wrapper.script.encoding"),
					_config.getBoolean("wrapper.script.reload", false), _debug,
					1);
			if (script != null)
				script.execute();
			else
				System.err.println("error opening script script: "
						+ scriptFileName);
		}
		catch (Throwable ex)
		{
			System.err.println("error executing script: " + scriptFileName);
			ex.printStackTrace();
		}
		Thread.currentThread().setContextClassLoader(currentClassLoader);

	}

	public void signalStopping(int timeoutHint)
	{
		try
		{
			if (_session == null || !_session.isActive())
			{
				final ChannelFuture future1 = connector.connect();
				future1.await();
				future1.isSuccess();
				_session = future1.channel();
			}
			ChannelFuture wFuture = _session.writeAndFlush(new Message(
					Constants.WRAPPER_MSG_STOP_PENDING, String
							.valueOf(timeoutHint)));
			wFuture.await();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			_session.close();
			_session = null;
		}

	}
	
	public String keystore(String key) throws InterruptedException
	{
		String result = null;
		System.out.println("get keystore for "+key);
		int i = 0;
		while (_session == null && !_stopping && i++ < 10)
			Thread.sleep(1000);
		if (_session != null && !_stopping)
		try
		{
			_session.writeAndFlush(new Message(Constants.WRAPPER_MSG_KEYSTORE,
					key));
			result = _keystoreResult.poll(5000, TimeUnit.MILLISECONDS);			
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		else
			System.out.println("no session");
		return result;

	}

	public Properties getProperties()
	{
		if (_properties == null)
			_properties = ConfigUtils.asProperties(_config);
		return _properties;
	}

	public String getStopReason()
	{
		return _stopReason;
	}

	@Override
	public void setShutdownListener(Runnable listener)
	{
		_shutdownListener = listener;
	}

	@Override
	public void warn(String string) {
		if (_debug > 1)
			System.out.println(string);
	}

}
