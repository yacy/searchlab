/**
 *  Cons
 *  Copyright 07.10.2022 by Michael Peter Christen, @orbiterlab
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.searchlab.tools;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Pair class in the style of Lisp Cons.
 */
public class Cons<H extends Comparable<H>, T> implements Map.Entry<H, T>, Comparable<Cons<H, T>>, Serializable {

    private static final long serialVersionUID = 7601891574530857595L;

    public final H car;
    public T cdr;

    /**
     * make a cons pair with a key and a value
     * @param key as head
     * @param value as tail
     */
    public Cons(final H key, final T value) {
        super();
        this.car = key;
        this.cdr = value;
    }

    @Override
    public final H getKey() {
        return this.car;
    }

    public final H car() {
        return this.car;
    }

    public final static <H extends Comparable<H>> H car(final Cons<H, ?> c) {
        return c.car;
    }

    @Override
    public T getValue() {
        return this.cdr;
    }

    public T cdr() {
        return this.cdr;
    }

    public final static <T> T cdr(final Cons<?, T> c) {
        return c.cdr;
    }

    @Override
    public T setValue(final T value) {
        final T old = this.cdr;
        this.cdr = value;
        return old;
    }

    /**
     * this compare implementation does only compare the head but not the tail
     */
    @Override
    public int compareTo(final Cons<H, T> other) {
        return this.car.compareTo(other.car);
    }

    /**
     * this equals implementation does only compare the head but not the tail
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj instanceof Map.Entry<?, ?>) {
            @SuppressWarnings("unchecked")
            final Map.Entry<H, T> other = (Map.Entry<H, T>) obj;
            return Objects.equals(this.car, other.getKey());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (this.car == null ? 0 : this.car.hashCode()) ^ (this.cdr == null ? 0 : this.cdr.hashCode());
    }

    @Override
    public String toString() {
        return "(" + this.car + ',' + this.cdr + ')';
    }

    /**
     * convenience method to make a generic cons pair
     * @param <H>
     * @param <T>
     * @param key
     * @param value
     * @return a cons pair
     */
    public static <H extends Comparable<H>, T> Cons<H, T> of(final H key, final T value) {
        return new Cons<H, T>(key, value);
    }

    /**
     * convenience method to make a String/String key/value pair
     * @param key
     * @param value
     * @return a cons pair
     */
    public static Cons<String, String> of(final String key, final String value) {
        return new Cons<String, String>(key, value);
    }

    /**
     * convience method to make a lisp-like cons-chain
     * @param <A> an atom type for the values in the cons-chain
     * @param <C> the cons-chain
     * @param key the head of the chain
     * @param value the tail of the chain
     * @return a cons pair
     */
    public static <A extends Comparable<A>, C extends Cons<A, ?>> Cons<A, Cons<A, ?>> of(final A key, final Cons<A, C> value) {
        return new Cons<A, Cons<A, ?>>(key, value);
    }

    /**
     * convenient method to make a chained list of cons from a given list
     * @param <A> an atom type for the values in the cons-chain
     * @param <C> the cons-chain
     * @param values the elements in the list
     * @return a list consisting of cons pairs
     */
    public static <A extends Comparable<A>, C extends Cons<A, ?>> Cons<A, Cons<A, ?>> list(final A... values) {
        return list(values, 0);
    }

    private static <A extends Comparable<A>, C extends Cons<A, ?>> Cons<A, Cons<A, ?>> list(final A[] values, final int idx) {
        if (idx >= values.length) return null;
        return of(values[idx], list(values, idx + 1));
    }

}