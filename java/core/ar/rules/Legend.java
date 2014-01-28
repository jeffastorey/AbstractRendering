package ar.rules;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridLayout;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import ar.Aggregates;
import ar.Renderer;
import ar.Transfer;
import ar.rules.combinators.Seq;
import ar.util.Util;


/**Compares a set of aggregates with the color output and produces a color map input to output.
 * 
 * Input/output values are assumed to exactly align (so in.get(x,y) corresponds to out.get(x,y)).
 * Therefore, this can only be used on transfer chains that maintain that correspondence.
 * 
 * TODO: Legend building currently happens at specialization time...should it be done at transfer time instead?
 * TODO: The auto-watcher reveals some problems with Specialized extending Transfer...perhaps Specialized should not! 
 * 
 * @param <A> Input type
 */
public class Legend<A> implements Transfer<A, Color> {
	final Transfer<A,Color> basis;
	final Formatter<A> formatter;
	
	public Legend(Transfer<A,Color> basis, Formatter<A> formatter) {
		this.basis = basis;
		this.formatter = formatter;
		
		if (basis == null) {throw new IllegalArgumentException("Must supply non-null transfer as a basis for building legends.");}
	}
	
	@Override public Color emptyValue() {return basis.emptyValue();}

	@Override
	public Specialized<A> specialize(Aggregates<? extends A> aggregates) {
		return new Specialized<A>(basis, formatter, aggregates);
	}
	
	public static final class Specialized<A> extends Legend<A> implements Transfer.Specialized<A, Color> {
		final Transfer.Specialized<A,Color> basis;
		final Map<A, Set<Color>> mapping;
 		
		public Specialized(Transfer<A, Color> rootBasis, Formatter<A> formatter, Aggregates<? extends A> inAggs) {
			super(rootBasis, formatter);	

			this.basis = rootBasis.specialize(inAggs);
			Map<A,Set<Color>> rawList = formatter.holder();

			Aggregates<Color> outAggs = Seq.SHARED_RENDERER.transfer(inAggs, basis);
			for (int x=inAggs.lowX(); x<inAggs.highX(); x++) {
				for (int y=inAggs.lowY(); y<inAggs.highY(); y++) {
					A in = inAggs.get(x, y);					
					if (!rawList.containsKey(in)) {rawList.put(in, new TreeSet<>(Util.COLOR_SORTER));}
					Set<Color> outs = rawList.get(in);
					outs.add(outAggs.get(x,y));
				}
			}
			this.mapping = formatter.select(rawList);
		}
		
		@Override
		public Aggregates<Color> process(Aggregates<? extends A> aggregates, Renderer rend) {return basis.process(aggregates, rend);}
		
		public JPanel legend() {
			return formatter.display(mapping);
		}
	}
	
	public static final class ColorSwatch extends JPanel {
		private final Color c;
		public ColorSwatch(Color c) {this.c = c;}		
		@Override public void paintComponent(Graphics g) {
			g.setColor(c);
			g.fillRect(0, 0, this.getWidth(), this.getHeight());
		}
	}


	/**Keeps the legend display up to date as the values inside shift.
	 * 
	 * TODO: Shift to an event-based system that fires a "legendChanged" events
	 * */
	public static final class AutoUpdater<A> extends Legend<A> {
		final JComponent host;
		final Object layoutParams;
		private JPanel legend;

		/**
		 * @param basis Legend to watch
		 * @param host Where the legend value should be placed
		 * @param layoutParams How the legend should be added.
		 */
		public AutoUpdater(Transfer<A,Color> basis, Formatter<A> formatter, JComponent host, Object layoutParams) {
			super(basis, formatter);
			this.host = host;
			this.layoutParams = layoutParams;
		}

		@Override public Color emptyValue() {return basis.emptyValue();}
		@Override public Specialized<A> specialize(Aggregates<? extends A> aggregates) {
			Specialized<A> spec = super.specialize(aggregates);
			if (legend != null) {host.remove(legend);}
			legend = spec.legend();
			host.removeAll();
			host.add(legend, layoutParams);
			host.revalidate();
			return spec;
		}		
	}
	
	public static interface Formatter<A> {
		/**Create a container to hold the input/output mappings.**/
		public Map<A, Set<Color>> holder();
		
		/**Select a subset of mappings to actually show in the display.**/
		public Map<A, Set<Color>> select(Map<A, Set<Color>> input);
		
		/**Generate a panel to show the selected mappings.**/
		public JPanel display(Map<A,Set<Color>> exemplars);
	}
	
	
	/**Shows a selection of categorical counts**/
	public static final class FormatCategories<T> implements Formatter<CategoricalCounts<T>> {
		/**How many parts should each category be divided into?
		 * The number of exemplars is approximately dimensions*divisions.*/
		private final int divisions;
		
		public FormatCategories() {this(2);}
		public FormatCategories(int divisions) {this.divisions = divisions;}
		
		@Override
		public Map<CategoricalCounts<T>, Set<Color>> holder() {return new HashMap<>();}

