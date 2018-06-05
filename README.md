# ACircuitMiner
An Arithmetic Circuit Miner.

This is meant to serve as a library for applications that mine patterns in arithmetic circuits to help in the design of (the functionality of) hardware components.

## Usage

### Important methods

**(Multi)BackTrackEnumerator**

These methods find all patterns (with one output and a given maximum amount of input nodes) and their occurrences in a given directed Graph (AC).
Since it only looks for patterns with one output, the problem is reduced to finding all occurrences with a certain node as output node.
That algorithm can then be repeated for each node. Both BackTrackEnumerator and MultiBackTrackEnumerator work this way. MultiBackTrackEnumerator uses multiple threads
that take the next available node to perform the algorithm on. When an occurrence is found, the associated pattern is determined by using canonical labeling.


**NeutralFinder**

This class contains methods that, given a pattern P, find all patterns that P can emulate using 0,1 or a variable as input.
Currently, this is done by using a brute-force approach that checks all possible combinations of input (0,1,2 where 2 is a variable input).
For each input, the resulting pattern is determined. The current implementation leaves room for improvements. After every decision (e.g 0 als 2nd input),
the consequences are checked. For example, if 0 is used as input for a multiplication, the other input value does not matter (unless that input 
is used by some other node where it does matter). The implementation of checking the consequences after every decision easily facilitates these kind of improvements.
The final result of these methods is not just the emulatable patterns but also information regarding the emuluation, e.g the amount of (in)active sum en product operations 
and which nodes are (in)active. Also the input (combination of {0,1,2}) that is used to emulate the pattern is stored. The datatype used for this is an EmulatableBlock.


**EdgeCanonical**

This class contains two methods to perform canonical labeling on induced, connected, directed acyclic graphs with one root (output) node.
The first method uses a breadth-first approach and is used by the enumeration algorithm (BackTrackEnumerator). The second method uses a depth-first approach.
The depth-first approach requires less memory and should therefore be more usable for bigger graphs (50+ nodes). It has not been studied whether the depth-first
approach results in a speedup and should be used by the enumeration algorithms. Both methods provide the canonical code, the amount of input nodes and the internal
nodes in an order related to their assignment in the labeling process.


**OperationUtils.replace(Neutral)**

There are also methods available to replace occurrences in an AC. The modified circuit can then also be written to an .ac file.
There is a distinction between node 0 and 1 and the the constants 0 and 1. This required distinction follows from the emulation algorithm
where constants 0 and 1 can be used as input of the pattern (component) to emulate another pattern. For the distinction, we will use -2^{constant} for constant input values.


**EquivalenceChecker**

To provide more certainty about the correctness of the replacement, there is a method that checks whether an AC before and after replacement is still functionally equivalent.
This currently happens by decomposing the composed operations back to their original structure with multiple nodes. The resulting circuit should then be isomorphic to the original 
circuit. For that last step the DFS-EdgeCanonical implementation is currently used. Experiments show that even for Alarm (1570 nodes) the problem already becomes too big and 
the algorithm takes too long (stopped after an hour). So in further work we could try to take another approach that only focuses on the nodes that have changed.

### Notable methods

The OperationUtils class contains some methods that can be useful for operations/experiments:

* getCosts/getTotalCosts This method is used to calculate the evaluation cost of a given AC per category (instruction, input, output and operations) and in total.

* removeOverlap This reduces a given set of occurrences to a non-overlapping set via e simple first-come, first-served heuristic.

* readACStructure This can read an AC from an .ac file.

* write This can write a Graph (AC) to an .ac file. The standard format is used. Besides the + and *, other additional symbols can be used that denote introduced patterns (components).
The structure of those patterns (components) can then also be saved as an .ac file. For constant input values, -2^{constant input value} is used.
 
* patternBlockCost This method calculates the evaluation cost of a pattern as if it was available as an hardware component. So all the nodes are evaluated as one node (instruction).
This makes a distinction between active and inactive nodes (based on the input). Inactive nodes (irrelevant operations) take only 10% of the usual cost.

* patternOccurrenceCost This method calculates the evaluation cost of a pattern as if each operation (node) was to be evaluted seperately. Note: +/3 and +/2 cost equally in
terms of operation cost (not in terms of input cost).

### Available experiments

1. ExperimentBestPattern first uses the enumeration algorithm to find patterns and their occurrences.
Next, each pattern is evaluated in parallel. This is done by using two heuristics based on the biggest-emulated-pattern first 
and the first-come, first-served principle for the occurrences. The code also includes a commented section that uses the best 
pattern to replace the occurrences and save the resulting ACs to file.

2. ExperimentBestPatternNL (NoLabel) is analogous to ExperimentBestPattern. The only difference is that it pretends that a node 
in a pattern can execute both the + and * operations. This is achieved by changing the operations in a pattern and then checking the emulated patterns. 
This is repeated for each possible combination of  + and * for the pattern after which all results are combined into one set of emulated patterns.

3. ExperimentMiner executes the enumeration algorithm

### Used networks

The networks used in testing this code were obtained from [bnlearn](http://www.bnlearn.com/bnrepository/) and compiled into .ac files with [ACE](http://reasoning.cs.ucla.edu/ace/).

## Dependencies
Google Guava 16.0.1+

FastUtil 8.1+
