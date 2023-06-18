package io.netty.util.internal.logging;

public class SimpleLogger implements InternalLogger {

	public void debug(String arg0) {
		System.out.println(arg0);
	}

	public void debug(String arg0, Throwable arg1) {
		System.out.println(arg0);
		arg1.printStackTrace();
	}

	public void error(String arg0) {
		System.out.println(arg0);
	}

	public void error(String arg0, Throwable arg1) {
		System.out.println(arg0);
		arg1.printStackTrace();
	}

	public void info(String arg0) {
		System.out.println(arg0);
	}

	public void info(String arg0, Throwable arg1) {
		System.out.println(arg0);
		arg1.printStackTrace();
	}

	public boolean isDebugEnabled() {
		return true;
	}

	public boolean isEnabled(InternalLogLevel arg0) {
		return true;
	}

	public boolean isErrorEnabled() {
		return true;
	}

	public boolean isInfoEnabled() {
		return true;
	}

	public boolean isWarnEnabled() {
		return true;
	}

	public void log(InternalLogLevel arg0, String arg1) {
		info(arg1);
	}

	public void log(InternalLogLevel arg0, String arg1, Throwable arg2) {
		info(arg1, arg2);
	}

	public void warn(String arg0) {
		System.out.println(arg0);
	}

	public void warn(String arg0, Throwable arg1) {
		System.out.println(arg0);
		arg1.printStackTrace();
	}

	@Override
	public String name() {
		return "SimpleLogger";
	}

	@Override
	public boolean isTraceEnabled() {
		return true;
	}

	@Override
	public void trace(String paramString) {
		info(paramString);
	}

	@Override
	public void trace(String paramString, Object paramObject) {
		info(paramString, paramObject);
	}

	@Override
	public void trace(String paramString, Object paramObject1, Object paramObject2) {
		info(paramString, paramObject1, paramObject2);
	}

	@Override
	public void trace(String paramString, Object... paramArrayOfObject) {
		info(paramString, paramArrayOfObject);
	}

	@Override
	public void trace(String paramString, Throwable paramThrowable) {
		info(paramString, paramThrowable);
	}

	@Override
	public void debug(String paramString, Object paramObject) {
		info(paramString, paramObject);
	}

	@Override
	public void debug(String paramString, Object paramObject1, Object paramObject2) {
		info(paramString, paramObject1, paramObject2);
	}

	@Override
	public void debug(String paramString, Object... paramArrayOfObject) {
		info(paramString, paramArrayOfObject);
	}

	@Override
	public void info(String paramString, Object paramObject) {
		info(paramString, new Object[] {paramObject});
	}

	@Override
	public void info(String paramString, Object paramObject1, Object paramObject2) {
		info(paramString, new Object[] {paramObject1, paramObject2});
	}

	@Override
	public void info(String paramString, Object... paramArrayOfObject) {
		System.out.println(format(paramString, paramArrayOfObject));
	}

	private String format(String format, Object[] parameters) {
		if (parameters == null || parameters.length == 0) {
			return format;
		}
		if (format.indexOf("{0") >= 0 || format.indexOf("{1") >= 0 || format.indexOf("{2") >= 0
				|| format.indexOf("{3") >= 0)
			try {
				return java.text.MessageFormat.format(format, parameters);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		return format;
	}

	@Override
	public void warn(String paramString, Object paramObject) {
		info(paramString, paramObject);
	}

	@Override
	public void warn(String paramString, Object... paramArrayOfObject) {
		info(paramString, paramArrayOfObject);
	}

	@Override
	public void warn(String paramString, Object paramObject1, Object paramObject2) {
		info(paramString, paramObject1, paramObject2);
	}

	@Override
	public void error(String paramString, Object paramObject) {
		info(paramString, paramObject);
	}

	@Override
	public void error(String paramString, Object paramObject1, Object paramObject2) {
		info(paramString, paramObject1, paramObject2);
	}

	@Override
	public void error(String paramString, Object... paramArrayOfObject) {
		info(paramString, paramArrayOfObject);
	}

	@Override
	public void log(InternalLogLevel paramInternalLogLevel, String paramString, Object paramObject) {
		info(paramString, paramObject);
	}

	@Override
	public void log(InternalLogLevel paramInternalLogLevel, String paramString, Object paramObject1,
			Object paramObject2) {
		info(paramString, paramObject1, paramObject2);
	}

	@Override
	public void log(InternalLogLevel paramInternalLogLevel, String paramString, Object... paramArrayOfObject) {
		info(paramString, paramArrayOfObject);
	}

	@Override
	public void trace(Throwable t) {
		t.printStackTrace();
	}

	@Override
	public void debug(Throwable t) {
		t.printStackTrace();
	}

	@Override
	public void info(Throwable t) {
		t.printStackTrace();
	}

	@Override
	public void warn(Throwable t) {
		t.printStackTrace();
	}

	@Override
	public void error(Throwable t) {
		t.printStackTrace();
	}

	@Override
	public void log(InternalLogLevel level, Throwable t) {
		if (level == InternalLogLevel.ERROR) {
			error(t);
		} else if (level == InternalLogLevel.WARN) {
			warn(t);
		} else if (level == InternalLogLevel.INFO) {
			info(t);
		} else if (level == InternalLogLevel.DEBUG) {
			debug(t);
		} else if (level == InternalLogLevel.TRACE) {
			trace(t);
		}
	}

}
