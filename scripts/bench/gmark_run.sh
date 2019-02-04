set -e -u
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
shopt -s expand_aliases
alias g_bench=${SCRIPT_DIR}/g_bench.sh
alias g_collect=${SCRIPT_DIR}/g_collect.sh
alias gmark_res=${SCRIPT_DIR}/gmark_res.sh



if [[ ${1:-} == - ]]; then
	exit 1
fi

do_bench() {
	local i=$1
	local n=$2
	base=exp_${n}
	args="- - - - $i $i 1"
	type1=chain
	type2=cycle
	type1a=1
	type2a=0
	g_bench ${base}/${type1} ${type1a} ${args}
	g_bench ${base}/${type2} ${type2a} ${args}
}

n=$1
for ((i=3; i<=$n;i+=1)) do
	do_bench ${i} ${n}
done

post_process() {
	type=$1
	g_collect ${base}/${type}
	gmark_res ${base}/${type} > ${base}/gmark_${type}.csv
	head -n 1 ${base}/gmark_${type}.csv | tee ${base}/gmark_${type}_{pg,bg}.csv
	sort -n -t"," -k 2 ${base}/gmark_${type}.csv | grep pg >> ${base}/gmark_${type}_pg.csv
	sort -n -t"," -k 2 ${base}/gmark_${type}.csv | grep bg >> ${base}/gmark_${type}_bg.csv
}

post_process ${type1}
post_process ${type2}

echo $(basename $0) done at $(date)
echo send.sh ${base}/gmark_*g.csv
