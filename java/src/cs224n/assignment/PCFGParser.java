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

    public void train(List<Tree<String>> trainTrees) {
        // Binarize the training tree so that rules are at most binary.
        List<Tree<String>> binarizedTrainTrees = new ArrayList<Tree<String>>();

        for (Tree<String> tree : trainTrees) {
          binarizedTrainTrees.add(TreeAnnotations.annotateTree(tree));
        }

        lexicon = new Lexicon(binarizedTrainTrees);
        grammar = new Grammar(binarizedTrainTrees);
}


    public Tree<String> getBestParse(List<String> sentence) {
        // Implements the CKY algorithm for retrieving the best parse.
        Set<String> nontermSet = lexicon.getAllTags();

        // Convert the set of nontermials to a List<String> so that we can keep
        // track of each nonterminal by its index.
        List<String> nonterms = new ArrayList<String>(nontermSet);

        double[][][] score = new double[sentence.size() + 1][sentence.size() +
          1][nonterms.size()];
        // This depends on the list of the nonterms staying constant.
        String[][][] back = new String[sentence.size() + 1][sentence.size() + 1]
            [nonterms.size()];

        fillingNonterminals(score, back, sentence, nonterms);
        fillingTable(score, back, sentence, nonterms);
        Tree<String> STree = buildTree(score, back, sentence.size() - 1, 
                sentence.size() - 1, "s", nonterms);
        List<Tree<String>> child = new ArrayList<Tree<String>>();
        child.add(STree);
        return new Tree<String>("ROOT", child);
    }

    /* The first part of the CKY Algorithm to fill the non-terminal that 
     * become words
     */
    private void fillingNonterminals(double[][][] score, 
            String[][][] back, List<String> sentence, List<String> nonterms) {
        // Score the preterminals.
        for (int i = 0; i < sentence.size(); i++) {
            String word = sentence.get(i);
            for (int j = 0; j < nonterms.size(); j++) {
                String nonterm = nonterms.get(j);
                // TODO How do we check whether there is a production rule for
                // this nonterminal and word? Does it matter? (Probability should
                // be 0 from the Lexicon if there's no instance of nonterm ->
                // word) also see
                // https://piazza.com/class/hjz2ma06gdh2hg?cid=142
                score[i][i+1][j] = lexicon.scoreTagging(word, nonterm);
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

                            if (parentIndex != -1) {
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
    }

    /* The second part of the CKY algorithm which fills the transition
     * rules for non-terminals
     */
    private void fillingTable(double[][][] score, 
            String[][][] back, List<String> sentence, List<String> nonterms) {
        // Loop for creating span numbers 
        for (int span = 2 ; span < sentence.size() ; span++) {
            for (int begin = 0 ; begin < sentence.size() - span ; span++) {
                int end = begin + span;

                for (int split = begin + 1 ; split < end - 1 ; split ++) {
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
            int i, int j, String parentString, List<String> nonterms) {
        int parent = nonterms.indexOf(parentString);
        List<Tree<String>> children = new ArrayList<Tree<String>>();
        String backEntry = back[i][j][parent];
        if (backEntry.indexOf(".") < 0) {
            children.add(buildTree(score, back, i, j, backEntry, nonterms));
        } else {
            String[] triple = backEntry.split("\\.");
            Tree<String> leftSubtree = buildTree(score, back, i,
                    Integer.parseInt(triple[0]), triple[1], nonterms);
            children.add(leftSubtree);
            Tree<String> rightSubtree = buildTree(score, back, 
                    Integer.parseInt(triple[0]), j, triple[2], nonterms);
            children.add(rightSubtree);
        }

        return new Tree<String>(parentString, children);
    }

}
