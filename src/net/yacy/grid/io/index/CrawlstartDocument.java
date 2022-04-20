/**
 *  CrawlstartDocument
 *  Copyright 9.3.2018 by Michael Peter Christen, @orbiterlab
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

package net.yacy.grid.io.index;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

public class CrawlstartDocument extends Document {

    public CrawlstartDocument() {
        super();
    }

    public CrawlstartDocument(final Map<String, Object> map) {
        super(map);
    }

    public CrawlstartDocument(final JSONObject obj) {
        super(obj);
    }

    public CrawlstartDocument setCrawlID(final String crawl_id) {
        try {this.putString(CrawlstartMapping.crawl_id_s, crawl_id);} catch (final JSONException e) {}
        return this;
    }

    public String getCrawlID() {
        return this.getString(CrawlstartMapping.crawl_id_s, "");
    }

    public CrawlstartDocument setUserID(final String user_id) {
        try {this.putString(CrawlstartMapping.user_id_s, user_id);} catch (final JSONException e) {}
        return this;
    }

    public String getUserID() {
        return this.getString(CrawlstartMapping.user_id_s, "");
    }

    public CrawlstartDocument setMustmatch(final String mustmatch) {
        try {this.putString(CrawlstartMapping.mustmatch_s, mustmatch);} catch (final JSONException e) {}
        return this;
    }

    public String getMustmatch() {
        return this.getString(CrawlstartMapping.mustmatch_s, "");
    }

    public CrawlstartDocument setCollections(final Collection<String> collections) {
        try {this.putStrings(CrawlstartMapping.collection_sxt, collections);} catch (final JSONException e) {}
        return this;
    }

    public List<String> getCollections() {
        return this.getStrings(CrawlstartMapping.collection_sxt);
    }

    public CrawlstartDocument setCrawlstartURL(final String url) {
        try {this.putString(CrawlstartMapping.start_url_s, url);} catch (final JSONException e) {}
        return this;
    }

    public String getCrawstartURL() {
        return this.getString(CrawlstartMapping.start_url_s, "");
    }

    public CrawlstartDocument setCrawlstartSSLD(final String url) {
        try {this.putString(CrawlstartMapping.start_ssld_s, url);} catch (final JSONException e) {}
        return this;
    }

    public String getCrawstartSSLD() {
        return this.getString(CrawlstartMapping.start_ssld_s, "");
    }

    public CrawlstartDocument setInitDate(final Date date) {
        try {this.putDate(CrawlstartMapping.init_date_dt, date);} catch (final JSONException e) {}
        return this;
    }

    public Date getInitDate() {
        try {return this.getDate(CrawlstartMapping.init_date_dt);} catch (final JSONException e) {}
        return new Date();
    }

    public CrawlstartDocument setData(final JSONObject data) {
        try {this.putObject(CrawlstartMapping.data_o, data);} catch (final JSONException e) {}
        return this;
    }

    public JSONObject getData() {
        return this.getObject(CrawlstartMapping.data_o);
    }

}
