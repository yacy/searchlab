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

package eu.searchlab.aaaaa;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import eu.searchlab.storage.io.ConcurrentIO;
import eu.searchlab.storage.io.GenericIO;
import eu.searchlab.storage.io.IOPath;
import eu.searchlab.storage.json.ImmutableTray;
import eu.searchlab.storage.json.PersistentTray;
import eu.searchlab.storage.json.Tray;
import eu.searchlab.tools.Logger;
import io.findify.s3mock.S3Mock;

public class UserDB {

    private final static String AUTHENTICATION_PATH = "authn.json"; // who is the user & identification
    private final static String AUTHORIZATION_PATH  = "authr.json"; // what is the user allowed to do
    private final static String ACCOUNTING_PATH     = "acctg.json"; // what has the user done / statistics
    private final static String AUDIT_PATH          = "audit.json"; // what has the user done / timeline
    private final static String ASSIGNMENT_PATH     = "asgmt.json"; // what is due to be done (technical)

    private final GenericIO aaaIO, assignmentIO;
    private final ConcurrentIO aaaCIO, assignmentCIO;
    private final IOPath authnPath, authrPath, acctgPath, asgmtPath, auditPath;
    private final Tray authnDB, authrDB, acctgDB, asgmtDB, auditDB;


    public UserDB(final GenericIO aaaIO, final GenericIO assignmentIO, final IOPath basePath) {
        this.aaaIO = aaaIO;
        this.assignmentIO = assignmentIO;
        this.aaaCIO = new ConcurrentIO(this.aaaIO, 10000);
        this.assignmentCIO = new ConcurrentIO(this.assignmentIO, 1000);
        this.authnPath = basePath.append(AUTHENTICATION_PATH);
        this.authrPath = basePath.append(AUTHORIZATION_PATH);
        this.acctgPath = basePath.append(ACCOUNTING_PATH);
        this.auditPath = basePath.append(AUDIT_PATH);
        this.asgmtPath = basePath.append(ASSIGNMENT_PATH);
        this.authnDB = new PersistentTray(this.aaaCIO, this.authnPath);
        this.authrDB = new ImmutableTray(this.aaaCIO, this.authrPath);
        this.acctgDB = new PersistentTray(this.aaaCIO, this.acctgPath);
        this.auditDB = new PersistentTray(this.aaaCIO, this.auditPath);
        this.asgmtDB = new PersistentTray(this.assignmentCIO, this.asgmtPath);
    }

    public GenericIO getAuthenticationIO() {
        return this.aaaIO;
    }

    public GenericIO getAuthorizationIO() {
        return this.aaaIO;
    }

    public GenericIO getAccountingIO() {
        return this.aaaIO;
    }

    public GenericIO getAuditIO() {
        return this.aaaIO;
    }

    public GenericIO getAssignmentIO() {
        return this.assignmentIO;
    }

    public IOPath getAuthenticationPath() {
        return this.authnPath;
    }

    public IOPath getAuthorizationPath() {
        return this.authrPath;
    }

    public IOPath getAccountingPath() {
        return this.acctgPath;
    }

    public IOPath getAuditPath() {
        return this.auditPath;
    }

    public IOPath getAssignmentPath() {
        return this.asgmtPath;
    }

    /**
     * store an authentication object.
     * This is usually only called when a user logs in with an authentication service.
     * It should not be called with temporary authentications like such with self-assigned user ids
     * @param authn
     * @throws IOException
     */
    public void setAuthentication(final Authentication authn) throws IOException {
        this.authnDB.put(authn.getID(), authn.getJSON());
    }

    public Authentication getAuthentiationByID(final String id) {
        try {
            final JSONObject json = this.authnDB.getObject(id);
            return json == null ? null : new Authentication(json);
        } catch (final JSONException | IOException e) {
            Logger.error(e);
            return null;
        }
    }

    /**
     * get the authentication object by email
     * @param email
     * @return the authentication object if one exist or NULL otherwise
     * @throws IOException
     */
    public Authentication getAuthentiationByEmail(final String email) throws IOException {
        try {
            for (final String id: this.authnDB.keys()) {
                final Authentication a = new Authentication(this.authnDB.getObject(id));
                if (a.getEmail().equals(email)) {
                    return a;
                }
            }
        } catch (final JSONException e) {
            throw new IOException(e.getMessage());
        }
        return null;
    }

    public void setAuthorization(final Authorization authr) throws IOException {
        this.authrDB.put(authr.getSessionID(), authr.getJSON());
    }

    public Authorization getAuthorization(final String sessionID) throws IOException {
        if (sessionID == null) return null;
        try {
            final JSONObject json = this.authrDB.getObject(sessionID);
            if (json == null) return null;
            return new Authorization(json);
        } catch (final JSONException e) {
            Logger.error(e);
            return null;
        }
    }

    /**
     * delete authorization to log out the user
     * @param sessionID
     */
    public void deleteAuthorization(final String sessionID) {
        if (sessionID == null) return;
        try {
            this.authrDB.remove(sessionID);
        } catch (final IOException e) {
            Logger.error(e);
        }

    }

    /**
     * delete authentication to remove user and delete the account
     * @param user_id
     */
    public void deleteAuthentication(final String user_id) {
        try {
            this.authnDB.remove(user_id);
        } catch (final IOException e) {
            Logger.error(e);
        }
    }

    public static void main(final String[] args) {
        final S3Mock api = new S3Mock.Builder().withPort(8001).withInMemoryBackend().build();
        api.start();
    }
}
