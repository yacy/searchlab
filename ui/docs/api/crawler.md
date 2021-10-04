# YaCy Crawler API
A web crawl is stared using either the web page at /CrawlStartSite_p.html or /CrawlStartExpert_p.html, however, both web pages call the servlet at /Crawler_p.html. The /Crawler_p.html can be called directly to show monitoring information but it is also the API access point to start a crawl using a direct call to the url as shown below:

```
http://localhost:8090/Crawler_p.html?crawlingDomMaxPages=10000&range=wide&intention=&sitemapURL=&crawlingQ=on&crawlingMode=url&crawlingURL=http://vip.asus.com/forum/default.aspx%3FSLanguage%3Den-us&crawlingFile=&mustnotmatch=&crawlingFile%24file=&crawlingstart=Neuen%20Crawl%20starten&mustmatch=.*&createBookmark=on&bookmarkFolder=/crawlStart&xsstopw=on&indexMedia=on&crawlingIfOlderUnit=hour&cachePolicy=iffresh&indexText=on&crawlingIfOlderCheck=on&bookmarkTitle=&crawlingDomFilterDepth=1&crawlingDomFilterCheck=on&crawlingIfOlderNumber=1&crawlingDepth=4
```

The parameters used here are explained below in detail. Each YaCy crawl job has its own profile to store information to ensure proper handling of crawled URLs. It is created at crawl start, will be set as terminated if a crawl is considered to be finished, and may also be edited or deleted while the crawl is running.

To start a new crawl and create its profile following parameters are needed


* crawlingstart =<br>
(no value needed) - this key must be present to trigger a crawl start

* crawlingMode =<br>
Possible values: 'url', 'sitemap', 'sitelist', 'file'. The crawler can be started with different modes:
'url': start from a given url which is the root of a crawl tree. The url is given in 'crawlingURL'. If this url is a http-link, then the Crawl will subsequently load all linked documents from http until a given depth is reached. If this url is a smb- or ftp-link, then the given resource will be listed completely using a special listing process.
'sitemap': use a sitemap to retrieve all files that are listed in the sitemap. The sitemap-URL is given in 'sitemapURL'
'sitelist': use a list of crawl start URLs. This is like starting with one url, but using several of them. The list of urls is retrieved by loading the url given in 'crawlingURL'. Each of the urls in that file is then used to start it's individual crawl. This makes sense if the 'range' attribute contains the value 'domain' or 'subpath' which creates an individual must-match pattern for each of the urls in the sitelist.
'file': use a file in the local file system to provide a start document. The crawl will then start like with a root url but the file itself will not be placed to the index, only the documents which are linked in that document.
* crawlingURL =<br>
The crawl start url. This value must be present in all cases of the crawlingMode options. The crawler's double-check does not check this start url which means that in the index existing start-URLs are always loaded (but can be loaded from the cache, see the cachePolicy option for that.
* sitemapURL =<br>
Only to be defined if 'crawlingMode' = 'sitemap'. This is an url which is typically linked within a robots.txt file. The sitemapURL must point to a resource which is formed as described in http://www.sitemaps.org
* crawlingFile =<br>
Only to be defined if 'crawlingMode' = 'file'. This is a path to a file in the local file system. The content of the file is parsed and all urls inside that document are roots for crawl starts as defined in this crawl request.
* crawlingDepth =<br>
This defines how often the Crawler will follow links embedded in websites.
A minimum of 0 is recommended and means that the page set as crawling URL, sitemap orfile will be added to the index, but no linked content is indexed. 2-4 is good for normal indexing. Be careful with the depth, consider a branching factor of average 20; A crawleing depth of 8 would index 25.600.000.000 pages, maybe this is the whole WWW.

