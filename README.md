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

Problem
=========

Solver solves Maximum Weighted Connected Subgraph with weighted vertices and edges. Besides, there is possibility to
create groups of vertices or edges. Weight of group takes into consideration in goal function only if at least one
vertex or edge presents in solution.

Sample
=========

Node file:

    1   7
    2   40
    3   15
    4   8
    5   7
    6   7

Edge file:

    1   2   3.0
    2   3   -20.0
    2   4   -7
    3   4   -20.0
    4   6   -10.0
    5   6   -2.0
    1   5   -15.3

Signal file:
    2 -- 3  3 -- 4
    1   5   6
    
Yellow vertices - vertex group, red edges - edge group.

![Sample](/sample.png?raw=true "Sample")

Red units in graph below - solution.

![Sample](/sample_solved.png?raw=true "Solution")
    
    

