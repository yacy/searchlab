/**
 *  AbstractFutureProgress
 *  Copyright 21.10.2022 by Michael Peter Christen, @orbiterlab
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

public abstract class AbstractProgress<T extends Number> implements Progress<T> {

    @SuppressWarnings("unchecked")
    private final T zero = (T) new Number() {
        private static final long serialVersionUID = 1L;
        @Override public int intValue() {return 0;}
        @Override public long longValue() {return 0L;}
        @Override public float floatValue() {return 0.0f;}
        @Override public double doubleValue() {return 0.0d;}
    };

    private T targetNumber = this.zero;
    private T progressNumber = this.zero;
    private final long startTime = System.currentTimeMillis();

    public AbstractProgress() {
        super();
    }

    public AbstractProgress(final T target) {
        super();
        if (target.doubleValue() == 0.0d) throw new RuntimeException("target must not be zero");
        this.targetNumber = target;
    }

    @Override
    public T getTarget() {
        return this.targetNumber;
    }

    public void setTarget(final T number) {
        this.targetNumber = number;
    }

    @Override
    public T getProgress() {
        return this.progressNumber;
    }

    public void setProgress(final T number) {
        this.progressNumber = number;
    }

    @Override
    public int getPercent() {
        if (this.targetNumber.doubleValue() == 0.0d) throw new UnsupportedOperationException("target is zero");
        return (int) Math.floor(this.progressNumber.doubleValue() / this.targetNumber.doubleValue() * 100.0d);
    }

    @Override
    public long getStartTime() {
        return this.startTime;
    }

    @Override
    public double getProgressPerSecond() {
        final long now = System.currentTimeMillis();
        if (now == this.startTime) return Double.NaN;
        return 1000.0d * this.progressNumber.doubleValue() / ((double) (now - this.startTime));
    }

    @Override
    public long getRemainingTime() {
        if (this.targetNumber.doubleValue() == 0.0d) throw new UnsupportedOperationException("target is zero");
        return (long) ((this.targetNumber.doubleValue() - this.progressNumber.doubleValue()) / getProgressPerSecond() * 1000.0d);
    }

    @Override
    public long getTargetTime() {
        return System.currentTimeMillis() + getRemainingTime();
    }

    @Override
    public int compareTo(final Progress<T> o) {
        return Double.compare(this.progressNumber.doubleValue(), o.getProgress().doubleValue());
    }
}
