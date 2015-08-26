# gmwcs-solver
Compilation
===========

Get source from github:

    git clone <HTTPS clone URL (see on the right side of this page)>
    
Then you should install concert library of CPLEX.
It's located in "cplex/lib" directory from CPLEX STUDIO root path.
For example, 

    mvn install:install-file -Dfile=/opt/ibm/ILOG/CPLEX_Studio1251/cplex/lib/cplex.jar -DgroupId=com.ibm -DartifactId=cplex -Dversion=12.5.1 -Dpackaging=jar
    
After that you can build the project using maven:

    mvn install
    
And jar file with name "signal.jar" will appear in "target" directory
    
Running
=======

To run program you should set jvm parameter java.library.path to directory of CPLEX binaries like that:

    java -Djava.library.path=/opt/ibm/ILOG/CPLEX_Studio1251/cplex/bin/x86-64_sles10_4.1/ -jar signal.jar

See more help by using flag -h.