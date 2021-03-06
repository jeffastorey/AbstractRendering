package ar.glyphsets.implicitgeometry;

import java.io.Serializable;
import java.util.Map;
import java.util.Map.Entry;

/**Convert a value into a piece of geometry.
 * Geometry can be points, lines, rectangles, shapes, etc.
 * 
 * TODO: Reverse G and I parameters.  General convention has been <input, output> and this violates...
 * 
 * @param <G> Geometry-type returned;
 * @param <I> Input value type
 * **/
public interface Shaper<G,I> extends Serializable {
	/**Create a shape from the passed item.**/
	public G shape (I from);
	
	/**Tagging interface.  Indicates that the shaper implements a simple enough layout
	 * that the maximum/minimum values for each field will give a correct bounding box. 
	 */
	public static interface SafeApproximate<G,I> extends Shaper<G,I> {}
	
	
	/**Given a map entry, return the value.  Used for maps where the key determines the shape
	 * and the value determines the info.
	 * @author jcottam
	 * @param <V>
	 */
	public static final class MapValue<K,G> implements Shaper<G, Map.Entry<K,G>> {
		@Override public G shape(Entry<K, G> from) {return from.getValue();}
	}

	/**Given a map entry, return the key.  Used for maps where the key determines the info
	 * and the value determines the shape.
	 * @author jcottam
	 * @param <V>
	 */
	public static final class MapKey<G,V> implements Shaper<G, Map.Entry<G, V>> {
		@Override public G shape(Entry<G, V> from) {return from.getKey();}
	}
}