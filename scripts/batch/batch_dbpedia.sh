set -e -u

last() {
  local basedir="$1"
  local n=${2--1}
  list=($(find "${basedir}" \
    -maxdepth 1 -mindepth 1 -type d -not -path '*/\.*' -printf '%f\n' | sort))
  last=${list[$((${#list[@]}+n))]}
  echo ${last}
}

run() {
  echo -- $*
  env $*
}
basedir=${1:-/data/khan/arq/output/batch}

tag=dbp
t_start=$(date +%s)

jobs=(
"JOB_NAME=raw_mod JOB_SET=default make work"
"JOB_NAME=uniq JOB_SET=default make work"
)

for ((i=0; i<${#jobs[@]}; i++)); do
  run ${jobs[$i]} #&& d0=$(last "${basedir}")
done

for x in ${basedir}/*/*/p*.txt; do gzip "${x}"; done

declare -a d
for ((i=0; i<${#jobs[@]}; i++)); do
  d[$i]=$( last "${basedir}" $((-${#jobs[@]}+${i})) )
done

date="$(echo "${d[1]}" | head -c 6)"
rawdir="${basedir}/${d[0]}_raw"
dedupdir="${basedir}/${d[1]}_dedup"
for ((i=0; i<${#jobs[@]}; i++)); do
  d[$i]="${basedir}/${d[$i]}"
done

mv "${d[0]}" "${d[0]}_raw"
mv "${d[1]}" "${d[1]}_dedup"

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

outprefix=${2:-$HOME/Downloads/out}
outfile="${outprefix}/sa_${date}_${tag}-raw.xlsx" \
  "${SCRIPT_DIR}/../../scripts/util/collect_sa.sh" "${rawdir}"/*
outfile=${outprefix}/sa_${date}_${tag}-dedup.xlsx \
  "${SCRIPT_DIR}/../../scripts/util/collect_sa.sh" "${dedupdir}"/*

fd=${date}
mkdir "${basedir}/.${fd}-${tag}"
mv ${basedir}/* "${basedir}/.${fd}-${tag}"
mv "${basedir}/.${fd}-${tag}" "${basedir}/${fd}-${tag}"

t_end=$(date +%s)
secs=$((t_end - t_start))
printf '%dh:%dm:%ds\n' $(($secs/3600)) $(($secs%3600/60)) $(($secs%60))
[ ${#@} -le 0 ] && exit 0

ARQ=~/work/code/arq
for f in *_*; do
  zcat ${f}/*/prop_paths.txt.gz |
    python ${ARQ}/scripts/mini/anon_prop_paths.py |
    sort | uniq -c | sort -nr > ${f}/prop_path_count.tsv
done


pp_acc() {
  printf "raw\tdedup\tform\n" > prop_path_count_${tag}.txt
  join -1 2  <(sort -k 2 *_raw/*count.tsv) \
       -2 2  <(sort -k 2 *_dedup/*count.tsv) \
       -o 1.1,2.1,1.2 | tr ' ' '\t' | sort -nr -k1,2 >> prop_path_count_${tag}.txt
}
pp_acc

for f in */*/prop_path_count.tsv; do
  echo ${f}
done
