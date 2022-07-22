package net.yacy.grid.io.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import eu.searchlab.Searchlab;
import eu.searchlab.tools.Classification;
import eu.searchlab.tools.CommonPattern;
import eu.searchlab.tools.OrderedScoreMap;
import eu.searchlab.tools.StringBuilderComparator;

/**
 * Typeahead - suggestion for search queries
 * Can be tested with i.e.
 * http://localhost:8400/en/api/suggest.json?q=who
 */
public class Typeahead {

    private static final int MinimumInputWordLength = 2;

    private final String word, head, tail;
    private final boolean endsWithSpace;

    private static final wordLengthComparator WORD_LENGTH_COMPARATOR = new wordLengthComparator();

    /**
     * @param index a termIndex - most likely retrieved from a switchboard object.
     * @param sort true/false -  sorts the resulting TreeSet by index.count(); <b>Warning:</b> this causes heavy i/o.
     */
    public Typeahead(final String word0) {
        this.endsWithSpace = word0.length() > 0 && word0.charAt(word0.length() - 1) == ' ';
        this.word = word0.trim();
        if (this.endsWithSpace) {
            this.head = this.word;
            this.tail = "";
        } else {
            final int p = word0.lastIndexOf(' ');
            if (p < 0) {
                this.head = this.word;
                this.tail = "";
            } else {
                this.head = word0.substring(0, p).trim();
                this.tail = word0.substring(p + 1).trim();
            }
        }
    }

    /**
     * get suggestions for a given word. The result is first ordered using a term size ordering,
     * and a subset of the result is sorted again with a IO-intensive order based on the index size
     * @param word0
     * @param timeout maximum time (in milliseconds) allowed for processing suggestions. A negative value means no limit.
     * @param preSortSelection the number of words that participate in the IO-intensive sort
     * @return
     */
    public Collection<String> getTypeahead(final long timeout, final int preSortSelection, final String user_id) {
        if (this.word.length() < MinimumInputWordLength) {
            return new ArrayList<>(0); // return nothing if input is too short
        }

        /* Allocate only a part of the total allowed time to the first processing step, so that some time remains to process results in case of timeout */
        final long preSortTimeout = timeout >= 0 ? ((long)(timeout * 0.8)) : timeout;
        final Collection<String> preSorted = getTypeahead(this.head, this.tail, preSortTimeout, preSortSelection, user_id);
        final LinkedHashSet<String> countSorted = new LinkedHashSet<>();
        try {
            for (final String s: preSorted) {
                if (StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(new StringBuilder(s), new StringBuilder(this.word)) ||
                    StringBuilderComparator.CASE_INSENSITIVE_ORDER.endsWith(new StringBuilder(this.word), new StringBuilder(s))) countSorted.add(this.word);
            }
            for (final String s: preSorted) {
                if (!StringBuilderComparator.CASE_INSENSITIVE_ORDER.equals(new StringBuilder(s), new StringBuilder(this.word))) countSorted.add(s);
            }
        } catch (final ConcurrentModificationException e) {
        }

        return countSorted;
    }

