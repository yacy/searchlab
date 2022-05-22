# Crawl Start


Start a Web crawl:

<form action=".">

<div class="form-group">
    <label for="crawlingURL">Start-URL</label>
    <input class="form-control" name="crawlingURL" id="crawlingURL" type="text" size="50" maxlength="256" value="" placeholder="https://..."/>
    <p class="help-block">The start-URL is a link on crawl depth 0</p>
</div>

<div class="form-group">
    <label for="crawlingDepth">Crawl Depth</label>
    <input class="form-control" name="crawlingDepth" id="crawlingDepth" type="text" size="2" maxlength="2" value="2"/>
    <p class="help-block">Depth 1 means: all linked documents from the start url are included.</p>
</div>

<div class="form-group">
    <label for="collection">Collection</label>
    <input class="form-control" name="collection" id="collection" type="text" size="50" maxlength="256" value="user"/>
    <p class="help-block">The start-URL is a link on crawl depth 0</p>
</div>

<div class="checkbox">
  <label>
    <input type="checkbox">Priority
  </label>
</div>

<div class="checkbox">
  <label>
    <input type="checkbox">Headless Browser Loading
  </label>
</div>

<button type="submit" name="crawlingstart" value="Start New Crawl" class="btn btn-primary"/>Start</button>
</form>

[//]: #           defaultValues.put("priority", 0);
[//]: #           defaultValues.put("loaderHeadless", "false");
[//]: #           defaultValues.put("storeAssets", "false");
[//]: #           defaultValues.put("archiveWARC", "true");
[//]: #           defaultValues.put("archiveIndex", "true");
[//]: #           defaultValues.put("archiveGraph", "true");


[//]: #{
[//]: #  "actions": [
[//]: #    {
[//]: #      "type": "crawler",
[//]: #      "queue": "crawler_webcrawler_00",
[//]: #      "id": "klg.de-2022-05-22-06-34-18-0",
[//]: #      "userId": "en",
[//]: #      "depth": 0,
[//]: #      "sourcegraph": "rootasset",
[//]: #      "assets": {
[//]: #        "rootasset": [
[//]: #          {
[//]: #            "canonical_s": "http:\/\/klg.de\/"
[//]: #          }
[//]: #        ]
[//]: #      }
[//]: #    }
[//]: #  ],
[//]: #  "data": [
[//]: #    {
[//]: #      "crawlingMode": "url",
[//]: #      "crawlingURL": "klg.de",
[//]: #      "sitemapURL": "",
[//]: #      "crawlingFile": "",
[//]: #      "crawlingDepth": 3,
[//]: #      "crawlingDepthExtension": "",
[//]: #      "range": "domain",
[//]: #      "mustmatch": ".*",
[//]: #      "mustnotmatch": ".*\\.(js|css|jpg|jpeg|png|dmg|mpg|mpeg|zip|gz|exe|pkg)",
[//]: #      "ipMustmatch": ".*",
[//]: #      "ipMustnotmatch": "",
[//]: #      "indexmustmatch": ".*",
[//]: #      "indexmustnotmatch": "",
[//]: #      "deleteold": "off",
[//]: #      "deleteIfOlderNumber": 0,
[//]: #      "deleteIfOlderUnit": "day",
[//]: #      "recrawl": "nodoubles",
[//]: #      "reloadIfOlderNumber": 0,
[//]: #      "reloadIfOlderUnit": "day",
[//]: #      "crawlingDomMaxCheck": "off",
[//]: #      "crawlingDomMaxPages": 1000,
[//]: #      "crawlingQ": "off",
[//]: #      "cachePolicy": "if fresh",
[//]: #      "collection": "user",
[//]: #      "agentName": "",
[//]: #      "priority": 0,
[//]: #      "loaderHeadless": "false",
[//]: #      "userId": "en",
[//]: #      "storeAssets": "false",
[//]: #      "archiveWARC": "true",
[//]: #      "archiveIndex": "true",
[//]: #      "archiveGraph": "true"
[//]: #    }
[//]: #  ],
[//]: #  "metadata": {
[//]: #    "count": 1
[//]: #  },
[//]: #  "success": true
[//]: #}
