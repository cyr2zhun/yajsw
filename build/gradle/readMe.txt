YAJSW uses gradle as build tool:

http://www.gradle.org/

The build is a multi project script. Gradle requires per project a build folder.
The following build folders are used:

wrapper 		-> wrapper.jar			main yajsw jar 
wrapper-app	-> wrapperApp.jar		wraps the application
ahessian		-> ahessian.jar			netty/hessian based asynch communication between wrapper and system tray icon. hessian packages renamed to avoid conflict with existing hessian libs
srvmgr			-> srvmgr.jar				experimental - monitoring of multiple servers

To execute a build: 
- <yajsw>/build/gradle
- Navigte with a console to <yajsw>/build/gradle
- If you are behind a http proxy edit gradlew.bat/gardlew.sh and set the proxy in the java options
- execute gradlew

This will download gradle and execute the build script.

The produced jar files are found in the folders: 

<yajsw>/build/gradle/<sub-project>/build/libs

Eclipse project files can be generated by adding the according gradle tasks to the gradle build scripts.

note: we need a build for jdk 8 and jdk >= 9

first build for jdk9:

edit build9.bat
edit settings.gradle
execute build9.bat

then build for jdk8:

edit build.bat
edit settings.gradle
execute build.bat

