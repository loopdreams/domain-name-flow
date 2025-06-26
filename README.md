# Domain Name Flow

An experimental web app to try out the recently released [flow](https://clojure.github.io/core.async/clojure.core.async.flow.html) clojure async library.

The app reads the stream of domain names as broadcast by [zonestream](https://openintel.nl/data/zonestream/), an open data project by [OpenIntel](https://openintel.nl/data/zonestream/). 

I came across the zonestream data feed from a great [presentation by Raffaele Sommese](https://www.caida.org/workshops/aims/2502/slides/gmi_aims_5_rsommese.pdf) on DNS abuse. 

This data stream takes the newly announced domain names according to Certificate Transparency Logs. There is also a stream for announced domains that have been subsequently verified by a RDAP lookup. In my case, I am just using the newly (non-confirmed) registered domain names. 

Then, using the 'flow' architecture, various processes chop up this data and present it on the web page. It tracks things like:
- Generic top-level domain (gTLD) frequencies
- Country code top-level domain (ccTLD) frequencies 
- Certificate Transparency log frequencies

These are fairly simple operations, simply intended to explore working with 'flow'. Ideally, you could perform more in-depth live analysis, such as passing the data to a model to try detect malicious domains. The idea with 'flow' is that this can all be done asynchronously in an easy-to-define process 'map'. 

This project also uses [htmx](https://htmx.org/), mainly because it was quick and easy (simple?) to set up. It also uses [echarts](https://echarts.apache.org/en/index.html) for the chart.

The main entry point is the `flow.clj` namespace. 
