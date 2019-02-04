SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
send.sh() { scp -P 2222 $1 admin@zt1.ydns.eu:/d/download/; }
searchdir=/data/khan/arq/output/batch
list=($(find "${searchdir}" \
  -maxdepth 1 -mindepth 1 -type d -not -path '*/\.*' -printf '%f\n' | sort))
[ "$*" ] || last=${list[$((${#list[@]}-1))]}
[ "$*" ] || infile=/data/khan/arq/output/batch/${last}/*
[ "$*" ] && infile=$*
tag=$(date +%y%m%d)
outfile=${outfile:-./out/sa_${tag}.xlsx}

infuse_cats() {
  while read -r line; do
    if [[ ${line} =~ shapes$ ]]; then
      for p in {,nc_}{,re_}X; do
        for s in X{,_f,_fo,_fov,_fox}; do
          echo "$line" | sed -r 's/([^:]*): shapes/'${p/X/}'\1'${s/X/}': '${p/X/}'shapes'${s/X/}'/g'
        done
      done
    else
      echo "$line"
    fi
  done
}

REPLACEMENTS="'\1: shapes\2\nre_\1: re_shapes\2\nnc_\1: nc_shapes\2\nnc_re_\1: nc_re_shapes\2'"
VARIANT="sed -r 's/(.*): shapes(.*)/'$REPLACEMENTS'/g'"
${SCRIPT_DIR}/XLSX.groovy \
  ${SCRIPT_DIR}/dataset_order.yaml \
  <(eval $VARIANT ${SCRIPT_DIR}/prop2cat.yaml) \
  ${outfile} \
  ${infile}
echo ${outfile}
