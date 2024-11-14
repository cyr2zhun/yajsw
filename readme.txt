yajsw-stable-13.13

    * Update: netty -> 4.1.114.Final
    * Update: commons-io -> 2.17.0
	* Update: commons-net -> 3.11.1
    * Bug: genConfig.bat generates defect config when classpath contains spaces
	* Bug: Hard coded Java path in setenv.bat in version 13.12
	* Bug: Quotes in posix batch files

Note: the next release (14.0) will include the following BREAKING CHANGES:

	* Support only JDK11+. Reason: Current groovy version does not run on JDK 22+, Groovy 5 requires JDK11+
	* VFS-DBX and VFS-webdav support will be dropped. Reason: dependencies no longer supported.
	* servermgr will be removed. Reason: glazedlist security issue.
	
If you have issues with this pls post a ticket.

NEW: for PAID SUPPORT please contact: jonatahn.roesner@espero.tech

Note: support the project by donating:

dogecoin: D84dTitLwAjvoTvZBi97mYdTKvtyJrc3xR
bitcoin:  bc1q5yghsmwj3k2ak7hpvrzd3gmpev5p089msqe4nr
polygon:  0xC46ffecEb6403452443AcC27E45335a589ca7eea

