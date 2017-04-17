[![Build Status](https://travis-ci.org/ctlab/sgmwcs-solver.svg?branch=master)](https://travis-ci.org/ctlab/sgmwcs-solver)

# sgmwcs-solver

This is a solver for signal version of generalized maximum-weight connected subgraph problem (SGMWCS).

See [releases](https://github.com/ctlab/sgmwcs-solver/releases) to get built jar files.

# Dependencies

The program requires CPLEX (≥ 12.63) to be installed on your computer.

Building from sources
===========

Get source using git or svn using the web URL:

    https://github.com/ctlab/sgmwcs-solver.git
    
Then you should install concert library of CPLEX.
It's located in "cplex/lib" directory from CPLEX STUDIO root path.
For example, 

    mvn install:install-file -Dfile=/opt/ibm/ILOG/CPLEX_Studio1251/cplex/lib/cplex.jar -DgroupId=com.ibm -DartifactId=cplex -Dversion=12.6.3 -Dpackaging=jar
    
After that you can build the project using maven:

    mvn install -DskipTests=true
    
And jar file with name "sgmwcs-solver.jar" will appear in the "target" directory
    
Running
=======

To run the program you should set jvm parameter java.library.path to directory of CPLEX binaries like that:

    java -Djava.library.path=/opt/ibm/ILOG/CPLEX_Studio1263/cplex/bin/x86-64_linux -jar sgmwcs-solver.jar

See more help by using flag -h.

Tip: you can put the file `libcplex%version%.so` to the one of the predefined in java.library.path 
directories(e.g. `/usr/lib`). Then to run the program you can simply type:
 
    java -jar sgmwcs-solver.jar

Problem
=========

The solver solves a signal version of generalized Maximum Weighted Connected Subgraph problem with weighted vertices and edges.
Input of the problem is graph with node and edge weights (positive or negative). 
Some of the nodes or edges are grouped into a signal so that each node/edge in the signal has the same score.
The goal is to find a connected subgraph with a maximal weight, considered nodes/edges in a signal group are counted maximum one time.

Format and example
=========

Node file(node_name  node_weight  [signal...]):

    1   7.0 S1
    2   40.0
    3   15.0
    4   8.0
    5   7.0 S1
    6   7.0 S1

Edge file(edge_from edge_to edge_weight  [signal...]):

    1   2   3.0
    2   3   -20.0 S2
    2   4   -7
    3   4   -20.0 S2
    4   6   -10.0
    5   6   -2.0
    1   5   -15.3

You can not specify any signal for some nodes/edges, in this case a unique one will be created implicitly.
    
Yellow vertices - vertex group S1, red edges - edge group S2.

![Example](/sample.png?raw=true "Sample")

Red units in graph below - solution.

![Example](/sample_solved.png?raw=true "Solution")

Running th example
==============

    java -Djava.library.path=PATH_TO_CPLEX -jar signal.jar -n nodes -e edges -s signals -o output
