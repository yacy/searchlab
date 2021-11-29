package net.yacy.grid.io.index;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriterConfig;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;

/**
 * A dump index provides a search result from a dump and holds it in-memory only.
 * The index is not available for write operations, all write operations to this
 * index is going into void.
 */
@SuppressWarnings("deprecation")
public class DumpIndex implements FulltextIndex {

    private String clusterName;
    private Node node;
    private Client elasticsearchClient;

    public DumpIndex(final String clusterName) {
        // create default settings and add cluster name
        Settings.Builder settings = Settings.builder()
                .put("cluster.routing.allocation.enable", "all")
                .put("cluster.routing.allocation.allow_rebalance", "always")
                .put("index.store.type", "mmapfs")
                .put("http.enabled", "true")
                .put("path.home", "elasticsearch-data");

        this.clusterName = clusterName;
        if (this.clusterName != null) settings.put("cluster.name", this.clusterName);
        this.node = new LocalNode(settings.build());
        this.elasticsearchClient = this.node.client();
    }

    private static class LocalNode extends Node {
        public LocalNode(Settings preparedSettings) {
            super(InternalSettingsPreparer.prepareEnvironment(preparedSettings, null));
        }

        @Override
        protected void registerDerivedNodeNameWithLogger(String nodeName) {
        }
    }

    /**
     * read gzipped jsonlist-files which can be generated with legacy yacy using json-export
     * @param indexName
     * @param jsonlist
     */
    public void load(String indexName, File jsonlist) throws IOException {
        InputStream is = new FileInputStream(jsonlist);
        if (jsonlist.getName().endsWith(".gz")) is = new GZIPInputStream(is);
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
        /*
        Directory d = getDirectory(indexName);
        IndexWriter writter = new IndexWriter(d, indexWriterConfig);
        String line;
        while ((line = br.readLine()) != null) {
            try {
                JSONObject json = new JSONObject(new JSONTokener(line));
                String title = json.optString("title");
                if (title == null) continue;
                String url = json.optString("sku");
                if (url == null) continue;
                String text = json.optString("text_t", "");

                Document document = new Document();
                document.add(new TextField("title", title, Store.YES));
                document.add(new TextField("text", text, Store.YES));
                writter.addDocument(document);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        writter.close();
        br.close();
        is.close();
        */
    }

    @Override
    public void refresh(String indexName) {
        // do nothing, no refresh required
    }

    @Override
    public void createIndexIfNotExists(String indexName, int shards, int replicas) {
        // do nothing, new indexes cannot be created, this is read-only
    }

    @Override
    public void setMapping(String indexName, String mapping) {
        // do nothing
    }

    @Override
    public void close() {
     // do nothing
    }

    @Override
    public long count(String indexName, YaCyQuery yq) {
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
    public boolean exist(String indexName, String id) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Set<String> existBulk(String indexName, Collection<String> ids) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean delete(String indexName, String typeName, String id) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int deleteByQuery(String indexName, YaCyQuery yq) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Map<String, Object> readMap(String indexName, String id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, Map<String, Object>> readMapBulk(String indexName, Collection<String> ids) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean writeMap(String indexName, String typeName, String id, Map<String, Object> jsonMap) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public BulkWriteResult writeMapBulk(String indexName, List<BulkEntry> jsonMapList) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Query query(String indexName, YaCyQuery yq, YaCyQuery postFilter, Sort sort,
            WebMapping highlightField, int timezoneOffset, int from, int resultCount, int aggregationLimit, boolean explain,
            WebMapping... aggregationFields) {
        // TODO Auto-generated method stub
        return null;
    }

    public static void main(String[] args) {
        File f = new File(args[0]);
        /*
        DumpIndex di = new DumpIndex();
        try {
            di.load("test", f);

        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("help: " + di.count("test", QueryBuilders.termQuery("text", "help")));
        */
    }

}
