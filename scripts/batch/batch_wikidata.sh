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

tag=wd
t_start=$(date +%s)

jobs=(
"JOB_NAME=wd17_raw_organic_grep JOB_SET=default make work" # 01:20
"JOB_NAME=wd17_raw_robotic JOB_SET=default make work" # 09:11 h
"JOB_NAME=wd17_dedup_organic_grep JOB_SET=default make work" # 01:05 min
"JOB_NAME=wd17_dedup_robotic JOB_SET=default make work" # 01:23 h
"JOB_NAME=wd17_raw_organic_500 JOB_SET=default make work"
"JOB_NAME=wd17_raw_robotic_500 JOB_SET=default make work"
"JOB_NAME=wd17_dedup_organic_500 JOB_SET=default make work"
"JOB_NAME=wd17_dedup_robotic_500 JOB_SET=default make work"
)

for ((i=0; i<${#jobs[@]}; i++)); do
  run ${jobs[$i]} #&& d0=$(last "${basedir}")
done

for x in ${basedir}/*/*/p*.txt; do gzip "${x}"; done

declare -a d
for ((i=0; i<${#jobs[@]}; i++)); do
  d[$i]=$( last "${basedir}" $((-${#jobs[@]}+${i})) )
done

date="$(echo "${d[3]}" | head -c 6)"
rawdir="${basedir}/${date}_raw"
dedupdir="${basedir}/${date}_dedup"

mkdir "${rawdir}"
mkdir "${dedupdir}"

for ((i=0; i<${#jobs[@]}; i++)); do
  d[$i]="${basedir}/${d[$i]}"
done

mv "${d[0]}"/* "${d[0]}/wd_raw_user"
mv "${d[1]}"/* "${d[1]}/wd_raw_bot"
mv "${d[2]}"/* "${d[2]}/wd_dedup_user"
mv "${d[3]}"/* "${d[3]}/wd_dedup_bot"
if [ ${#jobs[@]} -gt 4 ]; then
mv "${d[4]}"/* "${d[4]}/wd_raw_user_500"
mv "${d[5]}"/* "${d[5]}/wd_raw_bot_500"
mv "${d[6]}"/* "${d[6]}/wd_dedup_user_500"
mv "${d[7]}"/* "${d[7]}/wd_dedup_bot_500"
fi

cp -al -t "${rawdir}" "${d[0]}"/${tag}* "${d[1]}"/${tag}*
cp -al -t "${dedupdir}" "${d[2]}"/${tag}* "${d[3]}"/${tag}*
if [ ${#jobs[@]} -gt 4 ]; then
cp -al -t "${rawdir}" "${d[4]}"/${tag}* "${d[5]}"/${tag}*
cp -al -t "${dedupdir}" "${d[6]}"/${tag}* "${d[7]}"/${tag}*
fi

mv "${d[0]}" "${d[0]}_ro"
mv "${d[1]}" "${d[1]}_rr"
mv "${d[2]}" "${d[2]}_do"
mv "${d[3]}" "${d[3]}_dr"
if [ ${#jobs[@]} -gt 4 ]; then
mv "${d[4]}" "${d[4]}_rot"
mv "${d[5]}" "${d[5]}_rrt"
mv "${d[6]}" "${d[6]}_dot"
mv "${d[7]}" "${d[7]}_drt"
fi

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

outprefix=${2:-$HOME/Downloads/out}
outfile="${outprefix}/sa_${date}_${tag}-raw.xlsx" \
  "${SCRIPT_DIR}/../../scripts/util/collect_sa.sh" "${rawdir}"/*
outfile=${outprefix}/sa_${date}_${tag}-dedup.xlsx \
  "${SCRIPT_DIR}/../../scripts/util/collect_sa.sh" "${dedupdir}"/*

fd=$(basename ${d[7]})
mkdir "${basedir}/.${fd}-${tag}"
mv ${basedir}/* "${basedir}/.${fd}-${tag}"
mv "${basedir}/.${fd}-${tag}" "${basedir}/${fd}-${tag}"

t_end=$(date +%s)
secs=$((t_end - t_start))
printf '%dh:%dm:%ds\n' $(($secs/3600)) $(($secs%3600/60)) $(($secs%60))
[ ${#@} -le 0 ] && exit 0

ARQ=~/work/code/arq
for f in ??????_*/*; do
  zcat ${f}/prop_paths.txt.gz |
    python ${ARQ}/scripts/mini/anon_prop_paths.py |
    sort | uniq -c | sort -nr > ${f}/prop_path_count.tsv
done

types=(bot user bot_500 user_500)

pp_acc() {
  local type=$1
  printf "raw\tdedup\tform\n" > prop_path_count_${tag}-${type}.txt
  join -1 2  <(sort -k 2 *_raw/*${type}/*count.tsv) \
       -2 2  <(sort -k 2 *_dedup/*${type}/*count.tsv) \
       -o 1.1,2.1,1.2 | tr ' ' '\t' | sort -nr -k1,2 >> prop_path_count_${tag}-${type}.txt
}
for t in ${types[@]}; do
  pp_acc ${t}
done

for f in */*/prop_path_count.tsv; do
  echo ${f}
done
