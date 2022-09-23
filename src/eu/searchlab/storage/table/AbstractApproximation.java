/**
 *  AbstractApproximation
 *  Copyright 22.09.2022 by Michael Peter Christen, @orbiterlab
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


package eu.searchlab.storage.table;

public abstract class AbstractApproximation implements Approximation {

    private final String source;
    private final int from, until, maxValues;

    public AbstractApproximation(final String source, final int from /*including*/, final int until /*excluding*/) {
        this.source = source;
        this.from = from;
        this.until = until;
        this.maxValues = until - from;
    }

    @Override
    public String source() {
        return this.source;
    }

    @Override
    public int from() {
        return this.from;
    }

    @Override
    public int until() {
        return this.until;
    }

    @Override
    public int maxValues() {
        return this.maxValues;
    }

    @Override
    public double r() {
        double r = 0.0d;
        for (int i = from(); i < until(); i++) {
            final double u = approx(i);
            final double v = trueval(i);
            r += (u - v) / u;
        }
        return r;
    }

    @Override
    public double r2() {
        double r2 = 0.0d;
        for (int i = from(); i < until(); i++) {
            final double u = approx(i);
            final double v = trueval(i);
            r2 += (u - v) * (u - v);
        }
        return r2;
    }

}
