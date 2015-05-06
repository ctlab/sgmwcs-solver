# gmwcs-solver
Compilation
===========

Get source from github:

    git clone <HTTPS clone URL (see on the right side of this page)>
    
Then you should install concern library of CPLEX like this:

    mvn install:install-file  -Dfile=cplex.jar -DgroupId=com.ibm -DartifactId=cplex -Dversion=12.5.1 -Dpackaging=jar
    
After that you can build the project using maven:

    mvn install
    
And jar file with name "gmwcs-solver-1.0-jar-with-dependencies.jar" will appear in "target" directory
    
Running
=======

To run program you should set jvm parameter java.library.path to directory of CPLEX binaries like that:

    java -Djava.library.path=/opt/ibm/ILOG/CPLEX_Studio1251/cplex/bin/x86-64_sles10_4.1/ -jar gmwcs-solver-1.0-jar-with-dependencies.jar

See more help by using flag -h.