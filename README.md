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

Node file(node_name  [signal...]):

    1   S1  S11
    2   S3
    3   S4
    4   S5
    5   S1
    6   S1

Edge file(edge_from  edge_to  [signal...]):

    1   2   S6
    2   3   S2
    2   4   S7
    3   4   S2
    4   6   S8
    5   6   S9
    1   5   S10
    
Signal file(singal  weight)

    S1  7.0
    S2  -20.0
    S3  40.0
    S4  15.0
    S5  8.0
    S6  3.0
    S7  -7.0
    S8  -10.0
    S9  -2.0
    S10 -15.3
    S11 1.0


Yellow vertices - vertex group S1, red edges - edge group S2.

![Example](/sample.png?raw=true "Sample")

Red units in graph below - solution.

![Example](/sample_solved.png?raw=true "Solution")

Running th example
==============

    java -Djava.library.path=PATH_TO_CPLEX -jar signal.jar -n nodes -e edges -s signals -o output