    /**
     * return a string that is a suggestion list for the list of given words
     * @param head - the sequence of words before the last space in the sequence, fixed (not to be corrected); possibly empty
     * @param tail - the word after the last space, possibly empty or misspelled
     * @param timeout maximum time allowed for operation in milliseconds. A negative value means no limit.
     * @param preSortSelection - number of suggestions to be computed
     * @return
     */
    private Collection<String> getTypeahead(final String head, final String tail, final long timeout, final int preSortSelection, final String user_id) {
        final long startTime = System.currentTimeMillis();
        final long totalTimeLimit = timeout >= 0 ? startTime + timeout : Long.MAX_VALUE;
        final SortedSet<String> result = new TreeSet<>();
        int count = 30;
        final Classification.ContentDomain contentdom =  Classification.ContentDomain.contentdomParser("all");


        // run query against search index
        final YaCyQuery yq = new YaCyQuery((head + " " + tail).trim(), new String[0], contentdom, 0);
        final ElasticsearchClient.Query query = Searchlab.ec.query(
                System.getProperties().getProperty("grid.elasticsearch.indexName.web", ElasticsearchClient.DEFAULT_INDEXNAME_WEB),
                user_id, yq, null, new Sort(""), WebMapping.text_t, 0, 0, 100, 0, false);

        final OrderedScoreMap<String> snippets = new OrderedScoreMap<>(null);
        final List<Map<String, Object>> qr = query.results;
        for (int hitc = 0; hitc < qr.size(); hitc++) {
            final WebDocument doc = new WebDocument(qr.get(hitc));
            String s = doc.getSnippet(query.highlights.get(hitc), yq);

            if (tail.length() == 0) {

            } else {

            }

            // the suggestion for the tail is in the snippet
            final int headStart = s.indexOf(head);
            if (headStart < 0) continue;
            final int headEnd = headStart + head.length();
            final String snippet = s.substring(headStart, headEnd);
            final String afterSnippet = s.substring(headEnd).trim();
            s = snippet + (afterSnippet.length() > 0 ? " " + afterSnippet : "");
            for (int i = 0; i < s.length(); i++) {final char c = s.charAt(i); if (c < 'A') s = s.replace(c, ' ');} // remove funny symbols
            s = s.replaceAll("<b>", " ").replaceAll("</b>", " ").replaceAll("  ", " ").trim(); // wipe superfluous whitespace
            final String[] sx = CommonPattern.SPACES.split(s);
            final StringBuilder sb = new StringBuilder(s.length());
            for (final String x: sx) if (x.length() > 1 && sb.length() < 28) sb.append(x).append(' '); else break;
            s = sb.toString().trim();
            if (s.length() > 0)  snippets.inc(s, count--);
        }

        // delete all snippets which occur double-times, i.e. one that is a substring of another: remove longer snippet
        Iterator<String> si = snippets.keys(false);
        while (si.hasNext()) {
            final String testsnippet = si.next().toLowerCase();
            if (testsnippet.length() > head.length() + tail.length() + 1) {
                final Iterator<String> sin = snippets.keys(false);
                while (sin.hasNext()) {
                    final String snippetx = sin.next();
                    if (snippetx.length() != testsnippet.length() && snippetx.toLowerCase().startsWith(testsnippet)) {
                        snippets.delete(snippetx);
                    }
                }
            }
        }
        si = snippets.keys(false);
        while (si.hasNext() && result.size() < preSortSelection) {
            result.add(si.next());
        }

        return result;
    }


    /**
     * wordLengthComparator is used by DidYouMean to order terms by the term length
     * This is the default order if the indexSizeComparator is not used
     */
    private static class wordLengthComparator implements Comparator<String> {
        @Override
        public int compare(final String o1, final String o2) {
            final int i1 = o1.length();
            final int i2 = o2.length();
            if (i1 == i2) {
                return StringBuilderComparator.CASE_INSENSITIVE_ORDER.compare(new StringBuilder(o1), new StringBuilder(o2));
            }
            return (i1 < i2) ? 1 : -1; // '<' is correct, because the longest word shall be first
        }
    }

    /**
     * headMatchingComparator is used to sort results in such a way that words that match with the given words are sorted first
     */
    private static class headMatchingComparator implements Comparator<String> {
        private final String head;
        private final Comparator<String> secondaryComparator;
        public headMatchingComparator(final String head, final Comparator<String> secondaryComparator) {
            this.head = head;
            this.secondaryComparator = secondaryComparator;
        }

        @Override
        public int compare(final String o1, final String o2) {
            final boolean o1m = StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(new StringBuilder(o1), new StringBuilder(this.head));
            final boolean o2m = StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(new StringBuilder(o2), new StringBuilder(this.head));
            if ((o1m && o2m) || (!o1m && !o2m)) {
                return this.secondaryComparator.compare(o1, o2);
            }
            return o1m ? -1 : 1;
        }
    }
}
