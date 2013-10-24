package cs224n.assignment;

import cs224n.assignment.Grammar.UnaryRule;
import cs224n.assignment.Grammar.BinaryRule;
import cs224n.ling.Tree;
import cs224n.util.Pair;
import java.util.*;
import java.lang.String;
import java.lang.Integer;

/**
 * The CKY PCFG Parser you will implement.
 */
public class PCFGParser implements Parser {
    private Grammar grammar;
    private Lexicon lexicon;
    private static List<String> nonterms;

    public void train(List<Tree<String>> trainTrees) {
        // Binarize and annotate the training tree so that rules are at most
        // binary and we use second-order vertical Markovization.
        List<Tree<String>> binarizedTrainTrees = new ArrayList<Tree<String>>();

        for (Tree<String> tree : trainTrees) {
          binarizedTrainTrees.add(TreeAnnotations.annotateTree(tree));
        }

        lexicon = new Lexicon(binarizedTrainTrees);
        grammar = new Grammar(binarizedTrainTrees);

        // We wrote a function in Grammar which looks at all non-terms
        // and creates a list. Not just the non-terminals in the given
        // tree.
        nonterms = grammar.getAllTags();
    }


    public Tree<String> getBestParse(List<String> sentence) {
        // Replacing the triple matrix with a HashMap of 
        // String with current index of (i, j, nonterm) with a value of 
        // a pair of Double and String of (either B, or current split,
        // A and B).
        HashMap<String, HashMap<String, Pair<Double, String>>> scoreBack = 
            new HashMap<String, HashMap<String, Pair<Double, String>>>();

        // Score different parses of the sentence.
        fillingNonterminals(scoreBack, sentence);
        fillingTable(scoreBack, sentence);

        // Reconstruct the best parse tree from this information.
        Tree<String> bestParse = buildTree(scoreBack, 0, 
                sentence.size(), "ROOT");
        bestParse.setWords(sentence);
        bestParse = TreeAnnotations.unAnnotateTree(bestParse);

        return bestParse;
    }

    /* The first part of the CKY Algorithm to fill the preterminals.
     */
    private void fillingNonterminals(
            HashMap<String, HashMap<String, Pair<Double, String>>> scoreBack, 
            List<String> sentence) {
        // Score the preterminals. This loop is for per leaf.
        for (int i = 0; i < sentence.size(); i++) {
            String word = sentence.get(i);
            HashMap<String, Pair<Double, String>> inner = 
                new HashMap<String, Pair<Double, String>>();
            scoreBack.put(makeIndex(i, i+1), inner);
            for (String nonterm: nonterms) {
                double scoreTag = lexicon.scoreTagging(word, nonterm);
                if (scoreTag > 0) {
                    inner.put(nonterm, new Pair<Double, String>(scoreTag, ""));
                }
            }
            // Handle unaries.
            boolean added = true;

            while (added) {
                added = false;

                // For every rule which already has a great than 0
                // prob of occurring, treat it as the child of a 
                // unary and see if there's another rule that trumps
                // the current probability.
                String[] keys = inner.keySet().toArray(new String[0]);
                for (int b = 0 ; b < keys.length ; b++) {
                    String nonterm = keys[b];

                    List<UnaryRule> unaryRules =
                      grammar.getUnaryRulesByChild(nonterm);

                    Double bScore = inner.get(nonterm).getFirst();
                    if (bScore > 0) {

                        for (int k = 0; k < unaryRules.size(); k++) {

                            UnaryRule unaryRule = unaryRules.get(k);
                            String parent = unaryRule.getParent(); 

                            double prob = unaryRule.getScore() * bScore;

                            Pair<Double, String> parentPair = inner.get(parent);
                            if (parentPair == null ||
                                    prob > parentPair.getFirst()) {
                                inner.put(parent, 
                                        new Pair<Double, String>(prob, "" + nonterm));
                                added = true;
                            }
                        }
                    }
                }
            }
        }
    }


