package ar.app.components;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JSpinner;

import ar.Aggregates;
import ar.Transfer;
import ar.renderers.ParallelRenderer;

public class EmergentLegend extends JPanel {
	JSpinner exemplars = new JSpinner();
	
	public <A> EmergentLegend() {
		this.add(new LabeledItem("Exemplars:", exemplars));
	}
	
	public <A> Transfer<A,Color> getTransfer(Transfer<A,Color> base) {
		return new Legend<>(base, (int) exemplars.getValue());
	}
	
	public static class LegendPanel<A> extends JPanel {
		private final List<A> input;
		private final List<Color> output;
		
		private final int width = 20;
		private final int height = 10;
		private final int pad = 3;
		private final int indent = 10;
		
		public LegendPanel(List<A> inputExamples, List<Color> outputExamples) {
			this.input = inputExamples;
			this.output = outputExamples;
		}
		
		public void paintComponent(Graphics g) {
			for (int i=0; i< input.size();i++) {
				int y = i*(height+pad);
				g.setColor(output.get(i));
				g.fillRect(indent, y, width, height);
				g.drawChars(
					input.get(i).toString().toCharArray(), 
					0, 
					input.get(i).toString().length(), 
					indent + width + pad,
					y);
			}
		}
		
		
	}
	
	public static class Legend<A> implements Transfer<A,Color> {
		final Transfer<A,Color> base;
		final int exemplars;
		public Legend(Transfer<A,Color> base, int exemplars) {
			this.base = base;
			this.exemplars = exemplars;
		}

		@Override
		public Color emptyValue() {return base.emptyValue();}

		@Override
		public ar.Transfer.Specialized<A, Color> specialize(
				Aggregates<? extends A> aggregates) {
			Transfer.Specialized<A,Color> spec = base.specialize(aggregates);
			return new Specialized<>(spec, exemplars, aggregates);
		}
		
		public static final class Specialized<A> extends Legend<A> implements Transfer.Specialized<A,Color> {
			final Transfer.Specialized<A,Color> base;
			final Aggregates<Color> result;
			final Aggregates<? extends A> input;
			final List<A> inputExamples;
			final List<Color> outputExamples;
			
			public Specialized(Transfer.Specialized<A, Color> base, int exemplars, Aggregates<? extends A> aggregates) {
				super(base, exemplars);
				this.base = base;
				input = aggregates;
				result = new ParallelRenderer().transfer(aggregates, base);
				
				inputExamples = new ArrayList<>();
				outputExamples = new ArrayList<>();
				
				//TODO: Investigate more interesting ways to do this selection...
				for (int i=0; i< exemplars; i++) {
					int x = (int) (aggregates.lowX() + (Math.random()*(aggregates.highX()-aggregates.lowX())));
					int y = (int) (aggregates.lowY() + (Math.random()*(aggregates.highY()-aggregates.lowY())));
					
					inputExamples.add(aggregates.get(x,y));
					outputExamples.add(result.get(x, y));
				}
				
			}

			@Override
			public Color at(int x, int y, Aggregates<? extends A> aggregates) {return result.get(x,y);}
			
			public JPanel getLegend() {
				return new LegendPanel<>(inputExamples, outputExamples);
			}
			
		}
	}
}
