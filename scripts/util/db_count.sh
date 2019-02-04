
declare -a sets=(
dbpedia_12
dbpedia_13
dbpedia_14
dbpedia_15
dbpedia_16
lgd_uw13
lgd_uw14
bioportal_uw13
bioportal_uw14
biomed_uw13
swdf_uw13
RKBE_tsv
)


DB_NAME=sparql

nodupe() {
SRC="$1"
psql ${DB_NAME} << EOF | sed -nr "s/$FUNCNAME \| (.*)/\1/p"
SELECT count(id) as $FUNCNAME FROM "Queries" WHERE origin ~~ '${SRC}%';
\x\g\x
EOF
}

unival() {
SRC="$1"
psql ${DB_NAME} << EOF | sed -nr "s/$FUNCNAME \| (.*)/\1/p"
SELECT count(id) as $FUNCNAME FROM "Queries" WHERE origin ~~ '${SRC}%' AND "parseError" = false;
\x\g\x
EOF
}

dupe() {
SRC="$1"
psql ${DB_NAME} << EOF | sed -nr "s/$FUNCNAME \| (.*)/\1/p"
SELECT count(id) as $FUNCNAME FROM "Duplicates" WHERE origin ~~ '${SRC}%';
\x\g\x
EOF
}

for x in "${sets[@]}"; do
	printf "src nodupe dupe unival\n"
	printf "${x}"
	printf " "
	printf $(nodupe "${x}")
	printf " "
	printf $(dupe "${x}")
	printf " "
	printf $(unival "${x}")
	printf "\n"
done
