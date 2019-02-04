set -e -u
if [ ! ${GMARK_DIR} ]; then GMARK_DIR=~/work/ext/gmark; fi

gplot() {
	local OUTPUT=$1
	local INPUT1=$2
	local INPUT2=$3
	cat <<-EOF
		set terminal svg size 400,300 enhanced fname 'arial' fsize 10 butt solid
		set output '${OUTPUT}'
		set xrange [ -1 :  ]
		set key left
		set xtics 4
		plot "${INPUT1}" using ((column(0)+1)*4):1 title "PostgreSQL", \
		     "${INPUT2}" using ((column(0)+1)*4):1 title "BlazeGraph"
	EOF
}

plot() {
	local OUTPUT=$1
	local INPUT1=$2
	local INPUT2=$3
	gplot ${OUTPUT} ${INPUT1} ${INPUT2} | gnuplot
}

single() {
	local FILE=$1
	local acc=0
	local count=0
	while IFS='\n' read -r line; do
		acc=$(bc <<< ${acc}+${line})
		count=$((count + 1))
	done < ${FILE}
	echo $(bc <<< ${acc}/${count})
}

collect() {
	local DIR=$1
	for f in ${DIR}/b*/; do
		single ${f}/bench_pg2.csv
	done | tee ${DIR}/bench_pg.csv
	for f in ${DIR}/b*/; do
		single ${f}/bench_bg2.csv
	done | tee ${DIR}/bench_bg.csv
}

process() {
	local DIR=$1
	collect ${DIR}
}

[[ ! "$*" ]] && process ${GMARK_DIR}/exp
[[ "$*" ]] && process $1
echo $(basename $0) done at $(date)
