package edu.illinois.cs.cogcomp.comma;

import edu.illinois.cs.cogcomp.annotation.AnnotatorException;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.PredicateArgumentView;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TokenLabelView;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TreeView;
import edu.illinois.cs.cogcomp.core.datastructures.trees.TreeParserFactory;
import edu.illinois.cs.cogcomp.nlp.utilities.BasicTextAnnotationBuilder;
import junit.framework.TestCase;

import java.util.Collections;

public class CommaLabelerTest extends TestCase {
    private CommaLabeler classifier;
    private TextAnnotation ta;

    @Override
	public void setUp() throws Exception {
        super.setUp();
        classifier = new CommaLabeler();
        String[] sentence = "Mary , the clever scientist , was walking .".split("\\s+");
        ta = BasicTextAnnotationBuilder.createTextAnnotationFromTokens(Collections.singletonList(sentence));

        TokenLabelView tlv = new TokenLabelView(ViewNames.POS, "Test", ta, 1.0);
        tlv.addTokenLabel(0, "NNP", 1d);
        tlv.addTokenLabel(1, ",", 1d);
        tlv.addTokenLabel(2, "DT", 1d);
        tlv.addTokenLabel(3, "JJ", 1d);
        tlv.addTokenLabel(4, "NN", 1d);
        tlv.addTokenLabel(5, ",", 1d);
        tlv.addTokenLabel(6, "VBD", 1d);
        tlv.addTokenLabel(7, "VBG", 1d);
        tlv.addTokenLabel(8, ". ", 1d);
        ta.addView(ViewNames.POS, tlv);

        TreeView parse = new TreeView(ViewNames.PARSE_STANFORD, "Test", ta, 1.0);
        String treeString = "(ROOT (S (NP (NP (NNP Mary)) (, ,) (NP (DT the) (JJ clever) (NN scientist)) (, ,)) " +
                "(VP (VBD was) (VP (VBG walking)))  (. .)))";
        parse.setParseTree(0, TreeParserFactory.getStringTreeParser().parse(treeString));
        ta.addView(parse.getViewName(), parse);
    }

    public void testGetCommaSRL() throws AnnotatorException {
        // Create the Comma structure
        PredicateArgumentView srlView = (PredicateArgumentView) classifier.getView(ta);
        assertEquals(",:\n    LeftOfSubstitute: Mary\n    RightOfSubstitute: the clever scientist\n,:\n" +
                "    LeftOfSubstitute: the clever scientist\n", srlView.toString());
    }
}
