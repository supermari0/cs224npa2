package cs224n.assignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cs224n.ling.Tree;
import cs224n.ling.Trees;
import cs224n.ling.Trees.MarkovizationAnnotationStripper;
import cs224n.util.Filter;

/**
 * Class which contains code for annotating and binarizing trees for
 * the parser's use, and debinarizing and unannotating them for
 * scoring.
 */
public class TreeAnnotations {

	public static Tree<String> annotateTree(Tree<String> unAnnotatedTree) {
		// Currently, the only annotation done is a lossless binarization

		// change the annotation from a lossless binarization to a
		// finite-order markov process (try at least 1st and 2nd order)

		// mark nodes with the label of their parent nodes, giving a second
		// order vertical markov process

        secondVMarkov(unAnnotatedTree);
        //thirdVMarkov(unAnnotatedTree);

        unAnnotatedTree = binarizeTree(unAnnotatedTree);
        //firstHMarkov(unAnnotatedTree);
        //secondHMarkov(unAnnotatedTree);
		return unAnnotatedTree;
	}

    /* 
     * Function given a tree, creates the second
     * Vertical Markov by recursively adding the current
     * label to the children's labels
     */
    private static void secondVMarkov(Tree<String> copy) {
        if (!copy.isPreTerminal() && !copy.isLeaf()) {
            String label = copy.getLabel().split("\\^")[0];
            for (Tree<String> child : copy.getChildren()) {
                String childLabel = child.getLabel();
                child.setLabel(childLabel + "^" + label);
                secondVMarkov(child);
            }
        }
    }

    /* 
     * Function given a tree, creates the third
     * Vertical Markov by calling the secondVMarkov
     * then calling the recursive helper function to add
     * the current label to the grandchildren's label
     */
    private static void thirdVMarkov(Tree<String> copy) {
        secondVMarkov(copy);
        thirdVMarkovHelper(copy);
    }

    private static void thirdVMarkovHelper(Tree<String> copy) {
        if (!copy.isPreTerminal() && !copy.isLeaf()) {
            String[] label = copy.getLabel().split("\\^");
            for (Tree<String> child : copy.getChildren()) {
                String childLabel = child.getLabel().split("\\^")[0];
                if (label.length == 1) {
                    child.setLabel(childLabel + "^" + label[0]);
                } else {
                    child.setLabel(childLabel + "^" + 
                            label[0] + "^" + label[1]);
                }
                thirdVMarkovHelper(child);
            }
        }
    }

    /*
     * Transforms an infinite Horizontal Markovization 
     * binary tree into a 1st-order Horizontal markovization by
     * only keeping the right-most non-terminal of the siblings.
     * Recurse into leaf nodes.
     */
    private static void firstHMarkov(Tree<String> copy) {
        if (!copy.isLeaf()) {
            if (copy.getLabel().indexOf("->") > -1) {
                String[] leftRight = copy.getLabel().split("\\-\\>");
                String right = leftRight[1];
                String[] siblings = right.split("\\_");
                int sizeSibs = siblings.length;
                copy.setLabel(leftRight[0] + "->..._" + 
                        siblings[sizeSibs - 1]);
            }
            for (Tree<String> child : copy.getChildren()) {
                firstHMarkov(child);
            }
        }
    }

    /*
     * Transforms an infinite-order Horizontal Markovization
     * binary tree into a 2nd-order Horizontal Markovization by
     * only keeping the right-two most non-terminals of the
     * siblings. Again, recurse into leaf nodes.
     */
    private static void secondHMarkov(Tree<String> copy) {
        if (!copy.isLeaf()) {
            if (copy.getLabel().indexOf("->") > -1) {
                String[] leftRight = copy.getLabel().split("\\-\\>");
                String right = leftRight[1];
                if (right.indexOf("_", 1) != right.lastIndexOf("_")) {
                    String[] siblings = right.split("\\_");
                    int sizeSibs = siblings.length;
                    copy.setLabel(leftRight[0] + "->..._" + 
                            siblings[sizeSibs - 2] + "_" +
                            siblings[sizeSibs - 1]);
                }
            }
            for (Tree<String> child : copy.getChildren()) {
                secondHMarkov(child);
            }
        }
    }

	private static Tree<String> binarizeTree(Tree<String> tree) {
		String label = tree.getLabel();
		if (tree.isLeaf())
			return new Tree<String>(label);
		if (tree.getChildren().size() == 1) {
			return new Tree<String>
			(label, 
					Collections.singletonList(binarizeTree(tree.getChildren().get(0))));
		}
		// otherwise, it's a binary-or-more local tree, 
		// so decompose it into a sequence of binary and unary trees.
		String intermediateLabel = "@"+label+"->";
		Tree<String> intermediateTree =
				binarizeTreeHelper(tree, 0, intermediateLabel);
		return new Tree<String>(label, intermediateTree.getChildren());
	}

	private static Tree<String> binarizeTreeHelper(Tree<String> tree,
			int numChildrenGenerated, 
			String intermediateLabel) {
		Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
		List<Tree<String>> children = new ArrayList<Tree<String>>();
		children.add(binarizeTree(leftTree));
		if (numChildrenGenerated < tree.getChildren().size() - 1) {
			Tree<String> rightTree = 
					binarizeTreeHelper(tree, numChildrenGenerated + 1, 
							intermediateLabel + "_" + leftTree.getLabel());
			children.add(rightTree);
		}
		return new Tree<String>(intermediateLabel, children);
	} 

	public static Tree<String> unAnnotateTree(Tree<String> annotatedTree) {

		// Remove intermediate nodes (labels beginning with "@"
		// Remove all material on node labels which follow their base symbol
		// (cuts at the leftmost - or ^ character)
		// Examples: a node with label @NP->DT_JJ will be spliced out, 
		// and a node with label NP^S will be reduced to NP

		Tree<String> debinarizedTree =
				Trees.spliceNodes(annotatedTree, new Filter<String>() {
					public boolean accept(String s) {
						return s.startsWith("@");
					}
				});
		Tree<String> unAnnotatedTree = 
				(new Trees.FunctionNodeStripper()).transformTree(debinarizedTree);
    Tree<String> unMarkovizedTree =
        (new Trees.MarkovizationAnnotationStripper()).transformTree(unAnnotatedTree);
		return unMarkovizedTree;
	}
}
