shopt -s expand_aliases
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
alias detkdecomp=detkdecomp-linux64
alias make_hg="groovyclient $SCRIPT_DIR/JenaHg.groovy"
alias process_gml="groovyclient $SCRIPT_DIR/gml.groovy"
alias delim_split="python $SCRIPT_DIR/delim_split.py"

process() {
	local filename="$1"
	local SP_FILE="${filename%.*}.sparql"
	local HG_FILE="${filename%.*}.dtl"
	local GML_FILE="${filename%.*}.gml"
	local MAP_FILE="${filename%.*}.yaml"
	[[ ! -f $HG_FILE ]] && make_hg $SP_FILE 1> $HG_FILE 2> $MAP_FILE
	[[ ! -f $GML_FILE ]] && detkdecomp 2 $HG_FILE
	if [[ -f $GML_FILE ]]; then
		:
	else
		echo "problem with: $SP_FILE"
	fi
}

process_all() {
	IN_FILES=$*
	for i in $IN_FILES; do
		process "$i"
	done
}

main() {
	CAT_FILE=$1
	SPLIT_DIR=$2
	if [ ! -d $SPLIT_DIR ]; then
		mkdir -p $SPLIT_DIR
		delim_split "$SPLIT_DIR" .sparql '####' < $CAT_FILE
	fi
	process_all "$SPLIT_DIR/*.sparql"
}

INPUT=/data/khan/arq/output/batch/161214152722_shapes/usewod-2015/shapeless.txt
OUTPUT=~/Downloads/tests/sparql
main $INPUT $OUTPUT


