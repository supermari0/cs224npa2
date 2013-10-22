package cs224n.assignment;

import cs224n.assignment.Grammar.UnaryRule;
import cs224n.assignment.Grammar.BinaryRule;
import cs224n.ling.Tree;
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
    }


    public Tree<String> getBestParse(List<String> sentence) {
        double[][][] score = new double[sentence.size()+1][sentence.size()+1]
            [nonterms.size()];
        // This depends on the list of the nonterms staying constant.
        String[][][] back = new String[sentence.size()+1][sentence.size()+1]
            [nonterms.size()];

        fillingNonterminals(score, back, sentence);
        fillingTable(score, back, sentence);
        Tree<String> STree = buildTree(score, back, 0, 
                sentence.size(), nonterms.indexOf("S"));
        List<Tree<String>> child = new ArrayList<Tree<String>>();
        child.add(STree);
        Tree<String> bestParse = new Tree<String>("ROOT", child);
        bestParse.setWords(sentence);
        bestParse = TreeAnnotations.unAnnotateTree(bestParse);
        return bestParse;
    }

    /* The first part of the CKY Algorithm to fill the non-terminal that 
     * become words
     */
    private void fillingNonterminals(double[][][] score, 
            String[][][] back, List<String> sentence) {
        // Score the preterminals.
        for (int i = 0; i < sentence.size(); i++) {
            String word = sentence.get(i);
            for (int a = 0; a < nonterms.size(); a++) {
                String nonterm = nonterms.get(a);
                // https://piazza.com/class/hjz2ma06gdh2hg?cid=142
                
                double scoreTag = lexicon.scoreTagging(word, nonterm);
                if (scoreTag > 0) {
                    score[i][i+1][a] = scoreTag; 
                }
            }
            // Handle unaries.
            boolean added = true;

            while (added) {
                added = false;

                for (int b = 0; b < nonterms.size(); b++) {

                    List<UnaryRule> unaryRules =
                      grammar.getUnaryRulesByChild(nonterms.get(b));

                    if (score[i][i+1][b] > 0) {

                        for (int k = 0; k < unaryRules.size(); k++) {

                            UnaryRule unaryRule = unaryRules.get(k);
                            // Find the index into the score array for the parent
                            // of the unary rule.
                            int parentIndex = nonterms.indexOf(unaryRule.getParent());

                            double prob = unaryRule.getScore() *
                                score[i][i+1][b];

                            if (prob > score[i][i+1][parentIndex]) {
                                score[i][i+1][parentIndex] = prob;
                                back[i][i+1][parentIndex] = "" + b;
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
    private void fillingTable(double[][][] score, 
            String[][][] back, List<String> sentence) {
        // Loop for creating span numbers 
        for (int span = 1 ; span <= sentence.size() ; span++) {
            for (int begin = 0 ; begin <= sentence.size() - span; begin++) {
                int end = begin + span;

                for (int split = begin + 1 ; split <= end - 1 ; split ++) {

                    for (int b = 0 ; b < nonterms.size() ; b++) {
                        String BString = nonterms.get(b); 
                        List<BinaryRule> rules = 
                            grammar.getBinaryRulesByLeftChild(BString); 
                        
                        for (BinaryRule rule : rules) {
                            String AString = rule.getParent();
                            int a = nonterms.indexOf(AString);
                            String CString = rule.getRightChild();
                            int c = nonterms.indexOf(CString);

                            double prob = score[begin][split][b] * 
                                score[split][end][c] * rule.getScore(); 

                            if (prob > score[begin][end][a]) {
                                score[begin][end][a] = prob;
                                back[begin][end][a] = split + "." + 
                                    b + "." + c;
                            }
                        }
                    }
                }

                // Handles unaries
                boolean added = true;
                while (added) {
                    added = false;
                    for (int b = 0; b < nonterms.size(); b++) {
                        List<UnaryRule> unaryRules =
                          grammar.getUnaryRulesByChild(nonterms.get(b));
                        for (UnaryRule rule : unaryRules) {
                            double prob = rule.getScore() * score[begin][end][b];
                            int a = nonterms.indexOf(rule.getParent());
                            if (prob > score[begin][end][a]) {
                                score[begin][end][a] = prob;
                                back[begin][end][a] = "" + b;
                                added = true;
                            }
                        }
                    }
                }
            }
        }
    }

    /* Create a tree given the score and the point backs 
     */
    private Tree<String> buildTree(double[][][] score, String[][][] back,
            int i, int j, int parent) {
        List<Tree<String>> children = new ArrayList<Tree<String>>();
        String backEntry = back[i][j][parent];
        if (backEntry == null && score[i][j][parent] > 0) {
            Tree<String> leaf = new Tree<String>("fake");
            children.add(leaf);
            return new Tree<String>(nonterms.get(parent), children);
        }
        if (backEntry.indexOf(".") < 0) {
            children.add(buildTree(score, back, i, j, Integer.parseInt(backEntry)));
        } else {
            String[] triple = backEntry.split("\\.");
            Tree<String> leftSubtree = buildTree(score, back, i,
                    Integer.parseInt(triple[0]), Integer.parseInt(triple[1]));
            children.add(leftSubtree);
            Tree<String> rightSubtree = buildTree(score, back, 
                    Integer.parseInt(triple[0]), j, Integer.parseInt(triple[2]));
            children.add(rightSubtree);
        }

        return new Tree<String>(nonterms.get(parent), children);
    }

    /* Helper functions to print out the scores and back values so far
     */
    private void printScore(double[][][] score) {
        for (int i = 0 ; i < score.length ; i ++) {
            for (int j = 0 ; j < score[i].length ; j++) {
                for (int k = 0 ; k < score[i][j].length ; k++) {
                    System.out.println("(" + i + "," + j + "," + k + ") " + score[i][j][k]);
                }
            }
        }
    }

    private void printBack(String[][][] back) {
        for (int i = 0 ; i < back.length ; i ++) {
            for (int j = 0 ; j < back[i].length ; j++) {
                for (int k = 0 ; k < back[i][j].length ; k++) {
                    System.out.println("(" + i + "," + j + "," + k + ") " + back[i][j][k]);
                }
            }
        }
    }
}
