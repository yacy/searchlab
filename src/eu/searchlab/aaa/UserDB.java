/**
 *  UserDB
 *  Copyright 19.04.2022 by Michael Peter Christen, @orbiterlab
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

package eu.searchlab.aaa;

import eu.searchlab.storage.io.IOPath;
import io.findify.s3mock.S3Mock;

public class UserDB {

    IOPath aaaBasePath, assignmentBasePath;

    public UserDB(final IOPath aaaBasePath, final IOPath assignmentBasePath) {
        this.aaaBasePath = aaaBasePath;
        this.assignmentBasePath = assignmentBasePath;
    }

    public IOPath getAuthenticationBasePath() {
        return this.aaaBasePath;
    }

    public IOPath getAuthorizationBasePath() {
        return this.aaaBasePath;
    }

    public IOPath getAccountingBasePath() {
        return this.aaaBasePath;
    }

    public IOPath getAssignmentBasePath() {
        return this.assignmentBasePath;
    }

    public static void main(String[] args) {
        final S3Mock api = new S3Mock.Builder().withPort(8001).withInMemoryBackend().build();
        api.start();
    }
}
