/**
 * This software is released under the University of Illinois/Research and Academic Use License. See
 * the LICENSE file in the root folder for details. Copyright (c) 2016
 *
 * Developed by: The Cognitive Computation Group University of Illinois at Urbana-Champaign
 * http://cogcomp.cs.illinois.edu/
 * 
 * Edited by Zewei Chu, 2018-3-16
 * Adding 20newsgroups dataset for evaluation
 */
package edu.illinois.cs.cogcomp.datalessclassification;

import org.junit.Test;

import edu.illinois.cs.cogcomp.annotation.AnnotatorException;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.utilities.configuration.ResourceManager;
import edu.illinois.cs.cogcomp.datalessclassification.config.ESADatalessConfigurator;
import edu.illinois.cs.cogcomp.datalessclassification.ta.ADatalessAnnotator;
import edu.illinois.cs.cogcomp.datalessclassification.ta.ESADatalessAnnotator;
import edu.illinois.cs.cogcomp.nlp.tokenizer.StatefulTokenizer;
import edu.illinois.cs.cogcomp.nlp.utility.TokenizerTextAnnotationBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author shashank
 */

class Pair<L,R> {

	  private final L left;
	  private final R right;

	  public Pair(L left, R right) {
	    this.left = left;
	    this.right = right;
	  }

	  public L getLeft() { return left; }
	  public R getRight() { return right; }

	  @Override
	  public int hashCode() { return left.hashCode() ^ right.hashCode(); }

	  @Override
	  public boolean equals(Object o) {
	    if (!(o instanceof Pair)) return false;
	    Pair pairo = (Pair) o;
	    return this.left.equals(pairo.getLeft()) &&
	           this.right.equals(pairo.getRight());
	  }

	}

public class ESA20NewsTest {
    private String configFile;
    private ESADatalessAnnotator dataless;
    
    private String classFile = "/home/yijingxiao/research/data/20newsgroups/classes.esa.txt";
    private String testFile = "/home/yijingxiao/research/data/20newsgroups/test.csv";
    private List<String> classes;
    private List<Pair<String, Integer>> data;
    
    
    
    
    private List<String> documents;
    private List<Set<String>> docLabels;
    

    @Test
    public void testPredictions() {
        try {
        	
        	this.readClasses();
        	
//        	for (String s: this.classes) {
//        		System.out.println(s);
//        	}
        	
        	this.readData();
        	
//        	for (Pair<String, Integer> p: this.data) {
//        		System.out.println(p.getLeft());
//        		System.out.println(this.classes.get(p.getRight()));
//        		
//        	}
        	
            configFile = "config/project.properties";

            ResourceManager nonDefaultRm = new ResourceManager(configFile);
            ResourceManager rm = new ESADatalessConfigurator().getConfig(nonDefaultRm);
            dataless = new ESADatalessAnnotator(rm);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("IO Error while initializing the annotator .. " + e.getMessage());
            fail("IO Error while initializing the annotator .. " + e.getMessage());
        }

        try {
        	int correct_count = 0, total_count = 0;
            for (int i = 0; i < this.data.size(); i++) {
                // String docText = getDocumentText(docPaths.get(i));
                Pair<String, Integer> p = this.data.get(i);
                
                Set<String> docPredictions = getPredictions(getTextAnnotation(p.getLeft()), dataless);

                System.out.println("Doc" + i + ": Gold LabelIDs:");
                String gold = this.classes.get(p.getRight());
                System.out.println(gold);
                
                System.out.println("Doc" + i + ": Predicted LabelIDs:");
                String predicted = getLastElement(docPredictions);
                System.out.println(predicted);

                if (gold.equals(predicted)) {
                	correct_count++;
                }
                total_count ++;
                System.out.println();
            }
            System.out.println("correct prediction: " + correct_count + "accuracy: " + correct_count * 1.0 / total_count);
        } catch (AnnotatorException e) {
            e.printStackTrace();
            System.out.println("Error annotating the document .. " + e.getMessage());
            fail("Error annotating the document .. " + e.getMessage());
        }
    }
    
    public static <T> T getLastElement(final Iterable<T> elements) {
        final Iterator<T> itr = elements.iterator();
        T lastElement = itr.next();

        while(itr.hasNext()) {
            lastElement=itr.next();
        }

        return lastElement;
    }

    private boolean checkSetEquality(Set<String> goldLabels, Set<String> predictedLabels) {
        if (goldLabels.size() != predictedLabels.size())
            return false;

        for (String goldLabel : goldLabels) {
            if (predictedLabels.contains(goldLabel) == false)
                return false;
        }

        return true;
    }

    private String getDocumentText(String testFile){
        try(BufferedReader br = new BufferedReader(new FileReader(new File(testFile)))) {

            StringBuilder sb = new StringBuilder();

            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append(" ");
            }

            String text = sb.toString().trim();

            return text;
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("IO Error while reading the test file from " + testFile + " .. " + e.getMessage());
            throw new RuntimeException("IO Error while reading the test file from " + testFile + " .. " + e.getMessage());
        }
    }

    private TextAnnotation getTextAnnotation(String text) {
        TokenizerTextAnnotationBuilder taBuilder =
                new TokenizerTextAnnotationBuilder(new StatefulTokenizer());
        TextAnnotation ta = taBuilder.createTextAnnotation(text);

        return ta;
    }

    private Set<String> getPredictions(TextAnnotation ta, ADatalessAnnotator annotator)
            throws AnnotatorException {
        List<Constituent> annots = annotator.getView(ta).getConstituents();

        Set<String> predictedLabels = new HashSet<>();

        for (Constituent annot : annots) {
            String label = annot.getLabel();
//            System.out.println(annot.getTextAnnotation().symtab);
            predictedLabels.add(label);
        }

        return predictedLabels;
    }
    
    private void readClasses() {

    	this.classes = new ArrayList<String>();
    	try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(this.classFile)))){

	    	String strLine;
	
	    	//Read File Line By Line
	    	while ((strLine = br.readLine()) != null)   {
	    	  // Print the content on the console
	    	  this.classes.add(strLine.trim());
	    	}

	    	//Close the input stream
	    	br.close();
    	} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    // Read test data from 20news dataset
    private void readData() {

    	this.data = new ArrayList<Pair<String, Integer>>();
    	try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(this.testFile)))){

	    	String strLine;
	
	    	//Read File Line By Line
	    	while ((strLine = br.readLine()) != null)   {
	    	  // Print the content on the console
	    		String[] s = strLine.split(",", 2);
	    		this.data.add(new Pair<String, Integer>(s[1], Integer.parseInt(s[0])-1));
	    	}

	    	//Close the input stream
	    	br.close();
    	} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
}