    /* The second part of the CKY algorithm which fills the transition
     * rules for other non-terminals.
     */
    private void fillingTable(
            HashMap<String, HashMap<String, Pair<Double, String>>> scoreBack, 
            List<String> sentence) {
        // For every possible combination of span, split, begin, and end
        // look at the child rules and see if it's possible to create
        // a rule for the parent
        for (int span = 2 ; span <= sentence.size() ; span++) {
            for (int begin = 0 ; begin <= sentence.size() - span; begin++) {
                int end = begin + span;

                if (!scoreBack.containsKey(makeIndex(begin, end))) {
                    scoreBack.put(makeIndex(begin, end), 
                            new HashMap<String, Pair<Double, String>>());
                }
                // For this tile, look at the possible splits that 
                // could occur to comprise of this being and end span.
                HashMap<String, Pair<Double, String>> beginEnd
                    = scoreBack.get(makeIndex(begin, end));

                for (int split = begin + 1 ; split <= end - 1 ; split ++) {
                    HashMap<String, Pair<Double, String>> beginSplit
                        = scoreBack.get(makeIndex(begin, split));

                    HashMap<String, Pair<Double, String>> splitEnd 
                        = scoreBack.get(makeIndex(split, end));

                    // For every left child, get all the rules that
                    // exist for the left child and see if the corresponding
                    // one exists for the right child, if so, then 
                    // calculate the probability of this rule occurring
                    String[] keys = beginSplit.keySet().toArray(new String[0]);
                    for (int b = 0 ; b < keys.length ; b++) {

                        String BString = keys[b]; 
                        List<BinaryRule> rules = 
                            grammar.getBinaryRulesByLeftChild(BString); 
                        double BScore = beginSplit.get(BString).getFirst();
                        
                        for (BinaryRule rule : rules) {
                            String AString = rule.getParent();
                            String CString = rule.getRightChild();
                            
                            if (splitEnd.containsKey(CString)) {

                                double prob = BScore * splitEnd.get(CString).getFirst() 
                                    * rule.getScore(); 

                                Pair<Double, String> parentPair = 
                                    beginEnd.get(AString);
                                if (parentPair == null ||
                                        prob > parentPair.getFirst()) {
                                    beginEnd.put(AString, 
                                            new Pair<Double, String>(prob, split + "]" + 
                                                BString + "]" + CString));
                                }
                            }
                        }
                    }
                }

                // Handles unaries
                boolean added = true;
                while (added) {
                    added = false;

                    String[] keys = beginEnd.keySet().toArray(new String[0]);
                    for (int b = 0 ; b < keys.length ; b++) {
                        String BString = keys[b];

                        Pair<Double, String> BPair = 
                            beginEnd.get(BString);
                        List<UnaryRule> unaryRules =
                          grammar.getUnaryRulesByChild(BString);

                        double BScore = BPair.getFirst();
                        for (UnaryRule rule : unaryRules) {
                            double prob = rule.getScore() * BScore;
                            String AString = rule.getParent();

                            Pair<Double, String> parentPair = 
                                beginEnd.get(AString);
                            if (parentPair == null ||
                                    prob > parentPair.getFirst()) {
                                beginEnd.put(AString, 
                                        new Pair<Double, String>(prob, "" + BString));
                                added = true;
                            }
                        }
                    }
                }
            }
        }
        //printScoreBack(scoreBack);
    }


    /* Given the scoreBack hashmap, the tile, and the rule parent,
     * find the subtree of the highest probability 
     */
    private Tree<String> buildTree(
            HashMap<String, HashMap<String, Pair<Double, String>>> scoreBack,
            int i, int j, String parent) {

        List<Tree<String>> children = new ArrayList<Tree<String>>();
        Pair<Double, String> back = scoreBack.get(makeIndex(i, j)).get(parent);
        // If this is a leaf, then just add a placeholder leaf
        // that will be replaced during setWords()
        if (back.getSecond().equals("") && back.getFirst() > 0) {
            Tree<String> leaf = new Tree<String>("fake");
            children.add(leaf);
            return new Tree<String>(parent, children);
        }

        String backEntry = back.getSecond();
        // If it's a unary rule, look at the same tile but
        // with different entry
        if (backEntry.indexOf("]") < 0) {
            children.add(buildTree(scoreBack, i, j, backEntry));
        } else {
            // if it's a binary rule, look at the two tiles
            // which are the results of the split.
            String[] triple = backEntry.split("\\]");
            Tree<String> leftSubtree = buildTree(scoreBack, i,
                    Integer.parseInt(triple[0]), triple[1]);
            children.add(leftSubtree);
            Tree<String> rightSubtree = buildTree(scoreBack, 
                    Integer.parseInt(triple[0]), j, triple[2]);
            children.add(rightSubtree);
        }

        return new Tree<String>(parent, children);
    }


    private String makeIndex(int i, int j) {
        return "" + i + "." + j;
    }

    
    /* Helper functions to print out the scores and back values so far
     */
    private void printScoreBack(
            HashMap<String, HashMap<String, Pair<Double, String>>> scoreBack) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, HashMap<String, Pair<Double, String>>> outer :
                scoreBack.entrySet()) {
            sb.append(outer.getKey()).append("\n");
            for (Map.Entry<String, Pair<Double, String>> nonterm : 
                    outer.getValue().entrySet()) {
                sb.append("\t");
                sb.append(nonterm.getKey());
                sb.append("-----");
                sb.append(nonterm.getValue());
                sb.append("\n");
            }
        }
        System.out.println(sb.toString());
    }
}
