# ACircuitMiner
An Arithmetic Circuit Miner.

This is meant to serve as a library for applications that mine patterns in arithmetic circuits to help in the design of (the functionality of) hardware components.

## Usage

### Important classes

**[Miner](/src/main/java/com/vincentderk/acircuitminer/miner/Miner.java)**

Provides a method to mine patterns and their occurrences in a given directed Graph (AC). Currently (1.0) only occurrences (connected induced subgraph with additional input nodes) with one output and a maximum of k nodes are found. Current (1.0) algorithms find occurrences and determine their pattern by using a canonical labeling technique. This technique provides, given an occurrence X, the representative graph (pattern) of the set of isomorph graphs where X belongs to.


**[NeutralFinder](/src/main/java/com/vincentderk/acircuitminer/miner/emulatable/neutralfinder/NeutralFinder.java)**

This class contains methods that, given a pattern P, find all patterns that P can emulate using 0,1 or a variable as input.
Currently, this is done by using a brute-force approach that checks all possible combinations of input (0,1,2 where 2 is a variable input). For each input, the resulting pattern is determined. The current implementation leaves room for improvements. After every decision (e.g 0 als 2nd input), the consequences are checked. For example, if 0 is used as input for a multiplication, the other input value does not matter (unless that input is used by some other node where it does matter). The implementation of checking the consequences after every decision easily facilitates these kind of improvements. The final result of these methods is not just the emulatable patterns but also information regarding the emuluation, e.g the amount of (in)active sum en product operations and which nodes are (in)active. Also the input (combination of {0,1,2}) that is used to emulate the pattern is stored. The datatype used for this is an [EmulatableBlock](/src/main/java/com/vincentderk/acircuitminer/miner/emulatable/neutralfinder/EmulatableBlock.java).


**[EdgeCanonical](/src/main/java/com/vincentderk/acircuitminer/miner/canonical/EdgeCanonical.java)/[EdgeCanonicalMultiOutput](/src/main/java/com/vincentderk/acircuitminer/miner/canonical/EdgeCanonicalMultiOutput.java)**

Converts a given graph X (occurrence), to a representative graph (pattern) so that occurrences of the same pattern will be converted to the same representative graph. Having occurrences of the same pattern means they are isomorphic in regards to eachother. The concept of Canonical Labeling is used to reduce a set of isomorphic graphs to one representative graph such that the same representative graph means they are isomorphic and vice versa.


**[OperationUtils](/src/main/java/com/vincentderk/acircuitminer/miner/util/OperationUtils.java).replace(Neutral)**

There are also methods available to replace occurrences in an AC. The modified circuit can then be written to an .ac file.
Change in .ac specification: There is a distinction between node 0 and 1 and the input constants 0 and 1. This required distinction follows from the emulation algorithm where constants 0 and 1 can be used as input of the pattern (component) to emulate another pattern. For the distinction, we will use -2^{constant} for constant input values.


**[EquivalenceChecker](/src/main/java/com/vincentderk/acircuitminer/miner/util/verification/EquivalenceChecker.java)**

To provide more certainty about the correctness of the replacement, there is a method that checks whether an AC before and after replacement is still functionally equivalent. This currently happens by decomposing the composed operations back to their original structure with multiple nodes. The resulting circuit should then be isomorphic to the original 
circuit. For that last step, we use the DFS-EdgeCanonical implementation. Experiments show that even for Alarm (1570 nodes) the problem already becomes too big and the algorithm takes too long (stopped after an hour). So in further work we could try to take another approach that only focuses on the nodes that have changed.

### Notable methods

The [OperationUtils](/src/main/java/com/vincentderk/acircuitminer/miner/util/OperationUtils.java) contains some methods that can be useful for operations/experiments:

* getCosts/getTotalCosts This method is used to calculate the evaluation cost of a given AC per category (instruction, input, output and operations) and in total.

* removeOverlap This reduces a given set of occurrences to a non-overlapping set via e simple first-come, first-served heuristic.

* readACStructure This can read an AC from an .ac file.

* write This can write a Graph (AC) to an .ac file. The standard format is used. Besides the + and *, other additional symbols can be used that denote introduced patterns (components).
The structure of those patterns (components) can then also be saved as an .ac file. For constant input values, -2^{constant input value} is used.
 
* patternBlockCost This method calculates the evaluation cost of a pattern as if it was available as an hardware component. So all the nodes are evaluated as one node (instruction).
This makes a distinction between active and inactive nodes (based on the input). Inactive nodes (irrelevant operations) take only 10% of the usual cost.

* patternOccurrenceCost This method calculates the evaluation cost of a pattern as if each operation (node) was to be evaluted seperately. Note: +/3 and +/2 cost equally in
terms of operation cost (not in terms of input cost).

### Available [experiments](/src/main/java/com/vincentderk/acircuitminer/experiments/benchmarks/)

1. [ExperimentBestPattern](/src/main/java/com/vincentderk/acircuitminer/experiments/benchmarks/ExperimentBestPattern.java)  first uses the enumeration algorithm to find patterns and their occurrences.
Next, each pattern is evaluated in parallel. This is done by using two heuristics based on the biggest-emulated-pattern first 
and the first-come, first-served principle for the occurrences. The code also includes a commented section that uses the best 
pattern to replace the occurrences and save the resulting ACs to file.

2. [ExperimentBestPatternNL (NoLabel)](/src/main/java/com/vincentderk/acircuitminer/experiments/benchmarks/ExperimentBestPatternNL.java) is analogous to ExperimentBestPattern. The only difference is that it pretends that a node 
in a pattern can execute both the + and * operations. This is achieved by changing the operations in a pattern and then checking the emulated patterns. 
This is repeated for each possible combination of  + and * for the pattern after which all results are combined into one set of emulated patterns.

3. [ExperimentMiner](/src/main/java/com/vincentderk/acircuitminer/experiments/benchmarks/ExperimentMiner.java) executes the enumeration algorithm

### Used networks

The networks used in testing this code were obtained from [bnlearn](http://www.bnlearn.com/bnrepository/) and compiled into .ac files with [ACE](http://reasoning.cs.ucla.edu/ace/).

## Dependencies
Google Guava 16.0.1+

FastUtil 8.1+

## Changelog

### 2.0 MultiOutput (WIP)
*Focuses on adding support for patterns with multiple outputs. This version adds support for occurrences with only 1 root where intermediate nodes can also have an output.*

* Simplified Enumerator structure (API change)
* Changed State hierarchy to add MultiOutput support (API change)
* Added Labeling support for multiple outputs with multiple roots:  [EdgeCanonicalMultiOutput](/src/main/java/com/vincentderk/acircuitminer/miner/canonical/EdgeCanonicalMultiOutput.java)
*


### 1.0 Initial Release
*Focuses on best pattern (connected induced occurrences with only 1 output and max k nodes). Best is defined in terms of energy (pJ) required during evaluation.*

* Finding patterns and occurrences with only one output node (singleOutput)
* Emulation by neutral and absorbing input for patterns with only one output node (singleOutput)
* Experiments
* Utilities: replacement, cost calculation, reading from and writing to .ac files
