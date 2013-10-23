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
        // Binarize the training tree so that rules are at most binary.
        List<Tree<String>> binarizedTrainTrees = new ArrayList<Tree<String>>();

        for (Tree<String> tree : trainTrees) {
          binarizedTrainTrees.add(TreeAnnotations.annotateTree(tree));
        }

        lexicon = new Lexicon(binarizedTrainTrees);
        grammar = new Grammar(binarizedTrainTrees);

        nonterms = grammar.getAllTags();
        // Possibly just look at the given list of trees
        // before binarization for the nonterms in the first round
        // of nonterm going to a word. It might be a lot faster
        // but also slower since you need to Lexiconize each tree.
    }


    public Tree<String> getBestParse(List<String> sentence) {
        // Replacing the triple matrix with a HashMap of 
        // String with current index of (i, j, nonterm) with a value of 
        // a pair of Double and String of (either B, or current split,
        // A and B).
        HashMap<String, HashMap<String, Pair<Double, String>>> scoreBack = 
            new HashMap<String, HashMap<String, Pair<Double, String>>>();

        fillingNonterminals(scoreBack, sentence);
        fillingTable(scoreBack, sentence);
        Tree<String> STree = buildTree(scoreBack, 0, 
                sentence.size(), "S^ROOT");
        List<Tree<String>> child = new ArrayList<Tree<String>>();
        child.add(STree);
        Tree<String> bestParse = new Tree<String>("ROOT", child);
        bestParse.setWords(sentence);
//        bestParse = TreeAnnotations.unAnnotateTree(bestParse);
        return bestParse;
    }

    /* The first part of the CKY Algorithm to fill the non-terminal that 
     * become words
     */
    private void fillingNonterminals(
            HashMap<String, HashMap<String, Pair<Double, String>>> scoreBack, 
            List<String> sentence) {
        // Score the preterminals.
        for (int i = 0; i < sentence.size(); i++) {
            String word = sentence.get(i);
            HashMap<String, Pair<Double, String>> inner = 
                new HashMap<String, Pair<Double, String>>();
            scoreBack.put(makeIndex(i, i+1), inner);
            for (String nonterm: nonterms) {
                // https://piazza.com/class/hjz2ma06gdh2hg?cid=142
                
                double scoreTag = lexicon.scoreTagging(word, nonterm);
                if (scoreTag > 0) {
                    inner.put(nonterm, new Pair<Double, String>(scoreTag, ""));
                }
            }
            // Handle unaries.
            boolean added = true;

            while (added) {
                added = false;

                String[] keys = inner.keySet().toArray(new String[0]);
                for (int b = 0 ; b < keys.length ; b++) {
                    String nonterm = keys[b];

                    List<UnaryRule> unaryRules =
                      grammar.getUnaryRulesByChild(nonterm);

                    Double bScore = inner.get(nonterm).getFirst();
                    if (bScore > 0) {

                        for (int k = 0; k < unaryRules.size(); k++) {

                            UnaryRule unaryRule = unaryRules.get(k);
                            // Find the index into the score array for the parent
                            // of the unary rule.
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
     * rules for non-terminals
     */
    private void fillingTable(
            HashMap<String, HashMap<String, Pair<Double, String>>> scoreBack, 
            List<String> sentence) {
        // Loop for creating span numbers 
        for (int span = 2 ; span <= sentence.size() ; span++) {
            for (int begin = 0 ; begin <= sentence.size() - span; begin++) {
                int end = begin + span;

                if (!scoreBack.containsKey(makeIndex(begin, end))) {
                    scoreBack.put(makeIndex(begin, end), 
                            new HashMap<String, Pair<Double, String>>());
                }
                HashMap<String, Pair<Double, String>> beginEnd
                    = scoreBack.get(makeIndex(begin, end));

                for (int split = begin + 1 ; split <= end - 1 ; split ++) {
                    HashMap<String, Pair<Double, String>> beginSplit
                        = scoreBack.get(makeIndex(begin, split));

                    HashMap<String, Pair<Double, String>> splitEnd 
                        = scoreBack.get(makeIndex(split, end));

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


    /* Create a tree given the score and the point backs 
     */
    private Tree<String> buildTree(
            HashMap<String, HashMap<String, Pair<Double, String>>> scoreBack,
            int i, int j, String parent) {
        List<Tree<String>> children = new ArrayList<Tree<String>>();
        Pair<Double, String> back = scoreBack.get(makeIndex(i, j)).get(parent);
        System.out.println("Parent: " + parent);
        System.out.println(back);
        if (back.getSecond().equals("") && back.getFirst() > 0) {
            Tree<String> leaf = new Tree<String>("fake");
            children.add(leaf);
            return new Tree<String>(parent, children);
        }

        String backEntry = back.getSecond();
        if (backEntry.indexOf("]") < 0) {
            children.add(buildTree(scoreBack, i, j, backEntry));
        } else {
            System.out.println(backEntry);
            String[] triple = backEntry.split("\\]");
            System.out.println(triple);
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
