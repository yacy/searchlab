
    // modify this according to the location of your elasticsearch instance
    var search_api = "http://searchlab.eu/api/yacysearch.json";


    // t.min.js from https://github.com/jasonmoo/t.js (C) MIT License by Jason Mooberry 
    (function(){function c(a){this.t=a}function l(a,b){for(var e=b.split(".");e.length;){if(!(e[0]in a))return!1;a=a[e.shift()]}return a}function d(a,b){return a.replace(h,function(e,a,i,f,c,h,k,m){var f=l(b,f),j="",g;if(!f)return"!"==i?d(c,b):k?d(m,b):"";if(!i)return d(h,b);if("@"==i){e=b._key;a=b._val;for(g in f)f.hasOwnProperty(g)&&(b._key=g,b._val=f[g],j+=d(c,b));b._key=e;b._val=a;return j}}).replace(k,function(a,c,d){return(a=l(b,d))||0===a?"%"==c?(new Option(a)).innerHTML.replace(/"/g,"&quot;"):
    a:""})}var h=/\{\{(([@!]?)(.+?))\}\}(([\s\S]+?)(\{\{:\1\}\}([\s\S]+?))?)\{\{\/\1\}\}/g,k=/\{\{([=%])(.+?)\}\}/g;c.prototype.render=function(a){return d(this.t,a)};window.t=c})();


    // XHR request to evaluate search request
    function getapi() {
        const query = document.querySelector('#query').value;
        const startRecord = parseInt(document.querySelector('#startRecord').value); // starts at 0
        const xhr = new XMLHttpRequest();
        xhr.open('GET', search_api + '?startRecord=' + startRecord + '&query=' + query);
        xhr.setRequestHeader('Content-type', 'application/json');
        xhr.responseType = 'json';
        xhr.send();
        xhr.onload = function() {
            var channel = xhr.response.channels[0];
            var pages = Math.floor(channel.totalResults / channel.itemsPerPage) + 1;
            channel["results"] = channel.totalResults == 0 ? "" : "<p>" + channel.totalResults + " hits, page " + (Math.floor(startRecord / channel.itemsPerPage) + 1) + " of " + pages + "</p>";
            // result list 
            document.getElementById("result").innerHTML = new t(document.getElementById('resulttemplate').innerHTML).render(channel);
            // page navigation
            document.getElementById("pagination").innerHTML = new t(document.getElementById('paginationtemplate').innerHTML).render({"items": channel.pagenav});
        }
    }
    // event listener on query field to trigger search button when enter is hit
    var input = document.getElementById("query");
    input.addEventListener("keyup", function(event) {if (event.keyCode === 13) {return getapi();}});
