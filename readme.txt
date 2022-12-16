yajsw-stable-13.07

    * Update: netty -> 4.1.86.Final , CVE-2022-41915
    * Update: jna -> 5.12.1
    * Update: commons-cli -> 1.5.0
    * Update: commons-lang3 -> 3.12.0
    * Update: commons-vfs2 -> 2.9.0
    * Bug: Posix setting process affinity on vms
    * New configuration: wrapper.affinity.bitset, Posix only, affinity as string of 0/1, with length == core count, if wrapper.affinity is also set, new property has precendence
    * New configuration: wrapper.priority.nice,  Posix only, allows setting nice to values -20 - 19, if wrapper.priority is also set, new property has precendence
	
	
Note: support the project by donating:

dogecoin: D84dTitLwAjvoTvZBi97mYdTKvtyJrc3xR
bitcoin:  bc1q5yghsmwj3k2ak7hpvrzd3gmpev5p089msqe4nr
polygon:  0xC46ffecEb6403452443AcC27E45335a589ca7eea