* crawlingDepthExtension =<br>
This is a regular expression that can be used to extend the crawling depth to infinity. That means, if this pattern matches with the URL, the crawling depth is not a limitation any more on the ongoing crawl. Default is an empty String, which is a never-match regular expression.
* range =<br>
Possible values are 'domain', 'subpath' or not set, which means 'wide'. Default is 'wide'. If this value is set to 'domain' or 'subpath', the 'mustmatch' parameter is set automatically and overrides given values in that field. 'domain' creates a 'mustmatch' value which restricts the crawl to pages on the same domain of the start-url; 'subpath' creates a 'mustmatch' value which restricts the crawl to pages within the same subpath of the start-url.
* mustmatch =<br>
The filter is a regular expression that must match with the URLs which are used to be crawled; default is 'catch all'.
Example: to allow only urls that contain the word 'science', the filter is set to '.*science.*'. An automatic domain-restriction can be used to fully crawl a single domain.
* mustnotmatch =<br>
This filter must not match with the URL to allow that the page is accepted for crawling. The empty string is a never-match filter which should do well for most cases.
* ipMustmatch =<br>
The filter is a regular expression that must match with the IP of the host to be crawled; default is 'catch all'.
* ipMustnotmatch =<br>
This filter must not match with the IP of the host to allow that the page is accepted for crawling. The empty string is a never-match filter which should do well for most cases.
* indexmustmatch =<br>
This filter can be used to filter documents to be indexed. Even if a document was loaded by the crawler because the mustmatch filter allows that, this filter can be used to restrict documents to be indexed. Default is catch-all.
* indexmustnotmatch =<br>
This filter must not match to allow that the page is accepted for indexong. Default is an empty string, which is a never-match to exclude no documents from indexing.
* deleteold =<br>
if 'range' is either 'domain' or 'subpath' or the mustmatch-value is not catchall, this option can be used to delete all urls for given start-hosts. Possible values for 'deleteold' are 'off' (do not delete), 'on' (delete all documents for the start host(s), or 'age'. If 'age' is used, the values 'deleteIfOlderNumber' and 'deleteIfOlderUnit' must be set and this defines the limit for the deletion: if the load date of a stored document is older than the given time, then it is deleted.
* deleteIfOlderNumber =<br>
If 'deleteold' is 'age', then this must be set with a numeric value. The unit is given in field 'deleteIfOlderUnit'.
* deleteIfOlderUnit =<br>
Possible values are 'year', 'month', 'day', 'hour'
* recrawl =<br>
value is either 'nodoubles' or 'reload'. This value is a switch which activates the usage of 'reloadIfOlderNumber', and 'reloadIfOlderUnit' values. If 'nodoubles' is selected then every url that the crawler discovers and is already stored in the index is rejected and not loaded again. If 'reload' is selected then 'reloadIfOlderNumber' and 'reloadIfOlderUnit' is a limit for the double-check: if the load date of a new page url is older than the given time, then it is considered as stale and reloaded for indexing. If it is newer then it is considered as fresh and therefore as 'double' in the double-chech.
* reloadIfOlderNumber =<br>
If 'recrawl' is 'reload', then this must be set with a numeric value. The unit is given in field 'crawlingIfOlderUnit'.
* reloadIfOlderUnit =<br>
Possible values are 'year', 'month', 'day', 'hour'
* crawlingDomMaxCheck =<br>
The maxmimum number of pages that are fetched and indexed from a single domain can be limited with this option. If combined with the 'Auto-Dom-Filter' the limit is applied to all the domains within the given depth. Domains outside the given depth are then sorted-out anyway.
* crawlingDomMaxPages =<br>
Integer values are allowed. If this value is given, then the maximum number of pages per domain is restricted to this given number.
* crawlingQ =<br>
value is either 'on' or 'off'; default is 'off'. A questionmark is usually a hint for a dynamic page. URLs pointing to dynamic content should usually not be crawled. However, there are sometimes web pages with static content that is accessed with URLs containing question marks.
* directDocByURL =<br>
value is either 'on' or 'off'; default is 'off'. If this is 'on', then all documents which are linked from loaded pages are indexed with their URL only and without any metadata. This applies also if these documents are outside of the crawl depth or do not match with regular expressions.
* storeHTCache =<br>
value is either 'on' or 'off'; default is 'off'. If 'on', all downloaded content is stored in a built-in cache, the HTCache.
* cachePolicy =<br>
The caching policy states when to read from the HTCache during crawling:
no cache: never use the cache, all content from fresh internet source;
if fresh: use the cache if the cache exists and is fresh using the proxy-fresh rules;
if exist: use the cache if the cache exist. Do no check freshness. Othervise use online source;
cache only: never go online, use all content from cache. If no cache exist, treat content as unavailable
* indexText =<br>
value is either 'on' or 'off'; default is 'off'. If this is 'on' then the fulltext is indexed. If 'off', only the metadata of the pages or media according to 'indexMedia' is recorded and indexed.
* indexMedia =<br>
value is either 'on' or 'off'; default is 'off'. If this is 'on' then the metadta (if available) of media content. If 'off', only the metadata of the pages or fulltext according to 'indexText' is recorded and indexed.
* crawlOrder =<br>
value is either 'on' or 'off'; default is 'off'. If 'on' the crawler will contact other peers and use them as remote indexers for your crawl. If crwling results are needed locally, this switch should be set to false. Only senior and principal peers can initiate or receive remote crawls. A YaCyNews message will be created to inform all peers about a global crawl, so they can omit starting a crawl with the same start point.
* intention =<br>
This is a text message that is posted along with the crawlOrder option to inform other YaCy peers about a distributed crawl. If 'crawlOrder' is 'off', this value is not used.
* xsstopw =<br>
This can be useful to circumvent that extremely common words are added to the database, i.e. "the", "he", "she", "it"... To exclude all words given in the file yacy.stopwords from indexing, this hast to be set true.
* collection =<br>
The name of a collection or a comma-separated list of collections. This collections can be used to separate search results into different subsets which is used with the GSA search interface using the 'site' parameter in the search request.
* agentName =<br>
The name of a user agent that is used for crawling. Possible values are: 'YaCy Internet (cautious)', 'YaCy Intranet (greedy)', 'Googlebot', 'Random Browser' or 'Custom Agent'. If 'Custom Agent' is used, then the user agent credentials can be set in yacy.conf with the attributes 'crawler.userAgent.name' (i.e. 'yacybot'), 'crawler.userAgent.string' (i.e. 'yacybot ($$SYSTEM$$) http://yacy.net/bot.html'), 'crawler.userAgent.minimumdelta' (i.e. '500') and 'crawler.userAgent.clienttimeout' (i.e. '10000'). The 'crawler.userAgent.name' is the token which is selected in robots.txt files for robots rules. To use these values, there must not appear the word 'yacy' anywhere in these attributes (even if they are given here in the example)

