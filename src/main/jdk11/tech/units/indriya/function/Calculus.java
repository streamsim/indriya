/*
 * Units of Measurement Reference Implementation
 * Copyright (c) 2005-2025, Jean-Marie Dautelle, Werner Keil, Otavio Santana.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 *    and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of JSR-385, Indriya nor the names of their contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package tech.units.indriya.function;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import tech.units.indriya.spi.NumberSystem;

/**
 * Facade for internal number arithmetic.
 * 
 * @author Andi Huber
 * @author Werner Keil
 * @version 1.6.1, February 10, 2025
 * @since 2.0
 */
public final class Calculus {
	
	private static final Logger LOGGER = System.getLogger(Calculus.class.getPackage().getName());
		
	/**
	 * The default MathContext used for BigDecimal calculus.
	 */
	public static final MathContext DEFAULT_MATH_CONTEXT = MathContext.DECIMAL128;

	/**
	 * Exposes (non-final) the MathContext used for BigDecimal calculus.
	 */
	public static MathContext MATH_CONTEXT = DEFAULT_MATH_CONTEXT;
	
	private static NumberSystem currentSystem;
	
    private static final String DEFAULT_NUMBER_SYSTEM = "tech.units.indriya.function.DefaultNumberSystem";

    /**
     * All available {@link NumberSystem NumberSystems} used for Number arithmetic.
     */
    public static List<NumberSystem> getAvailableNumberSystems() {
        List<NumberSystem> systems = new ArrayList<>();
        ServiceLoader<NumberSystem> loader = ServiceLoader.load(NumberSystem.class, NumberSystem.class.getClassLoader());
        loader.forEach(systems::add);
        return systems;
    }

    /**
     * Returns the current {@link NumberSystem} used for Number arithmetic.
     */
    public static NumberSystem currentNumberSystem() {
    	if (currentSystem == null) {
    		currentSystem = getNumberSystem(DEFAULT_NUMBER_SYSTEM);
    	}
        return currentSystem;
    }
    
    /**
     * Sets the current number system
     *
     * @param system
     *          the new current number system.
     * @see #currentNumberSystem
     */
    public static void setCurrentNumberSystem(NumberSystem system) {
    	currentSystem = system;
    }

    /**
     * Returns the given {@link NumberSystem} used for Number arithmetic by (class) name.
     */
    public static NumberSystem getNumberSystem(String name) {
    	final Stream<NumberSystem> providers = ServiceLoader.load(NumberSystem.class)
    		                 .stream()
    		                 .map(Provider::get);
        final List<NumberSystem> systems = providers
                .filter(n -> n.getClass().getName().equals(name))
                .collect(Collectors.toList());
        if (systems.isEmpty()) {
        	throw new IllegalArgumentException("NumberSystem " + name + " not found");
        } else {
        	return systems.get(0);
        }    
    }
    
	/**
	 * Pi calculation with Machin's formula.
	 * 
	 * @see <a href= "http://mathworld.wolfram.com/PiFormulas.html" >Pi Formulas</a>
	 * 
	 */
	static final class Pi {

		private static final BigDecimal TWO = new BigDecimal("2");
		private static final BigDecimal THREE = new BigDecimal("3");
		private static final BigDecimal FOUR = new BigDecimal("4");
		private static final BigDecimal FIVE = new BigDecimal("5");
		private static final BigDecimal TWO_HUNDRED_THIRTY_NINE = new BigDecimal("239");

		/**
	     * Memoization of Pi by number-of-digits,
	     * as used by {@link PowerOfPiConverter} to match Pi's precision with that of
	     * the current {@link MathContext}.
	     */
	    private static final Map<Integer, BigDecimal> piCache = new ConcurrentHashMap<>();
		
	    // this is a utility class, don't instantiate
		private Pi() {}
		
		public static BigDecimal ofNumDigits(int numDigits) {
			
			if(numDigits<=0) {
				throw new IllegalArgumentException("numDigits is required to be greater than zero");
			}
			return piCache.computeIfAbsent(numDigits, key->calculatePi(numDigits));
		}
		
		/**
		 * Calculates Pi up to numDigits. 
		 */
		private static BigDecimal calculatePi(int numDigits) {
	        // adds an arbitrary safety margin of 10 digits to the requested number of digits
            // (this is a guess, without any particular research to back that up)
		    final int calcDigits = numDigits + 10;
            return FOUR
                    .multiply((FOUR.multiply(arccot(FIVE, calcDigits)))
                    .subtract(arccot(TWO_HUNDRED_THIRTY_NINE, calcDigits)))
                    .setScale(numDigits, RoundingMode.DOWN);
		}

		/** Compute arccot via the Taylor series expansion. */
		private static BigDecimal arccot(BigDecimal x, int numDigits) {
			BigDecimal unity = BigDecimal.ONE.setScale(numDigits, RoundingMode.DOWN);
			BigDecimal sum = unity.divide(x, RoundingMode.DOWN);
			BigDecimal xpower = new BigDecimal(sum.toString());
			BigDecimal term = null;
			int nTerms = 0;

			BigDecimal nearZero = BigDecimal.ONE.scaleByPowerOfTen(-numDigits);
			LOGGER.log(Level.DEBUG, ()->"arccot: ARGUMENT=" + x + " (nearZero=" + nearZero + ")");
			boolean add = false;
			// Add one term of Taylor series each time thru loop. Stop looping
			// when _term_
			// gets very close to zero.
			for (BigDecimal n = THREE; term == null || !term.equals(BigDecimal.ZERO); n = n.add(TWO)) {
				if (term != null && term.compareTo(nearZero) < 0)
					break;
				xpower = xpower.divide(x.pow(2), RoundingMode.DOWN);
				term = xpower.divide(n, RoundingMode.DOWN);
				sum = add ? sum.add(term) : sum.subtract(term);
				add = !add;
				if(LOGGER.isLoggable(Level.TRACE)) {
					LOGGER.log(Level.TRACE, "arccot: term=" + term);    
				}
				nTerms++;
			}
			if(LOGGER.isLoggable(Level.DEBUG)) {
				LOGGER.log(Level.DEBUG, "arccot: done. nTerms=" + nTerms);
			}
			return sum;
		}
	}
	
	// -- NORMAL FORM TABLE OF COMPOSITION
	
	private final static Map<Class<? extends AbstractConverter>, Integer> normalFormOrder = new HashMap<>(9);

    public static Map<Class<? extends AbstractConverter>, Integer> getNormalFormOrder() {
        synchronized (normalFormOrder) {
            if(normalFormOrder.isEmpty()) {
                normalFormOrder.put(AbstractConverter.IDENTITY.getClass(), 0);
                normalFormOrder.put(PowerOfIntConverter.class, 1); 
                normalFormOrder.put(RationalConverter.class, 2); 
                normalFormOrder.put(PowerOfPiConverter.class, 3);
                normalFormOrder.put(DoubleMultiplyConverter.class, 4);
                normalFormOrder.put(AddConverter.class, 5);
                normalFormOrder.put(LogConverter.class, 6); 
                normalFormOrder.put(ExpConverter.class, 7);
                normalFormOrder.put(AbstractConverter.Pair.class, 99);        
            }
        }
        
        return Collections.unmodifiableMap(normalFormOrder);
    }	
}
