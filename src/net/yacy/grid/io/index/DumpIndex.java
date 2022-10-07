/**
 *  DumpIndex
 *  Copyright 29.11.2021 by Michael Peter Christen, @orbiterlab
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.elasticsearch.index.query.QueryBuilder;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * A dump index provides a search result from a dump and holds it in-memory only.
 * The index is not available for write operations, all write operations to this
 * index is going into void.
 */
public class DumpIndex implements FulltextIndex {

    private final Map<String, Directory> directories; // storage space for indexes
    private final Map<String, DirectoryReader> indexReaders; // reader for indexed from directory
    private final Map<String, IndexSearcher> dirSearchers; // search function for indexes

    public DumpIndex() throws IOException {
        this.directories = new ConcurrentHashMap<>();
        this.indexReaders = new ConcurrentHashMap<>();
        this.dirSearchers = new ConcurrentHashMap<>();
    }

    private void initIndex(final String indexName) throws IOException {
        if (this.directories.containsKey(indexName)) return;
        final Directory dir  = new ByteBuffersDirectory();
        final DirectoryReader reader = DirectoryReader.open(dir);
        final IndexSearcher searcher = new IndexSearcher(reader);
        this.directories.put(indexName, dir);
        this.indexReaders.put(indexName, reader);
        this.dirSearchers.put(indexName, searcher);
    }

    private Directory getDirectory(final String indexName) throws IOException {
        initIndex(indexName);
        return this.directories.get(indexName);
    }

    private IndexSearcher getDirSearcher(final String indexName) throws IOException {
        initIndex(indexName);
        return this.dirSearchers.get(indexName);
    }

    /**
     * read gzipped jsonlist-files which can be generated with legacy yacy using json-export
     * @param indexName
     * @param jsonlist
     */
    private void load(final String indexName, final File jsonlist) throws IOException {
        InputStream is = new FileInputStream(jsonlist);
        if (jsonlist.getName().endsWith(".gz")) is = new GZIPInputStream(is);
        final BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

        final List<Document> documents = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            try {
                final JSONObject json = new JSONObject(new JSONTokener(line));
                final String title = json.optString("title");
                if (title == null) continue;
                final String url = json.optString("sku");
                if (url == null) continue;
                final String text = json.optString("text_t", "");

                final Document document = new Document();
                document.add(new StringField("sku", url , Store.YES));
                document.add(new TextField("text_t", text , Store.YES));
                document.add(new TextField("title", title , Store.YES));
                documents.add(document);
            } catch (final JSONException e) {
                e.printStackTrace();
            }
        }
        br.close();
        is.close();
        final Directory dir = getDirectory(indexName);
        final IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
        writer.addDocuments(documents);
        writer.close();
    }

    @Override
    public void refresh(final String indexName) {
        // do nothing, no refresh required
    }

    @Override
    public void createIndexIfNotExists(final String indexName, final int shards, final int replicas) {
        // do nothing, new indexes cannot be created, this is read-only
    }

    @Override
    public void setMapping(final String indexName, final String mapping) {
        // do nothing
    }

    @Override
    public void close() {
     // do nothing
    }

    @Override
    public long count(final String indexName, final String user_id, final YaCyQuery yq) {
        /*
        Directory d = getDirectory(indexName);
        try {
            IndexReader indexReader = DirectoryReader.open(d);
            IndexSearcher searcher = new IndexSearcher(indexReader);
            TopDocs topDocs = searcher.search(q.toQuery(this.context), 1);
            return topDocs.totalHits;
        } catch (IOException e) {
            return 0;
        }
        */
        /*
        List<Document> documents = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            documents.add(searcher.doc(scoreDoc.doc));
        }
        */
        return 0;
    }

    @Override
    public boolean exist(final String indexName, final String id) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Set<String> existBulk(final String indexName, final Collection<String> ids) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean deleteByID(final String indexName, final String typeName, final String id) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public long deleteByQuery(final String indexName, final String user_id, final YaCyQuery yq) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Map<String, Object> readMap(final String indexName, final String id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, Map<String, Object>> readMapBulk(final String indexName, final Collection<String> ids) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean writeMap(final String indexName, final String typeName, final String id, final Map<String, Object> jsonMap) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public BulkWriteResult writeMapBulk(final String indexName, final List<BulkEntry> jsonMapList) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Query query(final String indexName, final QueryBuilder queryBuilder, final YaCyQuery postFilter, final Sort sort,
            final WebMapping highlightField, final int timezoneOffset, final int from, final int resultCount, final int aggregationLimit,
            final boolean explain, final WebMapping... aggregationFields) {
        final QueryParser parser = new QueryParser("text_t", new StandardAnalyzer());
        org.apache.lucene.search.Query query = null;
        try {
            query = parser.parse("hello");
        } catch (final ParseException e) {}
        try {
            final IndexSearcher searcher = getDirSearcher(indexName);
            final TopDocs topDocs = searcher.search(query, 10);
            for (final ScoreDoc scoreDoc: topDocs.scoreDocs) {
                final Document document = searcher.doc(scoreDoc.doc);
                final String url = document.get("sku");
                final String text = document.get("text_t");
                System.out.println("url:  " + url + "\ntext: " + text + "\n\n");
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(final String[] args) {
        final File f = new File(args[0]);
        try {
            final DumpIndex di = new DumpIndex();
            di.load("test", f);
        } catch (final IOException e) {
            e.printStackTrace();
        }

    }
}
