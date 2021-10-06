/**
 *  IOPath
 *  Copyright 06.10.2021 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.storage.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

public class IOPath {

    private final String project;
    private final List<String> path;

    public IOPath(final String project) {
        this.project = project;
        this.path = new ArrayList<>();
    }

    public IOPath(final File file) throws IOException {
        final Path cpath = file.getCanonicalFile().toPath();
        assert cpath.isAbsolute();
        final String[] a = StreamSupport.stream(cpath.spliterator(), false).map(Path::toString).toArray(String[]::new);
        this.path = Arrays.asList(a);
        this.project = this.path.get(0);
        this.path.remove(0);
    }

    public String getProject() {
        return this.project;
    }

    public IOPath append(final String subpath) {
        this.path.add(subpath);
        return this;
    }

    public byte[] load() {
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("IOPath://").append(this.project);
        this.path.forEach(path -> sb.append('/').append(path));
        return sb.toString();
    }

    public static void main(final String[] args) {
        try {
            final IOPath p = new IOPath(new File("/tmp/a/b/c.gif"));
            System.out.println(p.toString());
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
