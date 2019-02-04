```bash
alias delim_split="python ~/Downloads/scripts/delim_split.py"
delim_split 0 .sparql '####' < shapeless.txt
alias make_hg="groovy ~/Downloads/scripts/JenaHg.groovy"
make_hg 1.sparql 1> 1.dtl 2> 1.yaml
make_hg 2.sparql 1> 2.dtl 2> 2.yaml
make_hg 3.sparql 1> 3.dtl 2> 3.yaml
make_hg 4.sparql 1> 4.dtl 2> 4.yaml
make_hg 5.sparql 1> 5.dtl 2> 5.yaml
make_hg 6.sparql 1> 6.dtl 2> 6.yaml
make_hg 7.sparql 1> 7.dtl 2> 7.yaml
make_hg 8.sparql 1> 8.dtl 2> 8.yaml
make_hg 9.sparql 1> 9.dtl 2> 9.yaml
make_hg 10.sparql 1> 10.dtl 2> 10.yaml

sed -r 's/([0-9]+),([0-9]+),[0-9]+/\1,\2/g'
ls -1 *.dtl | xargs -i detkdecomp 2 '{}'
ls *.gml | wc -l  # 2 9 10
```
