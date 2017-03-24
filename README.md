[![Build Status](https://travis-ci.org/ctlab/sgmwcs-solver.svg?branch=master)](https://travis-ci.org/ctlab/sgmwcs-solver)

# sgmwcs-solver

This is a solver for signal version of generalized maximum-weight connected subgraph problem (SGMWCS).

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

The solver solves a signal version of generalized Maximum Weighted Connected Subgraph problem with weighted vertices and edges.
Input of the problem is graph with node and edge weights (positive or negative). 
Some of the nodes or edges are grouped into a signal so that each node/edge in the signal has the same score.
The goal is to find a connected subgraph with a maximal weight, considered nodes/edges in a signal group are counted maximum one time.

Example
=========

Node file(node_name  node_weight):

    1   7.0
    2   40.0
    3   15.0
    4   8.0
    5   7.0
    6   7.0

Edge file(edge_from edge_to edge_weight):

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

Running sample
==============

    java -Djava.library.path=PATH_TO_CPLEX -jar signal.jar -n nodes -e edges -s signals
