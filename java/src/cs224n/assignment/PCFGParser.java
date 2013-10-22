package cs224n.assignment;

import cs224n.assignment.Grammar.UnaryRule;
import cs224n.ling.Tree;
import java.util.*;

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
        // TODO implement back pointers here

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
                for (int j = 0; j < nonterms.size(); j++) {
                    List<UnaryRule> unaryRules =
                      grammar.getUnaryRulesByChild(nonterms.get(j));
                    for (int k = 0; k < unaryRules.size(); k++) {
                        UnaryRule unaryRule = unaryRules.get(k);
                        if (score[i][i+1][j] > 0) {
                            double prob = unaryRule.getScore() *
                              score[i][i+1][j];

                            // Find the index into the score array for the parent
                            // of the unary rule.
                            int parentIndex = -1;
                            String parentNonterm = unaryRule.getParent();
                            for (int l = 0; l < nonterms.size(); l++) {
                                if (nonterms.get(l).equals(parentNonterm)) {
                                    parentIndex = l;
                                }
                            }

                            if (parentIndex != -1) {
                                if (prob > score[i][i+1][parentIndex]) {
                                    score[i][i+1][parentIndex] = prob;
                                    // TODO add back pointer here
                                    added = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        // TODO implement for loop over span starting here - how do we
        // interpret the for A, B, C loop over nonterminals? Consider all
        // possible 3 combinations of A, B, C?

        return null;

    }
}
