/**
 *  Authorization
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

package eu.searchlab.aaaaa;

import java.io.IOException;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.table.MinuteSeriesTable;
import eu.searchlab.storage.table.TableParser;

public class AuthorizationTS {

    private final static String[] authorizationViewColNames = new String[] {"view.user_id"};
    private final static String[] authorizationMetaColNames = new String[] {"meta.cookie_id"};
    private final static String[] authorizationDataColNames = new String[] {};

    private final ConcurrentIO cio;
    private final IOPath aaaaaIop, authorizationIop, loginIop;
    private MinuteSeriesTable loginTable;
    private long loginTableLoadTime = 0;

    public AuthorizationTS(final GenericIO io, final IOPath aaaaaIop) throws IOException {
        this.cio = new ConcurrentIO(io, 10000);
        this.aaaaaIop = aaaaaIop;
        this.authorizationIop = this.aaaaaIop.append("authorization");
        this.loginIop = this.authorizationIop.append("login.csv");
        loadLoginTable();
    }

    private void loadLoginTable() throws IOException {
        if (this.cio.exists(this.loginIop)) {
            final long lastModified = this.cio.getIO().lastModified(this.loginIop);
            if (lastModified < this.loginTableLoadTime) return;
            this.loginTable = new MinuteSeriesTable(this.cio, this.loginIop, authorizationViewColNames.length, authorizationMetaColNames.length, authorizationDataColNames.length, false);
            if (this.loginTable.viewCols.length != authorizationViewColNames.length ||
                    this.loginTable.metaCols.length != authorizationMetaColNames.length ||
                    this.loginTable.dataCols.length != authorizationDataColNames.length) {
                this.loginTable = new MinuteSeriesTable(authorizationViewColNames, authorizationMetaColNames, authorizationDataColNames, false);
            }
        } else {
            this.loginTable = new MinuteSeriesTable(authorizationViewColNames, authorizationMetaColNames, authorizationDataColNames, false);
        }
        this.loginTableLoadTime = System.currentTimeMillis();
    }

    public void announceAuthorization(final String user_id, final String cookie_id) throws IOException {
        loadLoginTable();
        this.loginTable.addValues(System.currentTimeMillis(),
                new String[] {user_id},
                new String[] {cookie_id},
                new long[] {});
        TableParser.storeCSV(this.cio, this.loginIop, this.loginTable.table.table());
    }

    public String getCookieId(final String user_id) throws IOException {
        loadLoginTable();
        final String[] meta = this.loginTable.getMetaWhere(new String[]{user_id});
        if (meta == null) return null;
        return meta[0];
    }

    public boolean verifyAuthorization(final String user_id, final String cookie_id) throws IOException {
        final String stored_cookie_id = getCookieId(user_id);
        if (stored_cookie_id == null) return false;
        return stored_cookie_id.equals(cookie_id);
    }

}
