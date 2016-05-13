# Fit default ext project  

Add HTTP, SSH, SNMP, Cmdline,SFTP support for fit autotest framework.

* build

         1. Copy fit.jar (compile fit first) to lib DIR
         2. Run build.xml
     
* use 

         1. Copy fit-ext-default.jar to fitall.jar!/plugins DIR
         2. Copy dependencies(lib/*.jar, exclude fit.jar) to fitall.jar!/extlib DIR
         
         Or run build-all.xml(in build-fit project) instead

