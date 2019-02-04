set -e -u
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
if [ ! ${GMARK_DIR} ]; then GMARK_DIR=~/work/ext/gmark; fi
shopt -s expand_aliases
alias pg_bench='groovy $SCRIPT_DIR/PostgresBench.groovy'
alias bg_bench='groovy $SCRIPT_DIR/BlazeGraph.groovy'

prepare() {
	local BASEDIR=$1
	local MIN=$2
	local MAX=$3
	local PRESET=$4
	cp ${GMARK_DIR}/use-cases/test.xml ${BASEDIR}/use-case.xml
	local TYPE='s#<type chain.*#<type chain="0" star="0" cycle="1" starchain="0"/>#g'
	if [ ${PRESET} = 0 ]; then
		:
	elif [ ${PRESET} = 1 ]; then
		TYPE='s#<type chain.*#<type chain="1" star="0" cycle="0" starchain="0"/>#g'
	else
		TYPE='s#<type chain.*#<type chain="0" star="1" cycle="0" starchain="0"/>#g'
	fi
	local GRAPH_SIZE=100000
	local WORKLOAD=2
	sed -e 's#<disjuncts min.*#<disjuncts min="1" max="1"/>#g' \
	    -e 's#<length min.*#<length min="1" max="1"/>#g' \
	    -e 's#<multiplicity star.*#<multiplicity star="0.0"/>#g' \
	    -e "${TYPE}" \
	    -e 's#<conjuncts min.*#<conjuncts min="'${MIN}'" max="'${MAX}'"/>#g' \
	    -e 's#<workload id="0".*#<workload id="0" size="'${WORKLOAD}'">#g' \
	    -e 's#<nodes>.*#<nodes>'${GRAPH_SIZE}'</nodes>#g' \
	    -e 's#<selectivity.*#<selectivity constant="0" linear="1" quadratic="0"/>#g' \
	    -i ${BASEDIR}/use-case.xml >/dev/null
}

gmark() {
	local BASEDIR=$1
	echo "running gmark"
	[ -d ${BASEDIR}/translated ] || mkdir -p ${BASEDIR}/translated
	${GMARK_DIR}/src/test -c ${BASEDIR}/use-case.xml -g ${BASEDIR}/graph.txt -w ${BASEDIR}/workload.xml -r ${BASEDIR}
	${GMARK_DIR}/src/querytranslate/test -w ${BASEDIR}/workload.xml -o ${BASEDIR}/translated
	>/dev/null sed 's/"true"/true/g' -i ${BASEDIR}/translated/*.sql
	>/dev/null sed -r 's/( UNION )+;/;/g' -i ${BASEDIR}/translated/*.sql
	>/dev/null sed -r 's/( UNION  \{  \} )+//g' -i ${BASEDIR}/translated/*.sparql
	>/dev/null sed 's/SELECT DISTINCT/SELECT/g' -i ${BASEDIR}/translated/*.{sql,sparql}
	>/dev/null sed -r 's/SELECT.*WHERE/ASK/g' -i ${BASEDIR}/translated/*.sparql
	>/dev/null sed -r 's/(^.*);/SELECT CAST(CASE WHEN EXISTS(\1) THEN 1 ELSE 0 END AS INT);/g' -i ${BASEDIR}/translated/*.sql
}

gen_input() {
	local BASEDIR=$1
	local EXT=$2
	echo ${BASEDIR}/translated/query-*.${EXT}
}

bench_pg() {
	local BASEDIR=$1
	export DB_USER=$(hostname)
	export DB_PASS=$(hostname)
	export DB_URL='jdbc:postgresql://localhost:5432/db1'
	printf "running pg_bench: %s\n" ${BASEDIR}
	pg_bench +r +e ${BASEDIR}/graph.txt -q $(gen_input ${BASEDIR} sql) |
	  tee ${BASEDIR}/bench_pg.txt
}

bench_bg() {
	local BASEDIR=$1
	printf "running bg_bench: %s\n" ${BASEDIR}
	bg_bench +r +e ${BASEDIR}/graph.txt -q $(gen_input ${BASEDIR} sparql) |
	  grep -E '^query|^insert' | tee ${BASEDIR}/bench_bg.txt
}

plot() {
	local OUTPUT=$1
	local INPUT=$2
	gnuplot <<-EOF
		set terminal svg size 400,300 enhanced fname 'arial' fsize 10 butt solid
		set output '${OUTPUT}'
		set boxwidth 0.5
		set style fill solid
		set xrange [ -1 :  ]
		set nokey
		plot "${INPUT}" using 1 with boxes
	EOF
}

plot_all() {
	local BASEDIR=$1
	cat ${BASEDIR}/bench_pg.txt | grep query |
	  grep -v TIMEOUT |
	  awk -F "," '{print $3}' | tee ${BASEDIR}/bench_pg2.csv > /dev/null
	cat ${BASEDIR}/bench_bg.txt | grep query |
	  grep -v TIMEOUT |
	  awk -F "," '{print $3}' | tee ${BASEDIR}/bench_bg2.csv > /dev/null
}

pack() {
	local BASEDIR=$1
	cd ${BASEDIR}
	tar -czf sql.tar.gz translated/*.sql
	tar -czf sparql.tar.gz translated/*.sparql
	tar -czf bench.tar.gz *.csv bench_*.txt
	cd - &>/dev/null
	gzip -c ${BASEDIR}/workload.xml > ${BASEDIR}/workload.gz
	gzip -c ${BASEDIR}/graph.txt > ${BASEDIR}/graph.gz
}

single() {
	local BASEDIR=$1
	local MIN=$2
	local MAX=$3
	local PRESET=$4
	[ -d ${BASEDIR} ] || mkdir -p ${BASEDIR}
	prepare ${BASEDIR} ${MIN} ${MAX} ${PRESET}
	gmark ${BASEDIR}
	bench_pg ${BASEDIR}
	bench_bg ${BASEDIR}
	plot_all ${BASEDIR}
	pack ${BASEDIR}
}

_prepare_gmark() {
	local old_pwd=$(pwd)
	cd ${GMARK_DIR} && cd git clone https://github.com/graphMark/gmark .
	git checkout ef74c53
	cd demo/scripts && ./compile-all.sh
	cd ${old_pwd}
}

postprocess() {
	local PREFIX=$1
	local STEPS=$2
	cd ${PREFIX}
	cd - &>/dev/null
}

generate() {
	local PREFIX=$1
	local PRESET=$2
	local LOW=$7
	local HIGH=$8
	local STEP=$9
	local start=$(date +%s)
	local count=1
	for ((i=$LOW; i<=$HIGH;i+=$STEP)) do
		single ${PREFIX}/b${i} $(( ($i) )) $(( ($i) )) ${PRESET}
		count=$((count+1))
	done
	local STEPS=$((count-1))
	postprocess ${PREFIX} ${STEPS}
	local end=$(date +%s)
	local delta=$((end-start))
	python -c "import datetime; print(str(datetime.timedelta(seconds=${delta})))"
}

[[ "$1" ]] && generate $@
echo $(basename $0) done at $(date)
