
- [x] see `jena_hg.groovy` or `JenaHg.java`
- [x] see `decomp.sh`

```bash
sudo docker run --rm --name groovy1 -v $(pwd):/source -v $(pwd)/grapes:/graperoot webratio/groovy jena_hg.groovy
```


- [graphlet GML](https://reference.wolfram.com/language/ref/format/Graphlet.html)
- [org.jgrapht.ext.GmlImporter](http://jgrapht.org/javadoc/org/jgrapht/ext/GmlImporter.html)

- [x] parse gml: `gml.groovy`

```txt
HE1(Vi11, Vi12, ..., Vi1m1),
HEn(Vin1, Vin2, ..., Vinmn).
```

```javascript
var SparqlParser = require('sparqljs').Parser;
var parser = new SparqlParser();
```
