
```bash
export GMARK_DIR=~/work/ext/gmark
alias g_bench=~/Downloads/scripts/g_bench.sh
alias g_collect=~/Downloads/scripts/g_collect.sh
alias gmark_res=~/Downloads/scripts/gmark_res.sh
export base=exp_s

mkdir -p ${base}/circle ${base}/chain
g_bench ${base}/circle 0 - - - - 3 8 1
g_bench ${base}/chain  1 - - - - 3 8 1

g_collect ${base}/circle
g_collect ${base}/chain

gmark_res ${base}/circle > ${base}/gmark_circle.csv
head -n 1 ${base}/gmark_circle.csv | tee ${base}/gmark_circle_{pg,bg}.csv
sort -n -t"," -k 2  ${base}/gmark_circle.csv | grep pg >> ${base}/gmark_circle_pg.csv
sort -n -t"," -k 2  ${base}/gmark_circle.csv | grep bg >> ${base}/gmark_circle_bg.csv
gmark_res ${base}/chain > ${base}/gmark_chain.csv
head -n 1 ${base}/gmark_chain.csv | tee ${base}/gmark_chain_{pg,bg}.csv
sort -n -t"," -k 2  ${base}/gmark_chain.csv | grep pg >> ${base}/gmark_chain_pg.csv
sort -n -t"," -k 2  ${base}/gmark_chain.csv | grep bg >> ${base}/gmark_chain_bg.csv
```



```bash
cp exp/exp1 exp/exp2
base=exp/exp2
sed 's/SELECT DISTINCT/SELECT/g' -i exp/exp2/*/*/translated/*
g_bench exp/exp2/circle 0 && g_bench exp/exp2/chain 1

g_collect ${base}/circle && g_collect ${base}/chain
```


```bash
sed -r 's/SELECT.*WHERE/ASK/g' -i *.sparql
sed -r 's/(^.*);/SELECT EXISTS (\1);/g' -i *.sql
ls ./exp*/*/*/translated/*.sparql
ls ./exp*/*/*/translated/*.sql

export base=exp_s
g_bench ${base}/circle 0 - - - - 3 8 1
g_bench ${base}/chain  1 - - - - 3 8 1
send.sh ${base}/gmark_*g.csv
```

```bash
sed -r 's/SELECT.*WHERE/ASK/g' -i ./exp*/*/*/translated/*.sparql
sed -r 's/(^.*);/SELECT EXISTS (\1);/g' -i ./exp*/*/*/translated/*.sql
scp -P 2222 ${base}/gmark_*g.csv admin@zt1.ydns.eu:/d/download/
```


```bash
shopt -s expand_aliases
export GMARK_DIR=~/work/ext/gmark
alias g_bench=~/Downloads/scripts/g_bench.sh
alias g_collect=~/Downloads/scripts/g_collect.sh
alias gmark_res=~/Downloads/scripts/gmark_res.sh
if [ $1 != 'l' ]; then
	base=exp_s
	g_bench ${base}/circle 0 - - - - 3 8 1
	g_bench ${base}/chain  1 - - - - 3 8 1
else
	base=exp_l
	g_bench ${base}/circle 0 - - - - 12 64 4
	g_bench ${base}/chain  1 - - - - 12 64 4
fi
g_collect ${base}/circle
g_collect ${base}/chain
gmark_res ${base}/circle > ${base}/gmark_circle.csv
head -n 1 ${base}/gmark_circle.csv | tee ${base}/gmark_circle_{pg,bg}.csv
sort -n -t"," -k 2  ${base}/gmark_circle.csv | grep pg >> ${base}/gmark_circle_pg.csv
sort -n -t"," -k 2  ${base}/gmark_circle.csv | grep bg >> ${base}/gmark_circle_bg.csv
gmark_res ${base}/chain > ${base}/gmark_chain.csv
head -n 1 ${base}/gmark_chain.csv | tee ${base}/gmark_chain_{pg,bg}.csv
sort -n -t"," -k 2  ${base}/gmark_chain.csv | grep pg >> ${base}/gmark_chain_pg.csv
sort -n -t"," -k 2  ${base}/gmark_chain.csv | grep bg >> ${base}/gmark_chain_bg.csv
echo done at $(date)
send.sh ${base}/gmark_*g.csv
```


```bash
tar -czf bench.tar.gz */*/*/bench*.txt

for f in */*/*/bench*.txt; do
  printf "%s,%s\n" \
    $(echo $f | sed -r 's#.*/(.*)/b(.*)/(bench_)(.g).txt#\1,\2,\4#g') \
    $(grep TIMEOUT $f | wc -l)
done

pg_bench +r -e graph.txt
```


```bash
{ printf "type,size,system,avg,avg_wt,timeout_count,match,non-match\n"
for f in */*/*/bench*.txt; do
  id=$(echo $f | sed -r 's#.*/(.*)/b(.*)/(bench_)(.g).txt#\1,\2,\4#g')
  avg=$(awk -F, '{a+=$3} END {print a/NR}' $f)
  avg_wt=$(sed -r 's#TIMEOUT#3E11#g' $f | awk -F, '{a+=$3} END {print a/NR}')
  timeouts=$(grep TIMEOUT $f | wc -l)
  match=$(grep ',1$' $f | wc -l)
  non_match=$(grep ',0$' $f | wc -l)
  printf "%s,%s,%s,%s,%s,%s\n" $id $avg $avg_wt $timeouts $match $non_match
done } > out.csv
```
