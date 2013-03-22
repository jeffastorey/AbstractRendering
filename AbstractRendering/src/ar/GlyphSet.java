package ar;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.Shape;
import java.util.Collection;

public interface GlyphSet {
	
	public Collection<Glyph> containing(Point2D p);
	public boolean isEmpty();
	public int size();
	public Rectangle2D bounds();
	public boolean add(Glyph g);
	
	public static final class Glyph {
		private static int IDCOUNTER=0;
		public final Shape shape;
		public final Color color;
		public final Integer id = IDCOUNTER++;
		public Glyph(Shape shape, Color color) {this.shape=shape; this.color=color;}
		public boolean equals(Object other) {
			return (other instanceof Glyph) && id.equals(((Glyph) other).id);
		}
		public int hashCode() {return id.hashCode();}
		
	}
}