		@Override
		public Map<CategoricalCounts<T>, Set<Color>> select(Map<CategoricalCounts<T>, Set<Color>> input) {
			//Get max/min (and possibly count) for each category
			SortedMap<T,Integer> catMaxs = new TreeMap<>();
			SortedMap<T,Integer> catMins = new TreeMap<>();
			for (Map.Entry<CategoricalCounts<T>, Set<Color>> e: input.entrySet()) {
				CategoricalCounts<T> cats = e.getKey();
				for (int i=0; i<cats.size(); i++) {
					T cat = cats.key(i);
					if (!catMaxs.containsKey(cat)) {catMaxs.put(cat, 0);}
					if (!catMins.containsKey(cat)) {catMins.put(cat, Integer.MIN_VALUE);}
					Integer max = catMaxs.get(cat);
					Integer min = catMins.get(cat);
					max = Math.max(max, cats.count(i));
					min = Math.min(min, cats.count(i));
					catMaxs.put(cat, max);
					catMins.put(cat, min);
				}
			}
			
			assert catMaxs.size() == catMins.size() : "Unequal number of maxes and mins";
			
			
			//Create a partition scheme in each dimension and a storage location			
			//Iterate the input again, keep/replace values in the bins (Reservoir sampling for a single item; select nth-item with probability 1/n) 
			Binner<T> binner = new Binner<>(divisions, catMaxs, catMins);			
			int[] binSize = new int[binner.binCount()]; //How many items have landed in each bin?
			Map<CategoricalCounts<T>,Set<Color>> exemplars = new TreeMap<>(new CategoricalCounts.MangitudeComparator<T>());
			for (Map.Entry<CategoricalCounts<T>, Set<Color>> e: input.entrySet()) {
				int bin = binner.bin(e.getKey());
				binSize[bin] = binSize[bin]+1;
				double replaceTest = Math.random();
				double replaceThreshold = 1/(double) binSize[bin];
				if (replaceTest < replaceThreshold) {exemplars.put(e.getKey(), e.getValue());}
			}

			return exemplars;
		}

		@Override
		public JPanel display(Map<CategoricalCounts<T>, Set<Color>> exemplars) {
			JPanel labels = new JPanel(new GridLayout(0,1));
			JPanel examples = new JPanel(new GridLayout(0,1));			

			for (Map.Entry<CategoricalCounts<T>, Set<Color>> entry: exemplars.entrySet()) {
				labels.add(new JLabel(entry.getKey().toString()));
				JPanel exampleSet = new JPanel(new GridLayout(1,0));
				for (Color c: entry.getValue()) {
					exampleSet.add(new ColorSwatch(c));
				}
				examples.add(exampleSet);
			}
			JPanel legend = new JPanel(new BorderLayout());
			legend.add(labels, BorderLayout.EAST);
			legend.add(examples, BorderLayout.WEST);			
			return legend;
		}
		
		/**Converts a set of categorical counts into a specific bin.**/
		private static final class Binner<T> {
			final int divisions;
			final SortedMap<T,Integer> maxes;
			final SortedMap<T,Integer> mins;
			
			public Binner(int divisions, SortedMap<T,Integer> maxes, SortedMap<T,Integer> mins) {
				this.divisions = divisions;
				this.maxes = maxes;
				this.mins = mins;
			}
			
			/**What bin does a particular categorization land in?*/
			public int bin(CategoricalCounts<T> cats){
				int bin=0;
				for (int i=0; i< cats.size(); i++) {
					T cat = cats.key(i);
					int catIdx = maxes.headMap(cat).size();
					int catMax = maxes.get(cat);
					int catMin = mins.get(cat);
					int count = cats.count(i);
					
					float span = catMax-catMin/((float) divisions);
					int catBin = (int) (count/span); 
					bin = bin + (catBin + (catIdx * divisions));
				}
				return bin;
			}

			/**How many bins are in this dataspace?**/
			public int binCount() {return (int) Math.pow(divisions, maxes.size());}
		}
		
	}
 	
	/**Shows a set of discrete values in a sortable set of inputs.**/
	public static final class DiscreteComparable<A> implements Formatter<A> {
		final Comparator<A> comp;
		final int exemplars;
		
		public DiscreteComparable() {this((Comparator<A>) new Util.ComparableComparator<>(), 10);}
		public DiscreteComparable(Comparator<A> comp, int exemplars) {
			this.comp = comp;
			this.exemplars = exemplars;
		}
		
		@Override public Map<A,Set<Color>> holder() {return new TreeMap<>(comp);}

		@Override
		public Map<A,Set<Color>> select(final Map<A,Set<Color>> input) {
			int size = input.size();
			if (size > exemplars) {
				@SuppressWarnings("unchecked")
				Map.Entry<A,Set<Color>>[] entries = input.entrySet().toArray(new Map.Entry[size]);
				Map<A,Set<Color>> output = holder();
				for (int i=0; i<entries.length; i=i+(size/exemplars-1)) {
					Map.Entry<A, Set<Color>> entry = entries[i];
					output.put(entry.getKey(), entry.getValue()); 
				}
				Map.Entry<A,Set<Color>> last = entries[entries.length-1];
				output.put(last.getKey(), last.getValue());
				return output;
			} else {
				return input;
			}
		}

		@Override
		public JPanel display(Map<A,Set<Color>> exemplars) {
			JPanel labels = new JPanel(new GridLayout(0,1));
			JPanel examples = new JPanel(new GridLayout(0,1));			

			for (Map.Entry<A, Set<Color>> entry: exemplars.entrySet()) {
				labels.add(new JLabel(entry.getKey().toString()));
				JPanel exampleSet = new JPanel(new GridLayout(1,0));
				for (Color c: entry.getValue()) {
					exampleSet.add(new ColorSwatch(c));
				}
				examples.add(exampleSet);
			}
			JPanel legend = new JPanel(new BorderLayout());
			legend.add(labels, BorderLayout.EAST);
			legend.add(examples, BorderLayout.WEST);			
			return legend;
		}
		
	}
}
