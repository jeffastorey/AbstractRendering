package ar.rules;

import ar.Aggregates;
import ar.Transfer;

/**Simple mathematical operations encoded as transfer functions
 * @author jcottam
 *
 */
public final class TransferMath {
	private TransferMath() {}

	
	/**Divide all items by a given value.*/
	public static class DivideInt implements Transfer.Specialized<Integer, Integer> {
		private final int denominator;
		
		
		/**@param denominator Value to divide by*/
		public DivideInt(int denominator) {this.denominator = denominator;}
		public Integer emptyValue() {return 0;}
		public ar.Transfer.Specialized<Integer, Integer> specialize(
				Aggregates<? extends Integer> aggregates) {return this;}
		public Integer at(int x, int y, Aggregates<? extends Integer> aggregates) {
			int val = aggregates.get(x, y).intValue()/denominator;
			return val;
		}
		
	}
}
